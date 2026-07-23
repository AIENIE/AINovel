package com.ainovel.app.workflow;

import com.ainovel.app.ai.AiProgressContext;
import com.ainovel.app.integration.AiGatewayGrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

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
            AtomicLong completedTokens = new AtomicLong();
            GuidedCreationGenerationService.GenerationResult result = AiProgressContext.withListener(
                    new AiGatewayGrpcClient.StreamProgressListener() {
                        @Override public void onDelta(long outputTokens, boolean estimated) {
                            jobService.updateStreamProgress(jobId, completedTokens.get() + outputTokens, estimated);
                        }
                        @Override public void onCompleted(long completionTokens, long promptTokens, long cacheTokens) {
                            long total = completedTokens.addAndGet(completionTokens);
                            jobService.completeStreamProgress(jobId, total);
                        }
                    },
                    () -> generationService.generate(context.run(), context.job(), context.payload()));
            GuidedCreationJobService.Completion completion = jobService.complete(jobId, result);
            generationCompleted = true;
            workflowService.advanceAutomatic(completion);
            log.info("Guided creation job completed jobId={} runId={} step={} chargedCredits={}",
                    jobId, claim.runId(), claim.step(), result.chargedCredits());
        } catch (RuntimeException ex) {
            if (generationCompleted) {
                jobService.failAutomaticAdvance(claim.runId(), ex);
                log.warn("Guided creation auto advance failed jobId={} runId={} step={} reason={}",
                        jobId, claim.runId(), claim.step(), ex.getMessage());
            } else {
                jobService.fail(jobId, ex);
                log.warn("Guided creation job failed jobId={} runId={} step={} reason={}",
                        jobId, claim.runId(), claim.step(), ex.getMessage());
            }
        }
    }
}
