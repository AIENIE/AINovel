package com.ainovel.app.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 调用 token 统计")
public record AiUsageDto(
        @Schema(description = "输入 token 数", example = "120")
        double inputTokens,
        @Schema(description = "输出 token 数", example = "86")
        double outputTokens,
        @Schema(description = "供应商上报的缓存命中输入 token 数", example = "64")
        double cacheTokens,
        @Schema(description = "输入 token 缓存命中率，0-1", example = "0.53")
        double cacheHitRate,
        @Schema(description = "预估成本", example = "0.0")
        double cost
) {}
