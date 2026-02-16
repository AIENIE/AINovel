package com.ainovel.app.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 调用 token 统计")
public record AiUsageDto(
        @Schema(description = "输入 token 数", example = "120")
        double inputTokens,
        @Schema(description = "输出 token 数", example = "86")
        double outputTokens,
        @Schema(description = "预估成本", example = "0.0")
        double cost
) {}
