package com.ainovel.app.manuscript.dto;

import jakarta.validation.constraints.NotNull;

public record SectionUpdateRequest(@NotNull String content, @NotNull Long expectedVersion, java.util.UUID expectedBranchId) {
    public SectionUpdateRequest(@NotNull String content, Long expectedVersion) { this(content, expectedVersion, null); }
}
