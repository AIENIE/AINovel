package com.ainovel.app.ai.dto;

import com.ainovel.app.modelconfig.AiModelSelection;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 对话响应")
public record AiChatResponse(
        @Schema(description = "响应角色", example = "assistant")
        String role,
        @Schema(description = "响应内容", example = "你好，我可以帮你梳理剧情结构。")
        String content,
        @Schema(description = "token 使用统计")
        AiUsageDto usage,
        @Schema(description = "旧结果记录的积分合计", nullable = true)
        Double remainingCredits,
        AiModelSelection selection,
        String actualModelKey,
        AiResponseMetadata metadata,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        @Schema(hidden = true) String nativeResponseJson,
        @Schema(description = "项目专属积分余额", example = "1200")
        Long remainingProjectCredits
) {
    public AiChatResponse(String role, String content, AiUsageDto usage, double remainingCredits,
                          AiModelSelection selection, String actualModelKey, AiResponseMetadata metadata,
                          String nativeResponseJson) {
        this(role, content, usage, remainingCredits, selection, actualModelKey, metadata, nativeResponseJson, null);
    }

    public AiChatResponse(String role, String content, AiUsageDto usage, double remainingCredits,
                          AiModelSelection selection, String actualModelKey, AiResponseMetadata metadata) {
        this(role, content, usage, remainingCredits, selection, actualModelKey, metadata, null);
    }

    public AiChatResponse(String role, String content, AiUsageDto usage, double remainingCredits,
                          AiModelSelection selection, String actualModelKey) {
        this(role, content, usage, remainingCredits, selection, actualModelKey, null, null);
    }

    public AiChatResponse(String role, String content, AiUsageDto usage, double remainingCredits) {
        this(role, content, usage, remainingCredits, null, null, null, null);
    }

    public AiNativeResult nativeResult() {
        return AiNativeResult.parse(nativeResponseJson);
    }
}
