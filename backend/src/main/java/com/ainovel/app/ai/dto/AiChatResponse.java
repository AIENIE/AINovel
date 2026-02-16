package com.ainovel.app.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 对话响应")
public record AiChatResponse(
        @Schema(description = "响应角色", example = "assistant")
        String role,
        @Schema(description = "响应内容", example = "你好，我可以帮你梳理剧情结构。")
        String content,
        @Schema(description = "token 使用统计")
        AiUsageDto usage,
        @Schema(description = "剩余资产", example = "1200.0")
        double remainingCredits
) {}
