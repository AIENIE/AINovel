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
        String summary,
        SlopQualitySignals signals
) {
    public SlopQualityRecord(SlopQualityRequest request,
                             String acceptedText,
                             int overallRiskScore,
                             SlopSeverity maxSeverity,
                             boolean revised,
                             int revisionCount,
                             SlopQualityStatus status,
                             List<SlopIssueDraft> issues,
                             String summary) {
        this(request, acceptedText, overallRiskScore, maxSeverity, revised, revisionCount, status, issues, summary,
                SlopQualitySignals.fromIssues(overallRiskScore, maxSeverity, issues));
    }

    public SlopQualityRecord {
        issues = issues == null ? List.of() : List.copyOf(issues);
        signals = signals == null ? SlopQualitySignals.fromIssues(overallRiskScore, maxSeverity, issues) : signals;
    }
}
