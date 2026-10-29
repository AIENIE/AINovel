package com.ainovel.app.world.dto;

import java.util.Map;

public record WorldModuleUpdateRequest(Map<String, String> fields, Long expectedRevision) {
    public WorldModuleUpdateRequest(Map<String, String> fields) {
        this(fields, null);
    }
}
