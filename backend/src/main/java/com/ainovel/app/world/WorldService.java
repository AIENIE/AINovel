package com.ainovel.app.world;

import com.ainovel.app.common.BusinessException;
import com.ainovel.app.common.ApiStatusException;
import com.ainovel.app.common.JsonColumnCodec;
import com.ainovel.app.world.dto.*;
import com.ainovel.app.world.model.World;
import com.ainovel.app.world.repo.WorldRepository;
import com.ainovel.app.user.User;
import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.dto.AiRefineRequest;
import com.ainovel.app.ai.dto.AiRefineResponse;
import com.ainovel.app.security.ResourceAccessGuard;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

@Service
public class WorldService {
    @Autowired
    private WorldRepository worldRepository;
    @Autowired private WorldEntryStore entryStore;
    @Autowired private com.ainovel.app.world.repo.WorldVersionRepository versions;
    @Autowired
    private AiService aiService;
    @Autowired
    private ResourceAccessGuard accessGuard;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JsonColumnCodec jsonColumnCodec;

    public List<WorldDefinitionDto> definitions() {
        return List.of(
                new WorldDefinitionDto("geography", "地理环境", "定义世界的地形、气候、重要地点", List.of(
                        new WorldDefinitionDto.Field("terrain", "地形地貌", "textarea", "例如：群山、海岛"),
                        new WorldDefinitionDto.Field("climate", "气候特征", "textarea", "例如：季风、极寒"),
                        new WorldDefinitionDto.Field("locations", "重要地点", "textarea", "列出关键城市")
                )),
                new WorldDefinitionDto("society", "社会体系", "政治、经济、文化", List.of(
                        new WorldDefinitionDto.Field("politics", "政治体制", "textarea", "君主制/联邦"),
                        new WorldDefinitionDto.Field("economy", "经济结构", "textarea", "主要产业"),
                        new WorldDefinitionDto.Field("culture", "文化", "textarea", "风俗")
                )),
                new WorldDefinitionDto("magic_tech", "魔法/科技", "魔法或科技体系", List.of(
                        new WorldDefinitionDto.Field("system_name", "体系名称", "text", ""),
                        new WorldDefinitionDto.Field("rules", "规则", "textarea", "力量来源"),
                        new WorldDefinitionDto.Field("limitations", "限制", "textarea", "代价")
                ))
        );
    }

    public List<WorldDto> list(User user) {
        accessGuard.assertCurrentUserEquals(user.getUsername());
        return worldRepository.findByUser(user).stream()
                .map(w -> new WorldDto(w.getId(), w.getName(), w.getTagline(), w.getStatus(), w.getVersion(), w.getUpdatedAt()))
                .toList();
    }

    @Transactional
    public WorldDetailDto create(User user, WorldCreateRequest request) {
        World world = new World();
        world.setUser(user);
        world.setName(request.name());
        world.setTagline(request.tagline());
        world.setThemesJson(writeJson(request.themes() == null ? List.of() : request.themes()));
        world.setCreativeIntent(request.creativeIntent());
        world.setNotes(request.notes());
        world.setStatus("draft");
        world.setVersion("0.1.0");
        world.setModulesJson(writeJson(new HashMap<String, Map<String, String>>()));
        world.setModuleProgressJson(writeJson(new HashMap<String, String>()));
        worldRepository.saveAndFlush(world);
        return toDetail(world);
    }

    @Transactional(readOnly = true)
    public WorldDetailDto get(UUID id) {
        World world = worldRepository.findById(id).orElseThrow(() -> new BusinessException("世界不存在"));
        accessGuard.assertOwner(world.getUser());
        return toDetail(world);
    }

    @Transactional(readOnly = true)
    public String contentHash(UUID id) {
        var world=worldRepository.findById(id).orElseThrow(()->new BusinessException("世界不存在"));
        accessGuard.assertOwner(world.getUser());return entryStore.hash(entryStore.content(world));
    }

