package com.ainovel.app.workflow;

import com.ainovel.app.common.BusinessException;
import com.ainovel.app.common.JsonColumnCodec;
import com.ainovel.app.workflow.model.AsyncJob;
import com.ainovel.app.workflow.model.AsyncJobStatus;
import com.ainovel.app.workflow.model.CreationWorkflowRun;
import com.ainovel.app.workflow.model.CreationWorkflowStatus;
import com.ainovel.app.workflow.model.GuidedCreationOperation;
import com.ainovel.app.workflow.model.GuidedCreationStep;
import com.ainovel.app.workflow.repo.AsyncJobRepository;
import com.ainovel.app.workflow.repo.CreationWorkflowRunRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class GuidedCreationJobService {
    private static final String JOB_TYPE = "G1_GUIDED_CREATION_STEP";
    private static final int ERROR_LIMIT = 500;

    private final AsyncJobRepository jobRepository;
    private final CreationWorkflowRunRepository runRepository;
    private final GuidedCreationJsonSupport jsonSupport;
    private final GuidedCreationOutlineJobSupport outlineSupport;
    private final JsonColumnCodec codec;
    private final ApplicationEventPublisher eventPublisher;
    private final String leaseOwner;

    public GuidedCreationJobService(AsyncJobRepository jobRepository,
                                    CreationWorkflowRunRepository runRepository,
                                    GuidedCreationJsonSupport jsonSupport,
                                    GuidedCreationOutlineJobSupport outlineSupport,
                                    JsonColumnCodec codec,
                                    ApplicationEventPublisher eventPublisher) {
        this.jobRepository = jobRepository;
        this.runRepository = runRepository;
        this.jsonSupport = jsonSupport;
        this.outlineSupport = outlineSupport;
        this.codec = codec;
        this.eventPublisher = eventPublisher;
        this.leaseOwner = resolveLeaseOwner();
    }

    @Transactional
    public AsyncJob enqueue(UUID runId, UUID actorId, GuidedCreationStep step, String hint) {
        CreationWorkflowRun run = requireOwnedRunForUpdate(runId, actorId);
        return enqueueStepCandidatesLocked(run, step, hint);
    }

    @Transactional
    public AsyncJob enqueueAutomatic(UUID runId) {
        CreationWorkflowRun run = requireRunForUpdate(runId);
        if (!run.isAutoRun() || run.getStatus() == CreationWorkflowStatus.COMPLETED) {
            throw new BusinessException("向导未处于自动运行状态");
        }
        return enqueueStepCandidatesLocked(run, run.getCurrentStep(), null);
    }

    @Transactional
    public AsyncJob enqueueOutlineOperation(UUID runId,
                                            UUID actorId,
                                            String directionId,
                                            GuidedCreationOperation operation,
                                            String instruction,
                                            Map<String, Object> editedPayload,
                                            Long expectedVersion) {
        CreationWorkflowRun run = requireOwnedRunForUpdate(runId, actorId);
        return enqueueOutlineOperationLocked(
                run, directionId, operation, instruction, editedPayload, expectedVersion);
    }

    @Transactional
    public AsyncJob enqueueAutomaticOutlineExpand(UUID runId, String directionId) {
        CreationWorkflowRun run = requireRunForUpdate(runId);
        if (!run.isAutoRun() || run.getStatus() == CreationWorkflowStatus.COMPLETED) {
            throw new BusinessException("向导未处于自动运行状态");
        }
        return enqueueOutlineOperationLocked(
                run, directionId, GuidedCreationOperation.OUTLINE_EXPAND, null, null, null);
    }

    private AsyncJob enqueueStepCandidatesLocked(CreationWorkflowRun run,
                                                  GuidedCreationStep step,
                                                  String hint) {
        if (run.getStatus() == CreationWorkflowStatus.COMPLETED || step == GuidedCreationStep.COMPLETED) {
            throw new BusinessException("向导已经完成");
        }
        if (run.getCurrentStep() != step) {
            throw new BusinessException("只能为当前步骤生成候选");
        }
        Map<String, Object> stepData = jsonSupport.stepData(jsonSupport.readSteps(run), step);
        if (!jsonSupport.candidates(stepData).isEmpty()) {
            throw new BusinessException("当前步骤的候选已经生成");
        }

        String idempotencyKey = jobKey(run.getId(), step);
        AsyncJob existing = reusableJob(run, idempotencyKey);
        if (existing != null) return existing;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operation", GuidedCreationOperation.STEP_CANDIDATES.name());
        payload.put("hint", hint == null ? "" : hint.trim());
        return createJob(run, step, idempotencyKey, payload);
    }

    private AsyncJob enqueueOutlineOperationLocked(CreationWorkflowRun run,
                                                    String directionId,
                                                    GuidedCreationOperation operation,
                                                    String instruction,
                                                    Map<String, Object> editedPayload,
                                                    Long expectedVersion) {
        GuidedCreationOutlineJobSupport.PreparedOperation prepared = outlineSupport.prepare(
                run, directionId, operation, instruction, editedPayload, expectedVersion);
        AsyncJob existing = reusableJob(run, prepared.idempotencyKey());
        if (existing != null) return existing;
        if (run.getActiveJobId() != null) {
            throw new BusinessException("已有生成任务正在进行，请稍候");
        }

        return createJob(run, GuidedCreationStep.OUTLINE,
                prepared.idempotencyKey(), prepared.payload());
    }

    private AsyncJob reusableJob(CreationWorkflowRun run, String idempotencyKey) {
        AsyncJob existing = jobRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing == null) return null;
        if (existing.getStatus() == AsyncJobStatus.FAILED
                || existing.getStatus() == AsyncJobStatus.RECOVERY_REQUIRED) {
            throw new BusinessException("上次生成未完成，请使用重试操作");
        }
        run.setActiveJobId(existing.getId());
        runRepository.save(run);
        return existing;
    }

    private AsyncJob createJob(CreationWorkflowRun run,
                               GuidedCreationStep step,
                               String idempotencyKey,
                               Map<String, Object> payload) {
        AsyncJob job = new AsyncJob();
        job.setUser(run.getUser());
        job.setScopeId(run.getId());
        job.setJobType(JOB_TYPE);
        job.setStep(step);
        job.setStatus(AsyncJobStatus.QUEUED);
        job.setProgress(0);
        job.setOutputTokens(0);
        job.setOutputTokensEstimated(true);
        job.setAttemptCount(0);
        job.setIdempotencyKey(idempotencyKey);
        job.setPayloadJson(codec.write(payload, "{}"));
        job = jobRepository.save(job);

        run.setActiveJobId(job.getId());
        run.setErrorMessage(null);
        run.setStatus(run.isAutoRun() ? CreationWorkflowStatus.AUTO_RUNNING : CreationWorkflowStatus.WAITING_USER);
        runRepository.save(run);
        publishQueued(job.getId());
        return job;
    }

    @Transactional
    public JobClaim claim(UUID jobId) {
        AsyncJob job = jobRepository.findByIdForUpdate(jobId).orElse(null);
        if (job == null || job.getStatus() != AsyncJobStatus.QUEUED) return null;
        CreationWorkflowRun run = requireRunForUpdate(job.getScopeId());
        if (run.getStatus() == CreationWorkflowStatus.COMPLETED || run.getCurrentStep() != job.getStep()) {
            job.setStatus(AsyncJobStatus.FAILED);
            job.setErrorMessage("向导步骤已经变化");
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);
            return null;
        }
        job.setStatus(AsyncJobStatus.RUNNING);
        job.setProgress(10);
        job.setAttemptCount(job.getAttemptCount() + 1);
        job.setLeaseOwner(leaseOwner);
        job.setLeaseUntil(Instant.now().plus(10, ChronoUnit.MINUTES));
        jobRepository.save(job);
        return new JobClaim(job.getId(), run.getId(), job.getStep(), operation(job));
    }

    @Transactional
    public GenerationContext markCallingAi(UUID jobId) {
        AsyncJob job = jobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new BusinessException("后台任务不存在"));
        if (job.getStatus() != AsyncJobStatus.RUNNING) {
            throw new BusinessException("后台任务状态已经变化");
        }
        CreationWorkflowRun run = runRepository.findWithUserById(job.getScopeId())
                .orElseThrow(() -> new BusinessException("向导草稿不存在"));
        job.setStatus(AsyncJobStatus.CALLING_AI);
        job.setProgress(30);
        job.setLeaseUntil(Instant.now().plus(10, ChronoUnit.MINUTES));
        jobRepository.save(job);
        return new GenerationContext(run, job, payload(job));
    }

    @Transactional
    public void updateStreamProgress(UUID jobId, long outputTokens, boolean estimated) {
        AsyncJob job = jobRepository.findByIdForUpdate(jobId).orElse(null);
        if (job == null || job.getStatus() != AsyncJobStatus.CALLING_AI) return;
        job.setOutputTokens(Math.max(job.getOutputTokens(), outputTokens));
        job.setOutputTokensEstimated(estimated);
        job.setProgress(Math.max(job.getProgress(), 35));
    }

    @Transactional
    public void completeStreamProgress(UUID jobId, long completionTokens) {
        AsyncJob job = jobRepository.findByIdForUpdate(jobId).orElse(null);
        if (job == null || job.getStatus() != AsyncJobStatus.CALLING_AI) return;
        job.setOutputTokens(completionTokens);
        job.setOutputTokensEstimated(false);
        job.setProgress(Math.max(job.getProgress(), 70));
    }

    @Transactional
    public Completion complete(UUID jobId, GuidedCreationGenerationService.GenerationResult result) {
        AsyncJob job = jobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new BusinessException("后台任务不存在"));
        if (job.getStatus() == AsyncJobStatus.SUCCEEDED) {
            CreationWorkflowRun current = requireRunForUpdate(job.getScopeId());
            return completionOf(current, job,
                    jsonSupport.stepData(jsonSupport.readSteps(current), job.getStep()));
        }
        if (job.getStatus() != AsyncJobStatus.CALLING_AI) {
            throw new BusinessException("后台任务状态已经变化");
        }
        CreationWorkflowRun run = requireRunForUpdate(job.getScopeId());
        if (run.getCurrentStep() != job.getStep()) {
            throw new BusinessException("向导步骤已经变化");
        }

        Map<String, Object> steps = jsonSupport.readSteps(run);
        GuidedCreationOperation operation = operation(job);
        Map<String, Object> stepData = operation == GuidedCreationOperation.STEP_CANDIDATES
                ? new LinkedHashMap<>(result.stepData())
                : mergeOutlineResult(jsonSupport.stepData(steps, GuidedCreationStep.OUTLINE), job, result);
        stepData.put("chargedCredits", numeric(stepData.get("chargedCredits")) + result.chargedCredits());
        stepData.put("remainingCredits", result.remainingCredits());
        steps.put(job.getStep().name(), stepData);
        jsonSupport.writeSteps(run, steps);
        run.setActiveJobId(null);
        run.setErrorMessage(null);
        run.setStatus(run.isAutoRun() ? CreationWorkflowStatus.AUTO_RUNNING : CreationWorkflowStatus.WAITING_USER);
        runRepository.save(run);

        job.setStatus(AsyncJobStatus.SUCCEEDED);
        job.setProgress(100);
        job.setResultJson(codec.write(result.stepData(), "{}"));
        job.setErrorMessage(null);
        job.setChargedCredits(result.chargedCredits());
        job.setRemainingCredits(result.remainingCredits());
        job.setLeaseOwner(null);
        job.setLeaseUntil(null);
        job.setCompletedAt(Instant.now());
        jobRepository.save(job);
        return completionOf(run, job, stepData);
    }

    private Map<String, Object> mergeOutlineResult(Map<String, Object> stepData,
                                                   AsyncJob job,
                                                   GuidedCreationGenerationService.GenerationResult result) {
        GuidedCreationOperation operation = operation(job);
        return outlineSupport.mergeResult(stepData, payload(job), operation, result.stepData());
    }

    @Transactional
    public void fail(UUID jobId, RuntimeException failure) {
        AsyncJob job = jobRepository.findByIdForUpdate(jobId).orElse(null);
        if (job == null || job.getStatus() == AsyncJobStatus.SUCCEEDED) return;
        String message = failureMessage(failure);
        job.setStatus(AsyncJobStatus.FAILED);
        job.setProgress(100);
        job.setErrorMessage(message);
        job.setLeaseOwner(null);
        job.setLeaseUntil(null);
        job.setCompletedAt(Instant.now());
        jobRepository.save(job);

        CreationWorkflowRun run = requireRunForUpdate(job.getScopeId());
        if (job.getId().equals(run.getActiveJobId())) {
            run.setStatus(CreationWorkflowStatus.FAILED);
            run.setErrorMessage(message);
            runRepository.save(run);
        }
    }

    @Transactional
    public void failAutomaticAdvance(UUID runId, RuntimeException failure) {
        CreationWorkflowRun run = requireRunForUpdate(runId);
        if (run.getStatus() == CreationWorkflowStatus.COMPLETED) return;
        run.setStatus(CreationWorkflowStatus.FAILED);
        run.setErrorMessage(failureMessage(failure));
        runRepository.save(run);
    }

    @Transactional
    public AsyncJob retry(UUID runId, UUID actorId) {
        CreationWorkflowRun run = requireOwnedRunForUpdate(runId, actorId);
        if (run.getStatus() == CreationWorkflowStatus.COMPLETED) {
            throw new BusinessException("向导已经完成");
        }
        if (run.getActiveJobId() == null) {
            throw new BusinessException("当前没有可重试的生成任务");
        }
        AsyncJob job = jobRepository.findByIdForUpdate(run.getActiveJobId())
                .orElseThrow(() -> new BusinessException("后台任务不存在"));
        if (job.getStatus() != AsyncJobStatus.FAILED
                && job.getStatus() != AsyncJobStatus.RECOVERY_REQUIRED) {
            throw new BusinessException("当前任务不需要重试");
        }
        job.setStatus(AsyncJobStatus.QUEUED);
        job.setProgress(0);
        job.setOutputTokens(0);
        job.setOutputTokensEstimated(true);
        job.setErrorMessage(null);
        job.setCompletedAt(null);
        job.setLeaseOwner(null);
        job.setLeaseUntil(null);
        jobRepository.save(job);
        run.setStatus(run.isAutoRun() ? CreationWorkflowStatus.AUTO_RUNNING : CreationWorkflowStatus.WAITING_USER);
        run.setErrorMessage(null);
        runRepository.save(run);
        publishQueued(job.getId());
        return job;
    }

    @Transactional
    public void recoverInterruptedJobs() {
        Collection<AsyncJobStatus> states = List.of(
                AsyncJobStatus.QUEUED, AsyncJobStatus.RUNNING, AsyncJobStatus.CALLING_AI);
        for (AsyncJob job : jobRepository.findByStatusIn(states)) {
            CreationWorkflowRun run = runRepository.findByIdForUpdate(job.getScopeId()).orElse(null);
            if (job.getStatus() == AsyncJobStatus.CALLING_AI) {
                String message = "服务重启时 AI 调用状态不确定，请确认后手动重试";
                job.setStatus(AsyncJobStatus.RECOVERY_REQUIRED);
                job.setErrorMessage(message);
                job.setLeaseOwner(null);
                job.setLeaseUntil(null);
                if (run != null && job.getId().equals(run.getActiveJobId())) {
                    run.setStatus(CreationWorkflowStatus.FAILED);
                    run.setErrorMessage(message);
                    runRepository.save(run);
                }
            } else {
                job.setStatus(AsyncJobStatus.QUEUED);
                job.setLeaseOwner(null);
                job.setLeaseUntil(null);
                publishQueued(job.getId());
            }
            jobRepository.save(job);
        }
    }

    @Transactional(readOnly = true)
    public List<UUID> queuedJobIds() {
        return jobRepository.findByStatusIn(List.of(AsyncJobStatus.QUEUED)).stream()
                .map(AsyncJob::getId)
                .toList();
    }

    private CreationWorkflowRun requireOwnedRunForUpdate(UUID runId, UUID actorId) {
        CreationWorkflowRun run = requireRunForUpdate(runId);
        if (actorId == null || !actorId.equals(run.getUser().getId())) {
            throw new BusinessException("无权访问该向导草稿");
        }
        return run;
    }

    private CreationWorkflowRun requireRunForUpdate(UUID runId) {
        return runRepository.findByIdForUpdate(runId)
                .orElseThrow(() -> new BusinessException("向导草稿不存在"));
    }

    private Completion completionOf(CreationWorkflowRun run, AsyncJob job, Map<String, Object> stepData) {
        GuidedCreationOperation operation = operation(job);
        Object candidateId = operation == GuidedCreationOperation.OUTLINE_EXPAND
                ? stepData.get("selectedDirectionId") : stepData.get("recommendedCandidateId");
        return new Completion(
                run.getId(), run.getUser().getId(), job.getStep(), operation, run.isAutoRun(),
                String.valueOf(candidateId));
    }

    private Map<String, Object> payload(AsyncJob job) {
        return new LinkedHashMap<>(codec.read(
                job.getPayloadJson(), new TypeReference<Map<String, Object>>() {}, Map.of()));
    }

    public GuidedCreationOperation operation(AsyncJob job) {
        Object raw = payload(job).get("operation");
        if (raw == null) return GuidedCreationOperation.STEP_CANDIDATES;
        try {
            return GuidedCreationOperation.valueOf(String.valueOf(raw));
        } catch (IllegalArgumentException ignored) {
            return GuidedCreationOperation.STEP_CANDIDATES;
        }
    }

    private long numeric(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private void publishQueued(UUID jobId) {
        eventPublisher.publishEvent(new GuidedCreationJobQueuedEvent(jobId));
    }

    private String jobKey(UUID runId, GuidedCreationStep step) {
        return "g1:" + runId + ":" + step.name().toLowerCase();
    }

    private String failureMessage(RuntimeException failure) {
        String raw = failure.getMessage();
        String message = raw == null || raw.isBlank() ? "生成失败，请重试" : raw.trim();
        return message.length() <= ERROR_LIMIT ? message : message.substring(0, ERROR_LIMIT);
    }

    private String resolveLeaseOwner() {
        try {
            return InetAddress.getLocalHost().getHostName() + ":" + UUID.randomUUID().toString().substring(0, 8);
        } catch (Exception ignored) {
            return "ainovel:" + UUID.randomUUID().toString().substring(0, 8);
        }
    }

    public record JobClaim(UUID jobId,
                           UUID runId,
                           GuidedCreationStep step,
                           GuidedCreationOperation operation) {}

    public record GenerationContext(CreationWorkflowRun run,
                                    AsyncJob job,
                                    Map<String, Object> payload) {}

    public record Completion(UUID runId,
                             UUID userId,
                             GuidedCreationStep step,
                             GuidedCreationOperation operation,
                             boolean autoRun,
                             String recommendedCandidateId) {}
}
