package com.ainovel.app.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Component
public class GuidedCreationRuntime implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(GuidedCreationRuntime.class);

    private final GuidedCreationJobService jobService;
    private final GuidedCreationWorkflowService workflowService;
    private final GuidedCreationJobWorker worker;
    private final Executor executor;

    public GuidedCreationRuntime(GuidedCreationJobService jobService,
                                 GuidedCreationWorkflowService workflowService,
                                 GuidedCreationJobWorker worker,
                                 @Qualifier("guidedCreationExecutor") Executor executor) {
        this.jobService = jobService;
        this.workflowService = workflowService;
        this.worker = worker;
        this.executor = executor;
    }

    @Override
    public void run(ApplicationArguments args) {
        jobService.recoverInterruptedJobs();
        resumeAutomaticRuns();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onQueued(GuidedCreationJobQueuedEvent event) {
        launch(event.jobId());
    }

    @Scheduled(fixedDelayString = "${app.guided-creation.dispatch-delay-ms:5000}")
    public void dispatchQueuedJobs() {
        jobService.queuedJobIds().forEach(this::launch);
    }

    @Scheduled(fixedDelayString = "${app.guided-creation.reconcile-delay-ms:10000}")
    public void resumeAutomaticRuns() {
        workflowService.activeAutomaticRunIds().forEach(runId -> {
            try {
                workflowService.resumeAutomatic(runId);
            } catch (RuntimeException ex) {
                jobService.failAutomaticAdvance(runId, ex);
                log.warn("Guided creation reconciliation failed runId={} reason={}", runId, ex.getMessage());
            }
        });
    }

    private void launch(UUID jobId) {
        try {
            executor.execute(() -> worker.process(jobId));
        } catch (RejectedExecutionException ex) {
            log.warn("Guided creation executor saturated; job remains queued jobId={}", jobId);
        }
    }
}
