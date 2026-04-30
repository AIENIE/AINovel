package com.ainovel.app.story.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record StoryCreateRequest(@NotBlank String title,
                                 String synopsis,
                                 String genre,
                                 String tone,
                                 String worldId,
                                 Map<String, Object> plotPlanningHints) {}
