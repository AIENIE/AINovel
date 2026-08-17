package com.ainovel.app.manuscript.context;

import java.util.List;
import java.util.UUID;

public record SceneDraftContextManifest(
        String compilerVersion,
        String contextHash,
        String hashAlgorithm,
        int tokenBudget,
        int tokenUsed,
        UUID storyId,
        UUID outlineId,
        UUID manuscriptId,
        UUID sceneId,
        UUID boundWorldId,
        int chapterOrder,
        int sceneOrder,
        String sceneType,
        List<Source> sources,
        List<String> warnings
) {
    public SceneDraftContextManifest {
        sources = sources == null ? List.of() : List.copyOf(sources);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public record Source(
            String slot,
            String sourceType,
            String sourceId,
            String label,
            String reason,
            int priority,
            int estimatedTokens,
            boolean truncated,
            String contentHash
    ) {
    }
}