    @Transactional
    public WorldDetailDto update(UUID id, WorldUpdateRequest request) {
        World world = requireEditableWorld(id, request.expectedRevision());
        Map<String, Map<String, String>> modules = entryStore.projection(entryStore.entries(world));
        if (request.modules() != null) modules.putAll(request.modules());
        entryStore.replaceLegacy(world, modules, current -> {
            if (request.name() != null) current.setName(request.name());
            if (request.tagline() != null) current.setTagline(request.tagline());
            if (request.themes() != null) current.setThemesJson(writeJson(request.themes()));
            if (request.creativeIntent() != null) current.setCreativeIntent(request.creativeIntent());
            if (request.notes() != null) current.setNotes(request.notes());
        });
        worldRepository.saveAndFlush(world);
        return toDetail(world);
    }

    @Transactional
    public void delete(UUID id) {
        World world = worldRepository.findById(id).orElseThrow();
        accessGuard.assertOwner(world.getUser());
        if (!"draft".equalsIgnoreCase(world.getStatus())) {
            throw new BusinessException("仅草稿世界可删除");
        }
        if (versions.countByWorldId(id) > 0) throw new BusinessException("已有版本的世界不可删除");
        entryStore.save(world, List.of(), List.of());
        worldRepository.delete(world);
    }

    @Transactional
    public WorldDetailDto updateModules(UUID id, WorldModulesUpdateRequest request) {
        World world = requireEditableWorld(id, request.expectedRevision());
        Map<String, Map<String, String>> modules = entryStore.projection(entryStore.entries(world));
        if (request.modules() != null) modules.putAll(request.modules());
        entryStore.replaceLegacy(world, modules);
        worldRepository.saveAndFlush(world);
        return toDetail(world);
    }

    @Transactional
    public WorldDetailDto updateModule(UUID id, String moduleKey, WorldModuleUpdateRequest request) {
        World world = requireEditableWorld(id, request.expectedRevision());
        Map<String, Map<String, String>> modules = entryStore.projection(entryStore.entries(world));
        modules.put(moduleKey, request.fields());
        entryStore.replaceLegacy(world, modules);
        worldRepository.saveAndFlush(world);
        return toDetail(world);
    }

    private World requireEditableWorld(UUID id, Long expectedRevision) {
        World world = worldRepository.findByIdForUpdate(id).orElseThrow(() -> new BusinessException("世界不存在"));
        accessGuard.assertOwner(world.getUser());
        if (expectedRevision != null && world.getRevision() != expectedRevision) {
            throw new ApiStatusException(HttpStatus.CONFLICT, "WORLD_VERSION_CONFLICT");
        }
        return world;
    }

    @Transactional
    public AiRefineResponse refineField(User user, UUID id, String moduleKey, String fieldKey, String text, String instruction) {
        World world = worldRepository.findById(id).orElseThrow(() -> new BusinessException("世界不存在"));
        accessGuard.assertOwner(world.getUser());
        String prompt = (instruction == null ? "" : instruction).trim();
        if (prompt.isBlank()) {
            prompt = "请优化以下世界观字段表述，使其更清晰、更具细节且保持一致性。";
        }
        return aiService.refine(user, new AiRefineRequest(text, prompt, null).forFeature("world."+moduleKey+"."+fieldKey+".refine"));
    }

    @Transactional(readOnly = true)
    public WorldPublishPreviewResponse preview(UUID id) {
        World world = worldRepository.findById(id).orElseThrow();
        accessGuard.assertOwner(world.getUser());
        Map<String, Map<String, String>> modules = entryStore.projection(entryStore.entries(world));
        List<String> missing = new ArrayList<>();
        List<String> toGenerate = new ArrayList<>();
        for (WorldDefinitionDto def : definitions()) {
            Map<String, String> fields = modules.get(def.key());
            boolean hasAny = false;
            if (fields != null) {
                for (String v : fields.values()) {
                    if (v != null && !v.isBlank()) {
                        hasAny = true;
                        break;
                    }
                }
            }
            if (!hasAny) {
                missing.add(def.key());
                toGenerate.add(def.key());
            }
        }
        return new WorldPublishPreviewResponse(missing, toGenerate);
    }

