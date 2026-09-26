package com.ainovel.app.workflow;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class GuidedCreationJobWorkerTest {
    @Test void coalescesBurstProgressEvenWithSlowWritesAndKeepsExactMultiCallTotals() {
        var service = mock(GuidedCreationJobService.class);
        var worker = new GuidedCreationJobWorker(service, null, null);
        var clock = new AtomicLong();
        var jobId = UUID.randomUUID();
        doAnswer(invocation -> { clock.addAndGet(2_000_000_000L); return null; })
                .when(service).updateStreamProgress(eq(jobId), anyLong(), anyBoolean());
        var listener = worker.progressListener(jobId, clock::get);
        listener.onDelta(1, true);
        for (int token = 2; token <= 1000; token++) listener.onDelta(token, true);
        verify(service, times(1)).updateStreamProgress(eq(jobId), anyLong(), anyBoolean());
        clock.addAndGet(1_000_000_000L);
        listener.onDelta(1001, true);
        listener.onCompleted(1200, 20, 0);
        clock.addAndGet(1_000_000_000L);
        listener.onDelta(10, true);
        listener.onCompleted(30, 5, 0);
        verify(service).updateStreamProgress(jobId, 1001, true);
        verify(service).updateStreamProgress(jobId, 1210, true);
        verify(service).completeStreamProgress(jobId, 1200);
        verify(service).completeStreamProgress(jobId, 1230);
    }
}
