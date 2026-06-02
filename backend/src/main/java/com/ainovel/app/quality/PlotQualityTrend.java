package com.ainovel.app.quality;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PlotQualityTrend(
        UUID manuscriptId,
        double averageRisk,
        int highRiskScenes,
        Map<String, Long> dimensionCounts,
        List<Point> points
) {
    public record Point(
            UUID runId,
            UUID sceneId,
            String chapterTitle,
            String sceneTitle,
            int chapterOrder,
            int sceneOrder,
            int riskScore,
            String maxSeverity,
            String status
    ) {
    }
}
