package com.ainovel.app.story.dto;

public record ChapterUpdateRequest(
        String title,
        String summary,
        Integer order,
        Long expectedRevision
) {
    public ChapterUpdateRequest(String title, String summary, Integer order) { this(title, summary, order, null); }
}

