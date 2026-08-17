package com.ainovel.app.manuscript.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

public record SceneGenerationFeedbackPatchRequest(
        @Size(max = 8) List<@Size(min = 1, max = 40) String> tags,
        String note,
        Boolean preferenceConfirmed
) {}
