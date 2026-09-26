package com.ainovel.app.workflow;

import com.ainovel.app.common.SafeLogThrowable;
import com.ainovel.app.ai.AiProgressContext;
import com.ainovel.app.integration.AiGatewayGrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import java.util.UUID;

@Service
public class GuidedCreationJobWorker {
    private static final Logger log = LoggerFactory.getLogger(GuidedCreationJobWorker.class);

    private final GuidedCreationJobService jobService;
    private final GuidedCreationGenerationService generationService;
    private final GuidedCreationWorkflowService workflowService;

    public GuidedCreationJobWorker(GuidedCreationJobService jobService,
                                   GuidedCreationGenerationService generationService,
                                   GuidedCreationWorkflowService workflowService) {
        this.jobService = jobService;
        this.generationService = generationService;
        this.workflowService = workflowService;
    }

    public void process(UUID jobId) {
        GuidedCreationJobService.JobClaim claim = jobService.claim(jobId);
        if (claim == null) {
            return;
        }
        boolean generationCompleted = false;
        try {
            GuidedCreationJobService.GenerationContext context = jobService.markCallingAi(jobId);
            GuidedCreationGenerationService.GenerationResult result = AiProgressContext.withListener(
                    progressListener(jobId, System::nanoTime),
                    () -> generationService.generate(context.run(), context.job(), context.payload()));
            GuidedCreationJobService.Completion completion = jobService.complete(jobId, result);
            generationCompleted = true;
            workflowService.advanceAutomatic(completion);
            log.info("Guided creation job completed jobId={} runId={} step={} chargedCredits={}",
                    jobId, claim.runId(), claim.step(), result.chargedCredits());
        } catch (RuntimeException ex) {
            if (generationCompleted) {
                jobService.failAutomaticAdvance(claim.runId(), ex);
                log.warn("Guided creation auto advance failed jobId={} runId={} step={} errorType={} grpcCode={}",
                        jobId, claim.runId(), claim.step(), ex.getClass().getSimpleName(),
                        ex instanceof io.grpc.StatusRuntimeException rpc ? rpc.getStatus().getCode().name() : "none",
                        SafeLogThrowable.stackOnly(ex));
            } else {
                jobService.fail(jobId, ex);
                log.warn("Guided creation job failed jobId={} runId={} step={} errorType={} grpcCode={}",
                        jobId, claim.runId(), claim.step(), ex.getClass().getSimpleName(),
                        ex instanceof io.grpc.StatusRuntimeException rpc ? rpc.getStatus().getCode().name() : "none",
                        SafeLogThrowable.stackOnly(ex));
            }
        }
    }

    AiGatewayGrpcClient.StreamProgressListener progressListener(UUID jobId, LongSupplier nanoTime) {
        AtomicLong completedTokens = new AtomicLong();
        return new AiGatewayGrpcClient.StreamProgressListener() {
            private boolean reported;
            private long lastReportedAt;

            @Override public void onDelta(long outputTokens, boolean estimated) {
                if (reported && nanoTime.getAsLong() - lastReportedAt < 1_000_000_000L) return;
                jobService.updateStreamProgress(jobId, completedTokens.get() + outputTokens, estimated);
                // Measure after the write so slow database commits cannot defeat throttling.
                lastReportedAt = nanoTime.getAsLong();
                reported = true;
            }

            @Override public void onCompleted(long completionTokens, long promptTokens, long cacheTokens) {
                jobService.completeStreamProgress(jobId, completedTokens.addAndGet(completionTokens));
            }
        };
    }
}
