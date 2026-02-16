package com.ainovel.app.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "用户创作汇总")
public record UserSummaryResponse(
        @Schema(description = "小说数量", example = "6")
        long novelCount,
        @Schema(description = "世界观数量", example = "3")
        long worldCount,
        @Schema(description = "累计字数", example = "120000")
        long totalWords,
        @Schema(description = "世界观已填写条目数", example = "88")
        long totalEntries
) {}
