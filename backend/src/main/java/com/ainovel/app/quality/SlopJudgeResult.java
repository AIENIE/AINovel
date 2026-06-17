package com.ainovel.app.quality;

import java.util.List;

public record SlopJudgeResult(
        int riskScore,
        boolean revisionRecommended,
        List<SlopIssueDraft> issues,
        String actionableHint,
        SlopQualitySignals signals
) {
    public SlopJudgeResult(int riskScore,
                           boolean revisionRecommended,
                           List<SlopIssueDraft> issues,
                           String actionableHint) {
        this(riskScore, revisionRecommended, issues, actionableHint,
                SlopQualitySignals.fromIssues(riskScore, maxSeverity(issues, severityForRisk(riskScore)), issues));
    }

    public SlopJudgeResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
        signals = signals == null ? SlopQualitySignals.empty() : signals;
    }

    private static SlopSeverity maxSeverity(List<SlopIssueDraft> issues, SlopSeverity fallback) {
        SlopSeverity max = fallback;
        if (issues == null) {
            return max;
        }
        for (SlopIssueDraft issue : issues) {
            if (issue.severity().ordinal() > max.ordinal()) {
                max = issue.severity();
            }
        }
        return max;
    }

    private static SlopSeverity severityForRisk(int riskScore) {
        if (riskScore >= 85) {
            return SlopSeverity.BLOCKING;
        }
        if (riskScore >= 70) {
            return SlopSeverity.HIGH;
        }
        if (riskScore >= 40) {
            return SlopSeverity.MEDIUM;
        }
        return SlopSeverity.LOW;
    }
}
