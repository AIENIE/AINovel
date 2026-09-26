package com.ainovel.app.manuscript.dto;
import java.util.UUID;
import java.time.Instant;
public record ManuscriptSummaryDto(UUID id, UUID outlineId, String title, String worldId, UUID currentBranchId, long version, Instant updatedAt) {}
