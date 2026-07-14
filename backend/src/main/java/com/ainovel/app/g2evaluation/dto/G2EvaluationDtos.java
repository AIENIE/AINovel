package com.ainovel.app.g2evaluation.dto;

import com.ainovel.app.g2evaluation.model.G2EvaluationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class G2EvaluationDtos {
    private G2EvaluationDtos() {
    }

    public record CreateExperimentRequest(
            @NotBlank @Size(max = 180) String title,
            @NotEmpty List<@NotBlank String> reviewerUsernames
    ) {
    }

    public record TransitionRequest(@NotNull G2EvaluationStatus status) {
    }

    public record SubmitSampleRequest(@NotNull UUID manuscriptId, @NotNull UUID sceneId) {
    }

    public record VoteRequest(@NotNull UUID sampleId, @NotBlank String choice) {
    }

    public record ExperimentResponse(
            UUID id,
            String title,
            G2EvaluationStatus status,
            int invitedReviewers,
            long readySamplePairs,
            long pendingSamplePairs,
            long validVotes,
            long reviewersWithVotes,
            long craftedWins,
            double craftedWinRate,
            boolean gatePassed,
            int minimumVotes,
            int minimumSamplePairs,
            int minimumReviewers,
            double craftedWinRateTarget,
            Instant createdAt
    ) {
    }

    public record SampleSubmissionResponse(UUID sampleId, String status) {
    }

    public record ReviewSampleResponse(UUID sampleId, String leftText, String rightText) {
    }
}
