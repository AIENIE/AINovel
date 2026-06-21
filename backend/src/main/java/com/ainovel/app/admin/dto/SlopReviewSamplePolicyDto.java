package com.ainovel.app.admin.dto;

public record SlopReviewSamplePolicyDto(
        int singleWeakSignalRisk,
        int expectedStyleWeakSignalRisk,
        int mediumDensityRisk,
        int highDensityRisk,
        int aiReviewRiskThreshold,
        int genericPhraseMediumDensityCount,
        int genericPhraseHighDensityCount
) {
}
