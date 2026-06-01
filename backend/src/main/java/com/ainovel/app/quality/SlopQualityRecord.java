package com.ainovel.app.quality;

import java.util.List;

public record SlopQualityRecord(
        SlopQualityRequest request,
        String acceptedText,
        int overallRiskScore,
        SlopSeverity maxSeverity,
        boolean revised,
        int revisionCount,
        SlopQualityStatus status,
        List<SlopIssueDraft> issues,
        String summary
) {
}
