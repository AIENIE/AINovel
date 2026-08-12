package com.ainovel.app.manuscript.dto;

import com.ainovel.app.manuscript.attribution.SceneGenerationRunStatus;

import java.time.Instant;
import java.util.UUID;

public record SceneGenerationRunSummaryDto(
        UUID id,
        UUID generationVersionId,
        SceneGenerationRunStatus status,
        Instant createdAt
) {}
