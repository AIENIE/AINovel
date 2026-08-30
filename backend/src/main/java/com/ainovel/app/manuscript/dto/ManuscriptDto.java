package com.ainovel.app.manuscript.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ManuscriptDto(UUID id,
                            UUID outlineId,
                            String title,
                            String worldId,
                            Map<String, String> sections,
                            @JsonInclude(JsonInclude.Include.NON_NULL)
                            SceneGenerationRunSummaryDto lastGenerationRun,
                            long version,
                            Instant updatedAt) {}
