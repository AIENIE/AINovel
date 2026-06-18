package com.ainovel.app.quality;

public record SlopHeuristicPolicy(
        int singleWeakSignalRisk,
        int expectedStyleWeakSignalRisk,
        int mediumDensityRisk,
        int highDensityRisk,
        int aiReviewRiskThreshold,
        int genericPhraseMediumDensityCount,
        int genericPhraseHighDensityCount
) {
    public static SlopHeuristicPolicy defaultPolicy() {
        return new SlopHeuristicPolicy(
                34,
                28,
                58,
                72,
                55,
                3,
                5
        );
    }
}
