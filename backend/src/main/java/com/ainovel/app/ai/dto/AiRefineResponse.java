package com.ainovel.app.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 润色响应")
public record AiRefineResponse(
        @Schema(description = "润色结果", example = "润色后的文本内容")
        String result,
        @Schema(description = "token 使用统计")
        AiUsageDto usage,
        @Schema(description = "剩余资产", example = "1150.0")
        double remainingCredits
) {}
