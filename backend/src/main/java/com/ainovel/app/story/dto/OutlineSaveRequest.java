package com.ainovel.app.story.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record OutlineSaveRequest(String title,
                                 String worldId,
                                 Map<String, Object> planning,
                                 List<ChapterPayload> chapters,
                                 Long expectedRevision) {
    public OutlineSaveRequest(String title, String worldId, Map<String, Object> planning, List<ChapterPayload> chapters) {
        this(title, worldId, planning, chapters, null);
    }
    public record ChapterPayload(UUID id,
                                 String title,
                                 String summary,
                                 Integer order,
                                 Map<String, Object> planning,
                                 List<ScenePayload> scenes) {}
    public record ScenePayload(UUID id,
                               String title,
                               String summary,
                               String content,
                               Integer order,
                               Map<String, Object> planning) {}
}
