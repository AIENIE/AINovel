package com.ainovel.app.aioperation;

import com.ainovel.app.common.ApiStatusException;
import com.ainovel.app.common.BusinessException;
import com.ainovel.app.user.User;
import com.ainovel.app.world.WorldGenerationCheckpoint;
import com.ainovel.app.world.WorldService;
import com.ainovel.app.world.dto.WorldDetailDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Supplier;

/** World-task recovery uses the existing operation payload, lease fence and model call identities. */
@Service
public class WorldTaskService {
    private final WorldService worlds;
    private final AiOperationRepository operations;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;

    public WorldTaskService(WorldService worlds, AiOperationRepository operations, ObjectMapper json,
                            PlatformTransactionManager transactionManager) {
        this.worlds = worlds;
        this.operations = operations;
        this.json = json;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public WorldTaskState freeze(UUID worldId, String moduleKey) {
        WorldDetailDto world = worlds.get(worldId);
        List<String> modules;
        if (moduleKey != null) {
            if (worlds.definitions().stream().noneMatch(def -> def.key().equals(moduleKey)))
                throw new BusinessException("世界模块不存在：" + moduleKey);
            modules = List.of(moduleKey);
        } else {
            // Match the existing publish rule: a module with any nonblank field is already complete.
            modules = worlds.definitions().stream().filter(def -> {
                Map<String, String> fields = world.modules().get(def.key());
                return fields == null || fields.values().stream().noneMatch(v -> v != null && !v.isBlank());
            }).map(def -> def.key()).toList();
        }
        return new WorldTaskState(1, fingerprint(world), modules, Map.of(), List.of());
    }

    public WorldDetailDto execute(AiOperationExecution execution, CoreAiOperationHandler.CorePayload payload) {
        Checkpoint checkpoint = new Checkpoint(execution.operationId(), payload, readState(payload));
        checkpoint.validate(worlds.get(payload.primaryId()));
        List<String> pending = checkpoint.state.modules().stream()
                .filter(module -> !checkpoint.state.appliedModules().contains(module)).toList();
        int total = Math.max(2, checkpoint.state.modules().size() + 2);
        execution.progress().step("检查世界任务输入", checkpoint.state.appliedModules().size(), total);
        WorldDetailDto result = null;
        if ("WORLD_PUBLISH".equals(payload.action())) {
            Supplier<WorldDetailDto> publish = () -> transactions.execute(status -> worlds.publish(payload.primaryId(), checkpoint));
            result = pending.isEmpty() ? AiOperationExecutionContext.complete(publish) : AiOperationExecutionContext.apply(publish);
        }
        for (int i = 0; i < pending.size(); i++) {
            String module = pending.get(i);
            execution.progress().step("生成世界模块：" + module, checkpoint.state.appliedModules().size() + 1, total);
            result = worlds.generateModule(payload.primaryId(), module, i == pending.size() - 1, checkpoint);
        }
        if (result == null) {
            result = AiOperationExecutionContext.complete(() -> {
                WorldDetailDto current = worlds.get(payload.primaryId());
                checkpoint.validate(current);
                return current;
            });
        }
        return result;
    }

    public GeneratedResults generatedResults(User user, UUID worldId, UUID operationId) {
        AiOperationRun operation = operations.findByIdAndUserId(operationId, user.getId())
                .orElseThrow(() -> new BusinessException("AI 操作不存在"));
        CoreAiOperationHandler.CorePayload payload = payload(operation);
        if (!CoreAiOperationHandler.TYPE.equals(operation.getOperationType())
                || !"WORLD".equals(operation.getScopeType()) || !worldId.equals(operation.getScopeId())
                || !("WORLD_PUBLISH".equals(payload.action()) || "WORLD_MODULE".equals(payload.action())))
            throw new BusinessException("世界生成任务不存在");
        WorldTaskState state = readState(payload);
        return new GeneratedResults(operationId, worldId, state.generatedFields(), state.appliedModules());
    }

    private WorldTaskState readState(CoreAiOperationHandler.CorePayload payload) {
        if (payload.request() == null || payload.request().isNull()) throw missingBaseline();
        try {
            WorldTaskState state = json.treeToValue(payload.request(), WorldTaskState.class);
            if (state.schemaVersion() != 1 || state.expectedContentHash() == null || state.expectedContentHash().isBlank()
                    || state.modules() == null || state.generatedFields() == null || state.appliedModules() == null)
                throw missingBaseline();
            return state;
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
            throw missingBaseline();
        }
    }

    private ApiStatusException missingBaseline() {
        return new ApiStatusException(HttpStatus.CONFLICT, "WORLD_TASK_BASELINE_REQUIRED");
    }

    private CoreAiOperationHandler.CorePayload payload(AiOperationRun operation) {
        try { return json.readValue(operation.getPayloadJson(), CoreAiOperationHandler.CorePayload.class); }
        catch (com.fasterxml.jackson.core.JsonProcessingException invalid) { throw missingBaseline(); }
    }

    private String fingerprint(WorldDetailDto world) {
        // Publishing and failure progress may change revisions without changing the business input.
        var content = new WorldContent(world.name(), world.tagline(), world.themes(), world.creativeIntent(), world.notes(), world.modules());
        try {
            byte[] bytes = json.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .writeValueAsString(content).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception invalid) { throw new IllegalStateException("世界任务输入摘要生成失败", invalid); }
    }

    private final class Checkpoint implements WorldGenerationCheckpoint {
        private final UUID operationId;
        private final CoreAiOperationHandler.CorePayload original;
        private WorldTaskState state;

        private Checkpoint(UUID operationId, CoreAiOperationHandler.CorePayload original, WorldTaskState state) {
            this.operationId = operationId;
            this.original = original;
            this.state = state;
        }

        @Override public void validate(WorldDetailDto current) {
            if (!state.expectedContentHash().equals(fingerprint(current)))
                throw new ApiStatusException(HttpStatus.CONFLICT, "WORLD_VERSION_CONFLICT");
        }

        @Override public String field(String moduleKey, String fieldKey, Supplier<String> generate) {
            validate(worlds.get(original.primaryId()));
            String saved = state.generatedFields().getOrDefault(moduleKey, Map.of()).get(fieldKey);
            if (saved != null && !saved.isBlank()) return saved;
            String output = generate.get();
            // Save received output even if the author edited during the call; it remains readable on conflict.
            Map<String, Map<String, String>> generated = new LinkedHashMap<>(state.generatedFields());
            Map<String, String> fields = new LinkedHashMap<>(generated.getOrDefault(moduleKey, Map.of()));
            fields.put(fieldKey, output);
            generated.put(moduleKey, fields);
            AiOperationExecutionContext.apply(() -> transactions.execute(status -> {
                persist(new WorldTaskState(1, state.expectedContentHash(), state.modules(), generated, state.appliedModules()));
                return null;
            }));
            validate(worlds.get(original.primaryId()));
            return output;
        }

        @Override public void applied(String moduleKey, WorldDetailDto updated) {
            List<String> applied = new ArrayList<>(state.appliedModules());
            applied.add(moduleKey);
            // The caller already holds the operation fence before locking/writing the world.
            persist(new WorldTaskState(1, fingerprint(updated), state.modules(), state.generatedFields(), applied));
        }

        private void persist(WorldTaskState next) {
            AiOperationRun operation = operations.findByIdForUpdate(operationId).orElseThrow();
            var updated = new CoreAiOperationHandler.CorePayload(original.action(), original.primaryId(), original.secondaryId(),
                    original.mode(), json.valueToTree(next), original.sceneGenerationVersion());
            try { operation.setPayloadJson(json.writeValueAsString(updated)); }
            catch (com.fasterxml.jackson.core.JsonProcessingException invalid) { throw new IllegalStateException(invalid); }
            operations.save(operation);
            state = next;
        }
    }

    private record WorldContent(String name, String tagline, List<String> themes, String creativeIntent, String notes,
                                Map<String, Map<String, String>> modules) { }

    public record WorldTaskState(int schemaVersion, String expectedContentHash, List<String> modules,
                                 Map<String, Map<String, String>> generatedFields, List<String> appliedModules) { }

    public record GeneratedResults(UUID operationId, UUID worldId, Map<String, Map<String, String>> modules,
                                    List<String> appliedModules) { }
}
