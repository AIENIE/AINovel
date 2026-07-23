package com.ainovel.app.workflow;

import com.ainovel.app.common.BusinessException;
import com.ainovel.app.user.User;
import com.ainovel.app.workflow.dto.CreationWorkflowDtos;
import com.ainovel.app.workflow.model.AsyncJob;
import com.ainovel.app.workflow.model.CreationWorkflowRun;
import com.ainovel.app.workflow.model.CreationWorkflowStatus;
import com.ainovel.app.workflow.model.GuidedCreationStep;
import com.ainovel.app.workflow.model.GuidedCreationOperation;
import com.ainovel.app.workflow.repo.AsyncJobRepository;
import com.ainovel.app.workflow.repo.CreationWorkflowRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;

@Service
public class GuidedCreationWorkflowService {
    private static final Logger log = LoggerFactory.getLogger(GuidedCreationWorkflowService.class);
    private static final String TEMPLATE_KEY = "quick-book-v1";

    private final CreationWorkflowRunRepository runRepository;
    private final AsyncJobRepository jobRepository;
    private final GuidedCreationJobService jobService;
    private final GuidedCreationMaterializer materializer;
    private final GuidedCreationJsonSupport jsonSupport;

    public GuidedCreationWorkflowService(CreationWorkflowRunRepository runRepository,
                                         AsyncJobRepository jobRepository,
                                         GuidedCreationJobService jobService,
                                         GuidedCreationMaterializer materializer,
                                         GuidedCreationJsonSupport jsonSupport) {
        this.runRepository = runRepository;
        this.jobRepository = jobRepository;
        this.jobService = jobService;
        this.materializer = materializer;
        this.jsonSupport = jsonSupport;
    }

    @Transactional
    public CreationWorkflowDtos.WorkflowResponse create(User user,
                                                        CreationWorkflowDtos.CreateRunRequest request) {
        CreationWorkflowRun run = new CreationWorkflowRun();
        run.setUser(user);
        run.setTemplateKey(TEMPLATE_KEY);
        run.setCurrentStep(GuidedCreationStep.PREMISE);
        run.setSeedIdea(request.seedIdea().trim());
        run.setGenre(normalized(request.genre()));
        run.setTone(normalized(request.tone()));
        run.setTargetChapterCount(request.targetChapterCount() == null ? 6 : request.targetChapterCount());
        run.setAutoRun(Boolean.TRUE.equals(request.autoRun()));
        run.setStatus(run.isAutoRun() ? CreationWorkflowStatus.AUTO_RUNNING : CreationWorkflowStatus.WAITING_USER);
        run.setStepsJson("{}");
        run = runRepository.save(run);
        if (run.isAutoRun()) {
            jobService.enqueueAutomatic(run.getId());
        }
        log.info("Guided creation workflow created runId={} userId={} autoRun={}",
                run.getId(), user.getId(), run.isAutoRun());
        return responseFor(runRepository.findWithUserById(run.getId()).orElse(run));
    }

    @Transactional(readOnly = true)
    public List<CreationWorkflowDtos.WorkflowResponse> list(User user) {
        return runRepository.findByUserOrderByUpdatedAtDesc(user).stream()
                .map(this::responseFor)
                .toList();
    }

    @Transactional(readOnly = true)
    public CreationWorkflowDtos.WorkflowResponse get(User user, UUID runId) {
        return responseFor(requireOwnedRun(runId, user.getId()));
    }

    @Transactional
    public CreationWorkflowDtos.AcceptedResponse generate(User user,
                                                          UUID runId,
                                                          GuidedCreationStep step,
                                                          CreationWorkflowDtos.GenerateStepRequest request) {
        AsyncJob job = jobService.enqueue(runId, user.getId(), step, request == null ? null : request.hint());
        return new CreationWorkflowDtos.AcceptedResponse(runId, job.getId());
    }

    @Transactional
    public CreationWorkflowDtos.AcceptedResponse developOutlineDirection(
            User user,
            UUID runId,
            String directionId,
            CreationWorkflowDtos.DevelopOutlineDirectionRequest request) {
        GuidedCreationOperation operation = switch (request.action().trim().toLowerCase(Locale.ROOT)) {
            case "continue" -> GuidedCreationOperation.OUTLINE_DEVELOP;
            case "rewrite" -> GuidedCreationOperation.OUTLINE_REWRITE;
            default -> throw new BusinessException("大纲方向操作只能是 continue 或 rewrite");
        };
        AsyncJob job = jobService.enqueueOutlineOperation(
                runId, user.getId(), directionId, operation,
                request.instruction(), request.editedPayload(), request.version());
        return new CreationWorkflowDtos.AcceptedResponse(runId, job.getId());
    }

