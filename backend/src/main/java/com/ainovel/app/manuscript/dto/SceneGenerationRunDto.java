package com.ainovel.app.manuscript.dto;

import com.ainovel.app.manuscript.attribution.SceneGenerationRunStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SceneGenerationRunDto(
        UUID id,
        UUID manuscriptId,
        UUID sceneId,
        UUID createdBy,
        String mode,
        SceneGenerationRunStatus status,
        String modelKey,
        String promptVersion,
        int attemptCount,
        String contextHash,
        Map<String, Object> contextManifest,
        UUID generationVersionId,
        UUID previousRunId,
        Instant firstEditedAt,
        Instant lastEditedAt,
        Integer addedCharacters,
        Integer deletedCharacters,
        Double retentionRate,
        boolean recalculationPending,
        List<String> feedbackTags,
        String feedbackNote,
        boolean preferenceConfirmed,
        Instant createdAt,
        Instant updatedAt
) {}
