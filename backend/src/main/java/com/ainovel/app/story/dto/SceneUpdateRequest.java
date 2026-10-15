package com.ainovel.app.story.dto;

public record SceneUpdateRequest(
        String title,
        String summary,
        String content,
        Integer order,
        Long expectedRevision
) {
    public SceneUpdateRequest(String title, String summary, String content, Integer order) { this(title, summary, content, order, null); }
}