    @Transactional
    public WorldDetailDto publish(UUID id) {
        return publish(id, null);
    }

    @Transactional
    public WorldDetailDto publish(UUID id, WorldGenerationCheckpoint checkpoint) {
        World world = requireEditableWorld(id, null);
        if (checkpoint != null) checkpoint.validate(toDetail(world));
        Map<String, Map<String, String>> modules = entryStore.projection(entryStore.entries(world));

        Map<String, String> progress = new HashMap<>();
        for (WorldDefinitionDto def : definitions()) {
            Map<String, String> fields = modules.get(def.key());
            boolean hasAny = false;
            if (fields != null) {
                for (String v : fields.values()) {
                    if (v != null && !v.isBlank()) {
                        hasAny = true;
                        break;
                    }
                }
            }
            if (hasAny) {
                progress.put(def.key(), "COMPLETED");
            } else {
                progress.put(def.key(), "AWAITING_GENERATION");
            }
        }

        // Legacy publish operations now only prepare a draft generation queue.
        if (!"active".equalsIgnoreCase(world.getStatus())) world.setStatus("draft");
        world.setModuleProgressJson(writeJson(progress));
        worldRepository.saveAndFlush(world);
        return toDetail(world);
    }

    @Transactional(readOnly = true)
    public WorldGenerationStatus generationStatus(UUID id) {
        World world = worldRepository.findById(id).orElseThrow();
        accessGuard.assertOwner(world.getUser());
        Map<String, String> progress = readProgress(world.getModuleProgressJson());
        List<WorldGenerationStatus.ModuleStatus> modules = progress.entrySet().stream()
                .map(e -> new WorldGenerationStatus.ModuleStatus(e.getKey(), statusOnly(e.getValue()), 1, statusError(e.getValue())))
                .toList();
        return new WorldGenerationStatus(modules);
    }

    public WorldDetailDto generateModule(UUID id, String moduleKey) {
        return generateModule(id, moduleKey, true);
    }

    public WorldDetailDto generateModule(UUID id, String moduleKey, boolean completeOperation) {
        return generateModule(id, moduleKey, completeOperation, null);
    }

