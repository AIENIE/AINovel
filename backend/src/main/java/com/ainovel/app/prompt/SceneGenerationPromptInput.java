package com.ainovel.app.prompt;

import java.util.List;

public record SceneGenerationPromptInput(
        String storyTitle,
        String genre,
        String tone,
        String storySynopsis,
        String chapterTitle,
        String chapterSummary,
        int chapterOrder,
        String sceneTitle,
        String sceneSummary,
        int sceneOrder,
        String characterContext,
        String previousContext,
        List<PromptReference> materialReferences,
        List<String> avoidExpressions,
        int minHan,
        int maxHan,
        String retryInstruction,
        int tokenBudget,
        String compiledContext,
        String contextCompilerVersion,
        String contextHash
) {
    public SceneGenerationPromptInput(String storyTitle,
                                      String genre,
                                      String tone,
                                      String storySynopsis,
                                      String chapterTitle,
                                      String chapterSummary,
                                      int chapterOrder,
                                      String sceneTitle,
                                      String sceneSummary,
                                      int sceneOrder,
                                      String characterContext,
                                      String previousContext,
                                      List<PromptReference> materialReferences,
                                      List<String> avoidExpressions,
                                      int minHan,
                                      int maxHan,
                                      String retryInstruction,
                                      int tokenBudget) {
        this(storyTitle, genre, tone, storySynopsis, chapterTitle, chapterSummary, chapterOrder,
                sceneTitle, sceneSummary, sceneOrder, characterContext, previousContext,
                materialReferences, avoidExpressions, minHan, maxHan, retryInstruction, tokenBudget,
                "", "", "");
    }
}
