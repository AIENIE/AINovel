package com.ainovel.app.quality;

import java.util.List;

public record SlopHeuristicResult(
        int overallRiskScore,
        SlopSeverity maxSeverity,
        boolean requiresAiReview,
        List<SlopIssueDraft> issues,
        List<SlopShadowHit> shadowHits
) {
    public SlopHeuristicResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
        shadowHits = shadowHits == null ? List.of() : List.copyOf(shadowHits);
    }
}
