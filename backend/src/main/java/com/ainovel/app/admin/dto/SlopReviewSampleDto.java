package com.ainovel.app.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Slop 校准审核样本")
public record SlopReviewSampleDto(
        UUID id,
        String sourceType,
        UUID sourceRunId,
        UUID storyId,
        UUID manuscriptId,
        UUID sceneId,
        String sampleId,
        String text,
        String textPreview,
        String genre,
        String tone,
        String chapterTitle,
        String sceneTitle,
        String characterContext,
        String styleContext,
        String expectedEvidenceLevel,
        boolean expectedRequiresAiReview,
        String observedEvidenceLevel,
        boolean observedRequiresAiReview,
        int observedRiskScore,
        String observedMaxSeverity,
        boolean matchesExpected,
        String status,
        String reviewerNote,
        String createdBy,
        String reviewedBy,
        Instant reviewedAt,
        Instant createdAt,
        Instant updatedAt
) {}
