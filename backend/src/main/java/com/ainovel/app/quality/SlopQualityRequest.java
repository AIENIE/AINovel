package com.ainovel.app.quality;

import java.util.UUID;

public record SlopQualityRequest(
        UUID storyId,
        UUID manuscriptId,
        UUID sceneId,
        String storyTitle,
        String genre,
        String tone,
        String chapterTitle,
        String sceneTitle,
        String sceneSummary,
        String previousContext,
        String characterContext,
        String styleContext,
        String candidateText,
        String analysisMode
) {
    public SlopQualityRequest(
            UUID storyId,
            UUID manuscriptId,
            UUID sceneId,
            String storyTitle,
            String genre,
            String tone,
            String chapterTitle,
            String sceneTitle,
            String sceneSummary,
            String previousContext,
            String characterContext,
            String styleContext,
            String candidateText
    ) {
        this(storyId, manuscriptId, sceneId, storyTitle, genre, tone, chapterTitle, sceneTitle, sceneSummary,
                previousContext, characterContext, styleContext, candidateText, "generation_gate");
    }
}
