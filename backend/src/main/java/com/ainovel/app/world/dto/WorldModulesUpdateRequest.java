package com.ainovel.app.world.dto;

import java.util.Map;

public record WorldModulesUpdateRequest(Map<String, Map<String, String>> modules, Long expectedRevision) {
    public WorldModulesUpdateRequest(Map<String, Map<String, String>> modules) {
        this(modules, null);
    }
}
