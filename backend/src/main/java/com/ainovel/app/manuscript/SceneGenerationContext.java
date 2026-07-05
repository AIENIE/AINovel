package com.ainovel.app.manuscript;

import java.util.List;
import java.util.UUID;

record SceneGenerationContext(
        UUID sceneId,
        String chapterTitle,
        String chapterSummary,
        Integer chapterOrder,
        String sceneTitle,
        String sceneSummary,
        Integer sceneOrder,
        List<UUID> previousSceneIds,
        List<String> siblingSceneTitles
) {
}
