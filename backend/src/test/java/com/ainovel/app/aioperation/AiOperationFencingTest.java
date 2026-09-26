package com.ainovel.app.aioperation;

import com.ainovel.app.ai.AiProgressContext;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiOperationFencingTest {
    final AiOperationRepository repository = mock(AiOperationRepository.class);
    final TransactionTemplate tx = mock(TransactionTemplate.class);
    final AiOperationHandler handler = mock(AiOperationHandler.class);
    final User user = new User();
    final AiOperationRun run = new AiOperationRun();
    final AiOperationService service;
    AiOperationFencingTest() {
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID()); user.setUsername("author");
        ReflectionTestUtils.setField(run, "id", UUID.randomUUID());
        run.setUser(user); run.setStatus(AiOperationStatus.QUEUED); run.setOperationType("test"); run.setPayloadJson("{}");
        when(handler.type()).thenReturn("test");
        when(repository.findByIdForUpdate(run.getId())).thenReturn(Optional.of(run));
        when(repository.findById(run.getId())).thenReturn(Optional.of(run));
        when(tx.execute(any())).thenAnswer(i -> ((TransactionCallback<?>) i.getArgument(0)).doInTransaction(mock(TransactionStatus.class)));
        doAnswer(i -> { ((Consumer<TransactionStatus>) i.getArgument(0)).accept(mock(TransactionStatus.class)); return null; }).when(tx).executeWithoutResult(any());
        service = new AiOperationService(repository, new ObjectMapper(), tx, Runnable::run, List.of(handler));
    }
    @Test void cancellationWinsAgainstLateStartedAndCompletedAndBusinessWrite() throws Exception {
        AtomicInteger writes = new AtomicInteger();
        when(handler.execute(any())).thenAnswer(i -> {
            var listener = AiProgressContext.current();
            assertEquals(AiOperationStatus.CANCELLED, service.cancel(user, run.getId()).status());
            listener.onStarted("late", "model"); listener.onCompleted(3, 2, 0);
            assertThrows(RuntimeException.class, () -> AiOperationExecutionContext.complete(writes::incrementAndGet));
            return Map.of("ignored", true);
        });
        ReflectionTestUtils.invokeMethod(service, "execute", run.getId());
        assertEquals(AiOperationStatus.CANCELLED, run.getStatus()); assertEquals(0, writes.get());
        assertNull(run.getResultJson()); assertNull(run.getLeaseOwner());
    }
    @Test void businessCompletionWinsAgainstLaterCancellationAndProgress() throws Exception {
        when(handler.execute(any())).thenAnswer(i -> {
            var result = AiOperationExecutionContext.complete(() -> Map.of("saved", true));
            assertEquals(AiOperationStatus.SUCCEEDED, service.cancel(user, run.getId()).status());
            AiProgressContext.current().onCompleted(100, 10, 0);
            return result;
        });
        ReflectionTestUtils.invokeMethod(service, "execute", run.getId());
        assertEquals(AiOperationStatus.SUCCEEDED, run.getStatus()); assertEquals("{\"saved\":true}", run.getResultJson());
    }
    @Test void cancelledNarrativeInferenceCannotStoreItsLateResult() throws Exception {
        var narrative = mock(com.ainovel.app.narrative.NarrativeService.class);
        var ai = mock(com.ainovel.app.ai.AiService.class);
        UUID extractionId = UUID.randomUUID();
        run.setPayloadJson("{\"extractionId\":\"" + extractionId + "\"}");
        when(narrative.input(user, extractionId, run.getId())).thenReturn(
                new com.ainovel.app.narrative.NarrativeDtos.ExtractionInput(extractionId, UUID.randomUUID(),
                        UUID.randomUUID(), List.of(), List.of(), List.of()));
        when(ai.chat(eq(user), any(), any())).thenAnswer(i -> {
            service.cancel(user, run.getId());
            return new com.ainovel.app.ai.dto.AiChatResponse("assistant", "{\"candidates\":[]}",
                    new com.ainovel.app.ai.dto.AiUsageDto(1, 1, 0, 0, 1), 5);
        });
        var realHandler = new com.ainovel.app.narrative.NarrativeExtractionHandler(narrative, ai,
                mock(com.ainovel.app.economy.EconomyService.class));
        when(handler.execute(any())).thenAnswer(i -> realHandler.execute(i.getArgument(0)));
        ReflectionTestUtils.invokeMethod(service, "execute", run.getId());
        assertEquals(AiOperationStatus.CANCELLED, run.getStatus());
        verify(narrative, never()).storeResult(any(), any(), any(), any(), any(), any());
    }
    @Test void replacedOrExpiredExecutionCannotApplyResult() throws Exception {
        when(handler.execute(any())).thenAnswer(i -> {
            run.setLeaseOwner("replacement"); run.setLeaseExpiresAt(Instant.now().plusSeconds(900));
            assertThrows(RuntimeException.class, () -> AiOperationExecutionContext.complete(() -> "stale"));
            return "stale";
        });
        ReflectionTestUtils.invokeMethod(service, "execute", run.getId());
        assertEquals("replacement", run.getLeaseOwner()); assertNull(run.getResultJson());
    }
    @Test void secondClaimDoesNotExecuteAnAlreadyRunningRow() throws Exception {
        run.setStatus(AiOperationStatus.RUNNING);
        ReflectionTestUtils.invokeMethod(service, "execute", run.getId());
        verify(handler, never()).execute(any()); verify(repository).findByIdForUpdate(run.getId());
    }
    @Test void retryReusesCallKeysButSeparatesCalls() throws Exception {
        List<String> keys = new ArrayList<>();
        when(handler.execute(any())).thenAnswer(i -> {
            keys.add(AiOperationExecutionContext.nextCallKey()); keys.add(AiOperationExecutionContext.nextCallKey());
            return Map.of("ok", true);
        });
        ReflectionTestUtils.invokeMethod(service, "execute", run.getId());
        run.setStatus(AiOperationStatus.QUEUED);
        ReflectionTestUtils.invokeMethod(service, "execute", run.getId());
        assertEquals(keys.get(0), keys.get(2)); assertEquals(keys.get(1), keys.get(3)); assertNotEquals(keys.get(0), keys.get(1));
    }
}