    @Transactional
    public CreationWorkflowDtos.AcceptedResponse expandOutlineDirection(
            User user,
            UUID runId,
            String directionId,
            CreationWorkflowDtos.ExpandOutlineDirectionRequest request) {
        AsyncJob job = jobService.enqueueOutlineOperation(
                runId, user.getId(), directionId, GuidedCreationOperation.OUTLINE_EXPAND,
                null, request.editedPayload(), request.version());
        return new CreationWorkflowDtos.AcceptedResponse(runId, job.getId());
    }

    @Transactional
    public CreationWorkflowDtos.WorkflowResponse confirm(User user,
                                                         UUID runId,
                                                         GuidedCreationStep step,
                                                         CreationWorkflowDtos.ConfirmStepRequest request) {
        CreationWorkflowRun run = materializer.confirm(
                runId, user.getId(), step, request.candidateId(), request.editedPayload(), request.version(), false);
        return responseFor(run);
    }

    @Transactional
    public CreationWorkflowDtos.WorkflowResponse skipWorld(User user, UUID runId, Long version) {
        return responseFor(materializer.skipWorld(runId, user.getId(), version, false));
    }

    @Transactional
    public CreationWorkflowDtos.WorkflowResponse startAuto(User user,
                                                           UUID runId,
                                                           CreationWorkflowDtos.AutoRunRequest request) {
        CreationWorkflowRun run = requireOwnedRunForUpdate(runId, user.getId());
        if (run.getStatus() == CreationWorkflowStatus.COMPLETED) {
            return responseFor(run);
        }
        if (request != null && request.targetChapterCount() != null) {
            run.setTargetChapterCount(request.targetChapterCount());
        }
        run.setAutoRun(true);
        run.setStatus(CreationWorkflowStatus.AUTO_RUNNING);
        run.setErrorMessage(null);
        runRepository.save(run);
        resumeAutomaticLocked(run);
        return responseFor(requireOwnedRun(runId, user.getId()));
    }

    @Transactional
    public CreationWorkflowDtos.WorkflowResponse retry(User user, UUID runId) {
        CreationWorkflowRun run = requireOwnedRunForUpdate(runId, user.getId());
        if (run.getActiveJobId() != null) {
            jobService.retry(runId, user.getId());
        } else if (run.isAutoRun()) {
            run.setStatus(CreationWorkflowStatus.AUTO_RUNNING);
            run.setErrorMessage(null);
            runRepository.save(run);
            resumeAutomaticLocked(run);
        } else {
            throw new BusinessException("当前没有可重试的生成任务");
        }
        return responseFor(requireOwnedRun(runId, user.getId()));
    }

    @Transactional
    public void advanceAutomatic(GuidedCreationJobService.Completion completion) {
        if (!completion.autoRun()) {
            return;
        }
        if (completion.step() == GuidedCreationStep.OUTLINE
                && completion.operation() == GuidedCreationOperation.STEP_CANDIDATES) {
            jobService.enqueueAutomaticOutlineExpand(
                    completion.runId(), completion.recommendedCandidateId());
            return;
        }
        CreationWorkflowRun run = materializer.confirm(
                completion.runId(), completion.userId(), completion.step(),
                completion.recommendedCandidateId(), null, null, true);
        if (run.getStatus() != CreationWorkflowStatus.COMPLETED) {
            jobService.enqueueAutomatic(run.getId());
        }
        log.info("Guided creation automatic step advanced runId={} step={} nextStep={}",
                run.getId(), completion.step(), run.getCurrentStep());
    }

    @Transactional
    public void resumeAutomatic(UUID runId) {
        CreationWorkflowRun run = runRepository.findByIdForUpdate(runId).orElse(null);
        if (run == null || !run.isAutoRun() || run.getStatus() != CreationWorkflowStatus.AUTO_RUNNING) {
            return;
        }
        resumeAutomaticLocked(run);
    }

    @Transactional(readOnly = true)
    public List<UUID> activeAutomaticRunIds() {
        return runRepository.findByAutoRunTrueAndStatusIn(List.of(CreationWorkflowStatus.AUTO_RUNNING)).stream()
                .map(CreationWorkflowRun::getId)
                .toList();
    }

