package com.ainovel.app.aioperation;

import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.dto.AiRefineRequest;
import com.ainovel.app.ai.dto.AiRefineResponse;
import com.ainovel.app.ai.dto.AiUsageDto;
import com.ainovel.app.auth.SsoTokenExchangeService;
import com.ainovel.app.common.ApiStatusException;
import com.ainovel.app.modelconfig.AiSelectionContext;
import com.ainovel.app.user.User;
import com.ainovel.app.user.UserRepository;
import com.ainovel.app.world.WorldService;
import com.ainovel.app.world.dto.WorldCreateRequest;
import com.ainovel.app.world.dto.WorldDetailDto;
import com.ainovel.app.world.dto.WorldUpdateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class WorldTaskRecoveryTests {
    @MockBean private SsoTokenExchangeService ssoTokenExchangeService;
    @MockBean private AiService ai;
    @Autowired private WorldService worlds;
    @Autowired private WorldTaskService tasks;
    @Autowired private AiOperationRepository operations;
    @Autowired private UserRepository users;
    @Autowired private ObjectMapper json;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    @WithMockUser(username = "world_recover_fields")
    void retryKeepsSavedFieldsAndTheRemainingCallsKeepTheirOriginalIdentities() throws Exception {
        User user = user("world_recover_fields");
        WorldDetailDto world = world(user);
        AiOperationRun operation = operation(user, world, "WORLD_MODULE", "geography", true);
        var callKeys = new ArrayList<String>();
        AtomicInteger calls = new AtomicInteger();
        when(ai.refine(any(), any())).thenAnswer(invocation -> {
            AiRefineRequest request = invocation.getArgument(1);
            callKeys.add(AiSelectionContext.callKey(request.featureKey()));
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            if (calls.incrementAndGet() == 2) throw new IllegalStateException("temporary model failure");
            return output(request.featureKey());
        });

        assertThrows(RuntimeException.class, () -> execute(operation, user));
        assertFalse(worlds.get(world.id()).modules().containsKey("geography"));
        assertEquals(Map.of("terrain", "world.geography.terrain.generate"),
                tasks.generatedResults(user, world.id(), operation.getId()).modules().get("geography"));
        assertNull(operations.findById(operation.getId()).orElseThrow().getResultJson());

        WorldDetailDto recovered = execute(operation, user);
        assertEquals(4, calls.get());
        assertEquals(callKeys.get(1), callKeys.get(2));
        assertEquals(3, recovered.modules().get("geography").size());
        assertEquals(List.of("geography"), tasks.generatedResults(user, world.id(), operation.getId()).appliedModules());
        assertEquals(recovered.revision(), worlds.get(world.id()).revision());
    }

    @Test
    @WithMockUser(username = "world_recover_edited")
    void authorMetadataChangeBlocksRecoveryBeforeAnotherModelCall() throws Exception {
        User user = user("world_recover_edited");
        WorldDetailDto world = world(user);
        AiOperationRun operation = operation(user, world, "WORLD_MODULE", "geography", true);
        when(ai.refine(any(), any())).thenReturn(output("saved terrain"))
                .thenThrow(new IllegalStateException("temporary model failure"));
        assertThrows(RuntimeException.class, () -> execute(operation, user));
        worlds.update(world.id(), new WorldUpdateRequest("作者的新名称", null, null, null, null));
        clearInvocations(ai);

        ApiStatusException conflict = assertThrows(ApiStatusException.class, () -> execute(operation, user));
        assertEquals("WORLD_VERSION_CONFLICT", conflict.getMessage());
        verifyNoInteractions(ai);
        assertEquals("作者的新名称", worlds.get(world.id()).name());
        assertEquals("saved terrain", tasks.generatedResults(user, world.id(), operation.getId()).modules().get("geography").get("terrain"));
    }

    @Test
    @WithMockUser(username = "world_edit_during_call")
    void resultArrivingAfterAuthorEditIsReadableButNeverApplied() throws Exception {
        User user = user("world_edit_during_call");
        WorldDetailDto world = world(user);
        AiOperationRun operation = operation(user, world, "WORLD_MODULE", "geography", true);
        when(ai.refine(any(), any())).thenAnswer(invocation -> {
            worlds.update(world.id(), new WorldUpdateRequest(null, null, null, null, "作者补充了限制"));
            return output("old-input terrain");
        });
        RuntimeException failure = assertThrows(RuntimeException.class, () -> execute(operation, user));
        assertEquals("WORLD_VERSION_CONFLICT", failure.getCause().getMessage());
        verify(ai, times(1)).refine(any(), any());
        assertTrue(worlds.get(world.id()).modules().isEmpty());
        assertEquals("作者补充了限制", worlds.get(world.id()).notes());
        assertEquals("old-input terrain", tasks.generatedResults(user, world.id(), operation.getId()).modules().get("geography").get("terrain"));
        assertThrows(ApiStatusException.class, () -> execute(operation, user));
        verify(ai, times(1)).refine(any(), any());
    }

    @Test
    @WithMockUser(username = "world_publish_recovery")
    void publishingResumesAfterItsOwnAppliedModuleWithoutRegeneratingIt() throws Exception {
        User user = user("world_publish_recovery");
        WorldDetailDto world = world(user);
        AiOperationRun operation = operation(user, world, "WORLD_PUBLISH", null, true);
        AtomicInteger calls = new AtomicInteger();
        var features = new ArrayList<String>();
        when(ai.refine(any(), any())).thenAnswer(invocation -> {
            AiRefineRequest request = invocation.getArgument(1);
            features.add(request.featureKey());
            if (calls.incrementAndGet() == 4) throw new IllegalStateException("second module failure");
            return output(request.featureKey());
        });
        assertThrows(RuntimeException.class, () -> execute(operation, user));
        assertEquals(List.of("geography"), tasks.generatedResults(user, world.id(), operation.getId()).appliedModules());
        assertEquals(1, worlds.get(world.id()).modules().size());

        WorldDetailDto result = execute(operation, user);
        assertEquals("draft", result.status());
        assertEquals("0.1.0", result.version());
        assertEquals(3, result.modules().size());
        assertEquals(10, calls.get());
        assertEquals(3, features.stream().filter(feature -> feature.startsWith("world.geography.")).count());
        assertEquals(List.of("geography", "society", "magic_tech"), tasks.generatedResults(user, world.id(), operation.getId()).appliedModules());
        assertEquals(AiOperationStatus.SUCCEEDED, operations.findById(operation.getId()).orElseThrow().getStatus());
    }

    @Test
    @WithMockUser(username = "world_legacy_task")
    void historicalTaskWithoutBaselineIsNotReconstructedOrRetried() throws Exception {
        User user = user("world_legacy_task");
        WorldDetailDto world = world(user);
        AiOperationRun operation = operation(user, world, "WORLD_MODULE", "geography", false);
        String original = operation.getPayloadJson();

        ApiStatusException conflict = assertThrows(ApiStatusException.class, () -> execute(operation, user));
        assertEquals("WORLD_TASK_BASELINE_REQUIRED", conflict.getMessage());
        verifyNoInteractions(ai);
        assertEquals(original, operations.findById(operation.getId()).orElseThrow().getPayloadJson());
        assertEquals(world, worlds.get(world.id()));
    }

    private User user(String username) {
        User user = new User();
        user.setUsername(username); user.setEmail(username + "@example.invalid"); user.setPasswordHash("x");
        return users.save(user);
    }

    private WorldDetailDto world(User user) {
        return worlds.create(user, new WorldCreateRequest("潮汐城", "随潮水迁徙的城市", List.of("代价"), "寻找定居的可能", null));
    }

    private AiOperationRun operation(User user, WorldDetailDto world, String action, String module, boolean baseline) throws Exception {
        AiOperationRun operation = new AiOperationRun();
        operation.setUser(user); operation.setOperationType(CoreAiOperationHandler.TYPE);
        operation.setScopeType("WORLD"); operation.setScopeId(world.id()); operation.setStatus(AiOperationStatus.RUNNING);
        operation.setLeaseOwner("world-recovery-test"); operation.setLeaseExpiresAt(Instant.now().plusSeconds(600));
        operation.setPayloadJson(json.writeValueAsString(new CoreAiOperationHandler.CorePayload(action, world.id(), null, module,
                baseline ? json.valueToTree(tasks.freeze(world.id(), module)) : null)));
        return operations.saveAndFlush(operation);
    }

    private WorldDetailDto execute(AiOperationRun operation, User user) throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        // Exercise the production lock order and atomic checkpoint/final-result transaction boundary.
        try (var fence = AiOperationExecutionContext.install(operation.getId(), new AiOperationExecutionContext.Fence() {
            @Override public <T> T apply(Supplier<T> mutation, Function<T, Object> resultView) {
                return tx.execute(status -> {
                    AiOperationRun current = operations.findByIdForUpdate(operation.getId()).orElseThrow();
                    T result = mutation.get();
                    if (resultView != null) {
                        current.setStatus(AiOperationStatus.SUCCEEDED);
                        try { current.setResultJson(json.writeValueAsString(resultView.apply(result))); }
                        catch (Exception invalid) { throw new IllegalStateException(invalid); }
                        operations.save(current);
                    }
                    return result;
                });
            }
        }); var selection = AiSelectionContext.install(new AiSelectionContext.Snapshot("frozen-quote", Map.of()))) {
            AiOperationRun current = operations.findById(operation.getId()).orElseThrow();
            var payload = json.readValue(current.getPayloadJson(), CoreAiOperationHandler.CorePayload.class);
            return tasks.execute(new AiOperationExecution(operation.getId(), user, current.getPayloadJson(), json, (a, b, c) -> { }), payload);
        }
    }

    private AiRefineResponse output(String text) {
        return new AiRefineResponse(text, new AiUsageDto(1, 1, 0, 0, 0), 100);
    }
}
