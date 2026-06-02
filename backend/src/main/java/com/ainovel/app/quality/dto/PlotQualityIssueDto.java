package com.ainovel.app.quality.dto;

import java.util.UUID;

public record PlotQualityIssueDto(
        UUID id,
        String dimension,
        String severity,
        int riskScore,
        String evidence,
        String whyItMatters,
        String minimalFix
) {
}
