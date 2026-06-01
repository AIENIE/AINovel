package com.ainovel.app.quality;

import java.util.List;
import java.util.UUID;

public record SlopQualityResult(
        UUID runId,
        String acceptedText,
        int overallRiskScore,
        SlopSeverity maxSeverity,
        boolean revised,
        int revisionCount,
        SlopQualityStatus status,
        List<SlopIssueDraft> issues
) {
}
