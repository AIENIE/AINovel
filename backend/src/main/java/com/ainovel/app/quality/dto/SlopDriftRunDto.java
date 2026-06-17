package com.ainovel.app.quality.dto;

import java.time.Instant;
import java.util.UUID;

public record SlopDriftRunDto(
        UUID id,
        UUID storyId,
        UUID manuscriptId,
        String status,
        int overallRiskScore,
        String riskLabel,
        String safeClaim,
        String summary,
        int totalCharacters,
        int windowCount,
        String sourceTextHash,
        String windowSummariesJson,
        String metricCurvesJson,
        String driftPointsJson,
        String evidenceItemsJson,
        String alternativeExplanationsJson,
        String rewriteTasksJson,
        Instant createdAt
) {
}
