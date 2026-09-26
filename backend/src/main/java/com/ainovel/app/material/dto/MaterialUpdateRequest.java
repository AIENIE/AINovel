package com.ainovel.app.material.dto;

import java.util.List;

public record MaterialUpdateRequest(String title,
                                    String type,
                                    String summary,
                                    String content,
                                    List<String> tags,
                                    String entitiesJson) {
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("Unsupported material update field");
    }
}
