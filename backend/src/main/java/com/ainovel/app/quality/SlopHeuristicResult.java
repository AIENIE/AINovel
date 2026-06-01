package com.ainovel.app.quality;

import java.util.List;

public record SlopHeuristicResult(
        int overallRiskScore,
        SlopSeverity maxSeverity,
        boolean requiresAiReview,
        List<SlopIssueDraft> issues
) {
}