    private void resumeAutomaticLocked(CreationWorkflowRun run) {
        if (run.getCurrentStep() == GuidedCreationStep.COMPLETED) {
            return;
        }
        Map<String, Object> stepData = jsonSupport.stepData(jsonSupport.readSteps(run), run.getCurrentStep());
        if (!jsonSupport.candidates(stepData).isEmpty()) {
            if (run.getCurrentStep() == GuidedCreationStep.OUTLINE) {
                String phase = String.valueOf(stepData.get("outlinePhase"));
                if ("DIRECTION_SELECTION".equals(phase)) {
                    if (run.getActiveJobId() == null) {
                        jobService.enqueueAutomaticOutlineExpand(
                                run.getId(), String.valueOf(stepData.get("recommendedCandidateId")));
                    }
                    return;
                }
                if ("OUTLINE_PREVIEW".equals(phase)) {
                    String candidateId = String.valueOf(stepData.get("selectedDirectionId"));
                    CreationWorkflowRun advanced = materializer.confirm(
                            run.getId(), run.getUser().getId(), run.getCurrentStep(),
                            candidateId, null, null, true);
                    if (advanced.getStatus() != CreationWorkflowStatus.COMPLETED) {
                        jobService.enqueueAutomatic(advanced.getId());
                    }
                    return;
                }
            }
            String candidateId = String.valueOf(stepData.get("recommendedCandidateId"));
            CreationWorkflowRun advanced = materializer.confirm(
                    run.getId(), run.getUser().getId(), run.getCurrentStep(), candidateId, null, null, true);
            if (advanced.getStatus() != CreationWorkflowStatus.COMPLETED) {
                jobService.enqueueAutomatic(advanced.getId());
            }
            return;
        }
        if (run.getActiveJobId() == null) {
            jobService.enqueueAutomatic(run.getId());
        }
    }

    private CreationWorkflowRun requireOwnedRun(UUID runId, UUID userId) {
        CreationWorkflowRun run = runRepository.findWithUserById(runId)
                .orElseThrow(() -> new BusinessException("向导草稿不存在"));
        if (!userId.equals(run.getUser().getId())) {
            throw new BusinessException("无权访问该向导草稿");
        }
        return run;
    }

    private CreationWorkflowRun requireOwnedRunForUpdate(UUID runId, UUID userId) {
        CreationWorkflowRun run = runRepository.findByIdForUpdate(runId)
                .orElseThrow(() -> new BusinessException("向导草稿不存在"));
        if (!userId.equals(run.getUser().getId())) {
            throw new BusinessException("无权访问该向导草稿");
        }
        return run;
    }

    private CreationWorkflowDtos.WorkflowResponse responseFor(CreationWorkflowRun run) {
        AsyncJob activeJob = run.getActiveJobId() == null
                ? null : jobRepository.findWithUserById(run.getActiveJobId()).orElse(null);
        return new CreationWorkflowDtos.WorkflowResponse(
                run.getId(), run.getTemplateKey(), run.getStatus(), run.getCurrentStep(),
                run.getSeedIdea(), run.getGenre(), run.getTone(), run.getTargetChapterCount(), run.isAutoRun(),
                jsonSupport.readSteps(run),
                run.getStory() == null ? null : run.getStory().getId(),
                run.getWorld() == null ? null : run.getWorld().getId(),
                run.getOutline() == null ? null : run.getOutline().getId(),
                run.getErrorMessage(), run.getVersion(), jobResponse(activeJob),
                run.getCreatedAt(), run.getUpdatedAt(), run.getCompletedAt()
        );
    }

    private CreationWorkflowDtos.JobResponse jobResponse(AsyncJob job) {
        if (job == null) {
            return null;
        }
        return new CreationWorkflowDtos.JobResponse(
                job.getId(), job.getStep(), jobService.operation(job), job.getStatus(), job.getProgress(),
                job.getOutputTokens(), job.isOutputTokensEstimated(), job.getErrorMessage(),
                job.getChargedCredits(), job.getRemainingCredits(), job.getCreatedAt(), job.getUpdatedAt()
        );
    }

    private String normalized(String value) {
        if (value == null) {
            return null;
        }
        String result = value.trim();
        return result.isBlank() ? null : result;
    }
}
