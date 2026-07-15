package com.ainovel.app.workflow;

import com.ainovel.app.common.BusinessException;
import com.ainovel.app.common.JsonColumnCodec;
import com.ainovel.app.user.User;
import com.ainovel.app.workflow.model.AsyncJob;
import com.ainovel.app.workflow.model.AsyncJobStatus;
import com.ainovel.app.workflow.model.CreationWorkflowRun;
import com.ainovel.app.workflow.model.CreationWorkflowStatus;
import com.ainovel.app.workflow.model.GuidedCreationStep;
import com.ainovel.app.workflow.repo.AsyncJobRepository;
import com.ainovel.app.workflow.repo.CreationWorkflowRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuidedCreationJobServiceTest {
    @Test
    void refusesAccessToAnotherUsersWorkflow() {
        Fixtures fixtures = new Fixtures();
        when(fixtures.runRepository.findByIdForUpdate(fixtures.run.getId()))
                .thenReturn(Optional.of(fixtures.run));

        BusinessException error = assertThrows(BusinessException.class, () -> fixtures.service.enqueue(
                fixtures.run.getId(), UUID.randomUUID(), GuidedCreationStep.PREMISE, null));

        assertTrue(error.getMessage().contains("无权访问"));
        verify(fixtures.jobRepository, never()).save(any());
    }

    @Test
    void reusesTheSingleQueuedJobForAStep() {
        Fixtures fixtures = new Fixtures();
        AsyncJob existing = fixtures.job(AsyncJobStatus.QUEUED);
        when(fixtures.runRepository.findByIdForUpdate(fixtures.run.getId()))
                .thenReturn(Optional.of(fixtures.run));
        when(fixtures.jobRepository.findByIdempotencyKey(
                "g1:" + fixtures.run.getId() + ":premise")).thenReturn(Optional.of(existing));

        AsyncJob result = fixtures.service.enqueue(
                fixtures.run.getId(), fixtures.user.getId(), GuidedCreationStep.PREMISE, null);

        assertEquals(existing.getId(), result.getId());
        assertEquals(existing.getId(), fixtures.run.getActiveJobId());
        verify(fixtures.jobRepository, never()).save(any());
    }

    @Test
    void marksUncertainAiCallForManualRecoveryOnStartup() {
        Fixtures fixtures = new Fixtures();
        AsyncJob calling = fixtures.job(AsyncJobStatus.CALLING_AI);
        fixtures.run.setActiveJobId(calling.getId());
        when(fixtures.jobRepository.findByStatusIn(any())).thenReturn(List.of(calling));
        when(fixtures.runRepository.findByIdForUpdate(fixtures.run.getId()))
                .thenReturn(Optional.of(fixtures.run));

        fixtures.service.recoverInterruptedJobs();

        assertEquals(AsyncJobStatus.RECOVERY_REQUIRED, calling.getStatus());
        assertEquals(CreationWorkflowStatus.FAILED, fixtures.run.getStatus());
        assertTrue(fixtures.run.getErrorMessage().contains("手动重试"));
        verify(fixtures.jobRepository).save(calling);
        verify(fixtures.runRepository).save(fixtures.run);
    }

    @Test
    void safelyRequeuesWorkThatHadNotStartedCallingAi() {
        Fixtures fixtures = new Fixtures();
        AsyncJob running = fixtures.job(AsyncJobStatus.RUNNING);
        when(fixtures.jobRepository.findByStatusIn(any())).thenReturn(List.of(running));
        when(fixtures.runRepository.findByIdForUpdate(fixtures.run.getId()))
                .thenReturn(Optional.of(fixtures.run));

        fixtures.service.recoverInterruptedJobs();

        assertEquals(AsyncJobStatus.QUEUED, running.getStatus());
        verify(fixtures.publisher).publishEvent(new GuidedCreationJobQueuedEvent(running.getId()));
    }

    private static class Fixtures {
        private final AsyncJobRepository jobRepository = mock(AsyncJobRepository.class);
        private final CreationWorkflowRunRepository runRepository = mock(CreationWorkflowRunRepository.class);
        private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        private final ObjectMapper objectMapper = new ObjectMapper();
        private final User user = user();
        private final CreationWorkflowRun run = run(user);
        private final GuidedCreationJobService service = new GuidedCreationJobService(
                jobRepository, runRepository,
                new GuidedCreationJsonSupport(new JsonColumnCodec(objectMapper)),
                new JsonColumnCodec(objectMapper), publisher);

        private AsyncJob job(AsyncJobStatus status) {
            AsyncJob job = new AsyncJob();
            ReflectionTestUtils.setField(job, "id", UUID.randomUUID());
            job.setUser(user);
            job.setScopeId(run.getId());
            job.setStep(GuidedCreationStep.PREMISE);
            job.setStatus(status);
            job.setIdempotencyKey("g1:" + run.getId() + ":premise");
            return job;
        }

        private static User user() {
            User user = new User();
            user.setId(UUID.randomUUID());
            user.setUsername("owner");
            user.setEmail("owner@example.com");
            user.setPasswordHash("x");
            return user;
        }

        private static CreationWorkflowRun run(User user) {
            CreationWorkflowRun run = new CreationWorkflowRun();
            ReflectionTestUtils.setField(run, "id", UUID.randomUUID());
            run.setUser(user);
            run.setTemplateKey("quick-book-v1");
            run.setCurrentStep(GuidedCreationStep.PREMISE);
            run.setStatus(CreationWorkflowStatus.WAITING_USER);
            run.setSeedIdea("故事想法");
            run.setStepsJson("{}");
            return run;
        }
    }
}
