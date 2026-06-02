package com.ainovel.app.quality.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlotQualityRunDto(
        UUID id,
        UUID storyId,
        UUID manuscriptId,
        UUID sceneId,
        String chapterTitle,
        String sceneTitle,
        int chapterOrder,
        int sceneOrder,
        String status,
        String maxSeverity,
        int overallRiskScore,
        String summary,
        String rewritePlanJson,
        String surgicalFixesJson,
        String revisionCandidateText,
        boolean revisionApplied,
        Instant revisionAppliedAt,
        Instant createdAt,
        List<PlotQualityIssueDto> issues
) {
}