    public WorldDetailDto generateModule(UUID id, String moduleKey, boolean completeOperation,
                                         WorldGenerationCheckpoint checkpoint) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        World snapshot = Objects.requireNonNull(tx.execute(status -> {
            World current = worldRepository.findById(id).orElseThrow();
            accessGuard.assertOwner(current.getUser());
            current.getUser().getUsername();
            return current;
        }));
        World world = snapshot;
        if (checkpoint != null) checkpoint.validate(toDetail(snapshot));
        Map<String, Map<String, String>> modules = entryStore.projection(entryStore.entries(world));
        Optional<WorldDefinitionDto> moduleDef = definitions().stream().filter(d -> d.key().equals(moduleKey)).findFirst();
        if (moduleDef.isEmpty()) {
            throw new BusinessException("世界模块不存在：" + moduleKey);
        }
        try {
            Map<String, String> fields = new HashMap<>(modules.getOrDefault(moduleKey, new HashMap<>()));
            for (WorldDefinitionDto.Field field : moduleDef.get().fields()) {
                String existing = fields.get(field.key());
                if (existing == null || existing.isBlank()) {
                    String generated = checkpoint == null ? generateField(world, moduleDef.get(), field)
                            : checkpoint.field(moduleKey, field.key(), () -> generateField(world, moduleDef.get(), field));
                    fields.put(field.key(), generated);
                }
            }
            modules.put(moduleKey, fields);
        } catch (RuntimeException ex) {
            String error = cleanError(ex.getMessage());
            com.ainovel.app.aioperation.AiOperationExecutionContext.apply(() -> {
                recordModuleFailure(id, snapshot.getRevision(), moduleKey, error); return null;
            });
            throw new RuntimeException("生成世界模块失败：" + error, ex);
        }
        java.util.function.Supplier<WorldDetailDto> apply = () -> tx.execute(status -> {
            World current = worldRepository.findByIdForUpdate(id).orElseThrow();
            accessGuard.assertOwner(current.getUser());
            if (checkpoint != null) checkpoint.validate(toDetail(current));
            else if (current.getRevision() != snapshot.getRevision())
                throw new com.ainovel.app.common.ApiStatusException(org.springframework.http.HttpStatus.CONFLICT, "WORLD_VERSION_CONFLICT");
            entryStore.replaceLegacy(current, modules);
            Map<String, String> currentProgress = readProgress(current.getModuleProgressJson());
            currentProgress.put(moduleKey, "COMPLETED");
            current.setModuleProgressJson(writeJson(currentProgress));
            worldRepository.saveAndFlush(current);
            WorldDetailDto updated = toDetail(current);
            if (checkpoint != null) checkpoint.applied(moduleKey, updated);
            return updated;
        });
        return completeOperation ? com.ainovel.app.aioperation.AiOperationExecutionContext.complete(apply)
                : com.ainovel.app.aioperation.AiOperationExecutionContext.apply(apply);
    }

    public WorldDetailDto retryModule(UUID id, String moduleKey) {
        return generateModule(id, moduleKey);
    }

    private String generateField(World world, WorldDefinitionDto module, WorldDefinitionDto.Field field) {
        String text = "世界名称：" + safe(world.getName()) + "\n"
                + "世界简介：" + safe(world.getTagline()) + "\n"
                + "主题：" + String.join("、", readThemes(world.getThemesJson())) + "\n"
                + "创作意图：" + safe(world.getCreativeIntent()) + "\n"
                + "模块：" + module.label() + "\n"
                + "字段：" + field.label();
        String instruction = "请为该世界观字段生成可直接保存的中文设定内容，避免占位说明，保持具体、可用、与已有世界设定一致。";
        AiRefineResponse response = aiService.refine(world.getUser(), new AiRefineRequest(text, instruction, null).forFeature("world."+module.key()+"."+field.key()+".generate"));
        String result = response == null ? "" : safe(response.result()).trim();
        if (result.isBlank()) {
            throw new RuntimeException("AI 返回空内容");
        }
        return result;
    }

    private void recordModuleFailure(UUID id, long revision, String moduleKey, String error) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> {
            World failedWorld = worldRepository.findByIdForUpdate(id).orElseThrow();
            if (failedWorld.getRevision() != revision) return;
            Map<String, String> failedProgress = readProgress(failedWorld.getModuleProgressJson());
            failedProgress.put(moduleKey, "FAILED|" + error);
            failedWorld.setModuleProgressJson(writeJson(failedProgress));
            worldRepository.save(failedWorld);
        });
    }

    private WorldDetailDto toDetail(World world) {
        return new WorldDetailDto(
                world.getId(),
                world.getName(),
                world.getTagline(),
                world.getStatus(),
                world.getVersion(),
                readThemes(world.getThemesJson()),
                world.getCreativeIntent(),
                world.getNotes(),
                entryStore.projection(entryStore.entries(world)),
                readProgress(world.getModuleProgressJson()),
                world.getUpdatedAt(),
                world.getRevision()
        );
    }

    private String statusOnly(String raw) {
        if (raw == null) {
            return null;
        }
        int sep = raw.indexOf('|');
        return sep < 0 ? raw : raw.substring(0, sep);
    }

    private String statusError(String raw) {
        if (raw == null) {
            return null;
        }
        int sep = raw.indexOf('|');
        return sep < 0 ? null : raw.substring(sep + 1);
    }

    private String cleanError(String message) {
        if (message == null || message.isBlank()) {
            return "未知错误";
        }
        return message.length() > 240 ? message.substring(0, 240) : message;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private List<String> readThemes(String json) {
        List<String> themes = jsonColumnCodec.read(json, new TypeReference<>() {}, List.of());
        return themes == null ? List.of() : themes.stream().filter(Objects::nonNull).toList();
    }

    private Map<String, String> readProgress(String json) {
        return jsonColumnCodec.read(json, new TypeReference<>() {}, new HashMap<>());
    }

    private String writeJson(Object obj) {
        return jsonColumnCodec.write(obj, "{}");
    }
}
