package com.ainovel.app.workflow.dto;

import com.ainovel.app.workflow.model.AsyncJobStatus;
import com.ainovel.app.workflow.model.CreationWorkflowStatus;
import com.ainovel.app.workflow.model.GuidedCreationStep;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class CreationWorkflowDtos {
    private CreationWorkflowDtos() {}

    public record CreateRunRequest(
            @NotBlank @Size(max = 1000) String seedIdea,
            @Size(max = 100) String genre,
            @Size(max = 100) String tone,
            @Min(3) @Max(12) Integer targetChapterCount,
            Boolean autoRun
    ) {}

    public record GenerateStepRequest(@Size(max = 500) String hint) {}

    public record ConfirmStepRequest(
            @NotBlank String candidateId,
            Map<String, Object> editedPayload,
            Long version
    ) {}

    public record AutoRunRequest(@Min(3) @Max(12) Integer targetChapterCount) {}

    public record JobResponse(
            UUID id,
            GuidedCreationStep step,
            AsyncJobStatus status,
            int progress,
            String errorMessage,
            long chargedCredits,
            long remainingCredits,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record WorkflowResponse(
            UUID id,
            String templateKey,
            CreationWorkflowStatus status,
            GuidedCreationStep currentStep,
            String seedIdea,
            String genre,
            String tone,
            int targetChapterCount,
            boolean autoRun,
            Map<String, Object> steps,
            UUID storyId,
            UUID worldId,
            UUID outlineId,
            String errorMessage,
            long version,
            JobResponse activeJob,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt
    ) {}

    public record AcceptedResponse(@NotNull UUID workflowId, @NotNull UUID jobId) {}
}
