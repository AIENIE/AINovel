package com.ainovel.app.world.dto;

import java.util.List;
import java.util.Map;

public record WorldUpdateRequest(String name, String tagline, List<String> themes, String creativeIntent, String notes,
                                 Map<String, Map<String, String>> modules, Long expectedRevision) {
    public WorldUpdateRequest(String name, String tagline, List<String> themes, String creativeIntent, String notes) {
        this(name, tagline, themes, creativeIntent, notes, null, null);
    }
}
