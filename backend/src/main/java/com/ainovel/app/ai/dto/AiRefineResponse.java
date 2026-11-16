package com.ainovel.app.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 润色响应")
public record AiRefineResponse(
        @Schema(description = "润色结果", example = "润色后的文本内容")
        String result,
        @Schema(description = "token 使用统计")
        AiUsageDto usage,
        @Schema(description = "旧结果记录的积分合计", nullable = true)
        Double remainingCredits,
        @Schema(description = "项目专属积分余额", example = "1180")
        Long remainingProjectCredits
) {
    public static AiRefineResponse fromChat(AiChatResponse response) {
        return new AiRefineResponse(response.content(), response.usage(), response.remainingCredits(),
                response.remainingProjectCredits());
    }
}
