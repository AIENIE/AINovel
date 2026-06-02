package com.ainovel.app.quality;

import java.util.UUID;

public record PlotQualityRequest(
        UUID storyId,
        UUID manuscriptId,
        UUID sceneId,
        String storyTitle,
        String genre,
        String tone,
        String chapterTitle,
        int chapterOrder,
        String sceneTitle,
        int sceneOrder,
        String sceneSummary,
        String outlinePlanning,
        String previousContext,
        String characterContext,
        String sceneText
) {
}
