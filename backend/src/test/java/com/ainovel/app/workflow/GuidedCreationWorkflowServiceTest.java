package com.ainovel.app.workflow;

import com.ainovel.app.workflow.model.CreationWorkflowRun;
import com.ainovel.app.workflow.model.CreationWorkflowStatus;
import com.ainovel.app.workflow.model.GuidedCreationStep;
import com.ainovel.app.workflow.model.GuidedCreationOperation;
import com.ainovel.app.workflow.repo.AsyncJobRepository;
import com.ainovel.app.workflow.repo.CreationWorkflowRunRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GuidedCreationWorkflowServiceTest {
    @Test
    void automaticCompletionSelectsRecommendationAndQueuesNextStep() {
        CreationWorkflowRunRepository runRepository = mock(CreationWorkflowRunRepository.class);
        AsyncJobRepository jobRepository = mock(AsyncJobRepository.class);
        GuidedCreationJobService jobService = mock(GuidedCreationJobService.class);
        GuidedCreationMaterializer materializer = mock(GuidedCreationMaterializer.class);
        GuidedCreationJsonSupport jsonSupport = mock(GuidedCreationJsonSupport.class);
        GuidedCreationWorkflowService service = new GuidedCreationWorkflowService(
                runRepository, jobRepository, jobService, materializer, jsonSupport);
        UUID runId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CreationWorkflowRun advanced = new CreationWorkflowRun();
        ReflectionTestUtils.setField(advanced, "id", runId);
        advanced.setCurrentStep(GuidedCreationStep.WORLD);
        advanced.setStatus(CreationWorkflowStatus.AUTO_RUNNING);
        when(materializer.confirm(runId, userId, GuidedCreationStep.PREMISE,
                "recommended", null, null, true)).thenReturn(advanced);

        service.advanceAutomatic(new GuidedCreationJobService.Completion(
                runId, userId, GuidedCreationStep.PREMISE,
                GuidedCreationOperation.STEP_CANDIDATES, true, "recommended"));

        verify(materializer).confirm(runId, userId, GuidedCreationStep.PREMISE,
                "recommended", null, null, true);
        verify(jobService).enqueueAutomatic(runId);
        assertEquals(GuidedCreationStep.WORLD, advanced.getCurrentStep());
    }

    @Test
    void automaticOutlineDirectionsExpandRecommendationBeforeConfirmation() {
        CreationWorkflowRunRepository runRepository = mock(CreationWorkflowRunRepository.class);
        AsyncJobRepository jobRepository = mock(AsyncJobRepository.class);
        GuidedCreationJobService jobService = mock(GuidedCreationJobService.class);
        GuidedCreationMaterializer materializer = mock(GuidedCreationMaterializer.class);
        GuidedCreationJsonSupport jsonSupport = mock(GuidedCreationJsonSupport.class);
        GuidedCreationWorkflowService service = new GuidedCreationWorkflowService(
                runRepository, jobRepository, jobService, materializer, jsonSupport);
        UUID runId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        service.advanceAutomatic(new GuidedCreationJobService.Completion(
                runId, userId, GuidedCreationStep.OUTLINE,
                GuidedCreationOperation.STEP_CANDIDATES, true, "direction-b"));

        verify(jobService).enqueueAutomaticOutlineExpand(runId, "direction-b");
        verifyNoInteractions(materializer);
    }

    @Test
    void repeatedConfirmationOfSameCandidateIsIdempotent() {
        CreationWorkflowRunRepository runRepository = mock(CreationWorkflowRunRepository.class);
        CreationWorkflowRun run = new CreationWorkflowRun();
        UUID runId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ReflectionTestUtils.setField(run, "id", runId);
        var user = new com.ainovel.app.user.User();
        user.setId(userId);
        run.setUser(user);
        run.setCurrentStep(GuidedCreationStep.WORLD);
        run.setStepsJson("{\"PREMISE\":{\"selectedCandidateId\":\"same\"}}");
        when(runRepository.findByIdForUpdate(runId)).thenReturn(Optional.of(run));
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        GuidedCreationMaterializer materializer = new GuidedCreationMaterializer(
                runRepository,
                mock(com.ainovel.app.story.repo.StoryRepository.class),
                mock(com.ainovel.app.world.repo.WorldRepository.class),
                mock(com.ainovel.app.story.repo.CharacterCardRepository.class),
                mock(com.ainovel.app.story.repo.OutlineRepository.class),
                new GuidedCreationJsonSupport(new com.ainovel.app.common.JsonColumnCodec(mapper)),
                new com.ainovel.app.common.JsonColumnCodec(mapper));

        CreationWorkflowRun result = materializer.confirm(
                runId, userId, GuidedCreationStep.PREMISE, "same", null, null, false);

        assertEquals(run, result);
    }
}
