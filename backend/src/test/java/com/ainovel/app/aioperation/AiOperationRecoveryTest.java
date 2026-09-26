package com.ainovel.app.aioperation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiOperationRecoveryTest {
    final AiOperationRepository repository = mock(AiOperationRepository.class);
    final TransactionTemplate transactions = mock(TransactionTemplate.class);
    final AiOperationHandler handler = mock(AiOperationHandler.class);
    final AiOperationService service;

    AiOperationRecoveryTest() {
        when(handler.type()).thenReturn("NARRATIVE_STATE_EXTRACT");
        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(mock(TransactionStatus.class)); return null;
        }).when(transactions).executeWithoutResult(any());
        service = new AiOperationService(repository, new ObjectMapper(), transactions, command -> {}, List.of(handler));
    }
    AiOperationRun run(boolean expired) {
        var run = new AiOperationRun();
        ReflectionTestUtils.setField(run, "id", UUID.randomUUID());
        run.setOperationType("NARRATIVE_STATE_EXTRACT");
        run.setPayloadJson("{}"); run.setStatus(AiOperationStatus.STREAMING); run.setStreamStarted(true);
        run.setLeaseOwner("previous-process"); run.setActiveScopeKey("active");
        run.setLeaseExpiresAt(Instant.now().plusSeconds(expired ? -1 : 900));
        when(repository.findByStatusIn(any())).thenReturn(List.of(run));
        when(repository.findByIdForUpdate(run.getId())).thenReturn(Optional.of(run));
        return run;
    }
    @Test void expiresReservationAndTaskTogetherThenDoesNotRepeatRecovery() throws Exception {
        var run = run(true);
        service.recover();
        assertEquals(AiOperationStatus.RECOVERY_REQUIRED, run.getStatus());
        assertNull(run.getLeaseOwner()); assertNull(run.getLeaseExpiresAt()); assertEquals("active", run.getActiveScopeKey());
        service.recover();
        verify(handler, times(1)).recoverExpiredLease(argThat(e -> e.operationId().equals(run.getId())));
        verify(handler, never()).execute(any());
    }
    @Test void doesNotReleaseAnUnexpiredExecution() throws Exception {
        var run = run(false); service.recover();
        assertEquals(AiOperationStatus.STREAMING, run.getStatus());
        verify(handler, never()).recoverExpiredLease(any());
    }
    @Test void rechecksTerminalStatusUnderLockBeforeReleasing() throws Exception {
        var run = run(true);
        when(repository.findByIdForUpdate(run.getId())).thenAnswer(i -> {
            run.setStatus(AiOperationStatus.CANCELLED); return Optional.of(run);
        });
        service.recover();
        assertEquals(AiOperationStatus.CANCELLED, run.getStatus());
        verify(handler, never()).recoverExpiredLease(any());
    }
    @Test void failedRecoveryDoesNotMakeTaskRetryable() throws Exception {
        var run = run(true);
        doThrow(new IllegalStateException("temporary accounting failure")).when(handler).recoverExpiredLease(any());
        assertThrows(IllegalStateException.class, service::recover);
        assertEquals(AiOperationStatus.STREAMING, run.getStatus());
        assertNotNull(run.getLeaseExpiresAt());
    }
}
