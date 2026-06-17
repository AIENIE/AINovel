package com.ainovel.app.quality.dto;

import java.util.UUID;

public record SlopQualityIssueDto(
        UUID id,
        String dimension,
        String severity,
        int riskScore,
        String evidence,
        String whyItMatters,
        String minimalFix,
        Integer charStart,
        Integer charEnd,
        String quote,
        String module,
        String patternId,
        String issueType,
        String evidenceLevel,
        String alternativeExplanationsJson,
        String repairHint
) {
}
