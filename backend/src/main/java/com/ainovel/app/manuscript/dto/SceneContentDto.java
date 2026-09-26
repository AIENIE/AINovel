package com.ainovel.app.manuscript.dto;
import java.util.UUID;
import java.time.Instant;
public record SceneContentDto(UUID manuscriptId, UUID branchId, UUID sceneId, String content, long version, Instant updatedAt) {}
