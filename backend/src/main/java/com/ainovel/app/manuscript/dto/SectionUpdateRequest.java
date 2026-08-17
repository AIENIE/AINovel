package com.ainovel.app.manuscript.dto;

import jakarta.validation.constraints.NotNull;

public record SectionUpdateRequest(String content, @NotNull Long expectedVersion) {}
