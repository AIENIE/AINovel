package com.ainovel.app.admin.dto;

import java.util.UUID;

public record SlopReviewSampleMismatchDto(
        UUID id,
        String sampleId,
        String expectedEvidenceLevel,
        String observedEvidenceLevel,
        boolean expectedRequiresAiReview,
        boolean observedRequiresAiReview,
        int observedRiskScore,
        String status,
        String textPreview
) {
}
