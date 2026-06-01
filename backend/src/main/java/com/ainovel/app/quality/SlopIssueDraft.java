package com.ainovel.app.quality;

public record SlopIssueDraft(
        SlopDimension dimension,
        SlopSeverity severity,
        int riskScore,
        String evidence,
        String whyItMatters,
        String minimalFix
) {
}
