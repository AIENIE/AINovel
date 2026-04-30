package com.ainovel.app.story.dto;

import java.util.Map;

public record OutlineCreateRequest(String title, String worldId, Map<String, Object> planning) {}
