package com.ainovel.app.quality.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SlopQualityRunDto(
        UUID id,
        UUID storyId,
        UUID manuscriptId,
        UUID sceneId,
        String status,
        String maxSeverity,
        int overallRiskScore,
        boolean revised,
        int revisionCount,
        String summary,
        Instant createdAt,
        List<SlopQualityIssueDto> issues
) {
}
