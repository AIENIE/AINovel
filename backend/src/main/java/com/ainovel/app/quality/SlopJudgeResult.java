package com.ainovel.app.quality;

import java.util.List;

public record SlopJudgeResult(
        int riskScore,
        boolean revisionRecommended,
        List<SlopIssueDraft> issues,
        String actionableHint
) {
}
