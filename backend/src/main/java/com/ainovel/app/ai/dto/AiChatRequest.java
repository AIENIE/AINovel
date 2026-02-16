package com.ainovel.app.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "AI 对话请求")
public record AiChatRequest(
        @Schema(description = "多轮消息列表")
        List<Message> messages,
        @Schema(description = "模型 ID", example = "2")
        String modelId,
        @Schema(description = "上下文对象（可选）", example = "{\"scene\":\"chapter-1\"}")
        Object context
) {
    @Schema(description = "单条对话消息")
    public record Message(
            @Schema(description = "角色", example = "user")
            String role,
            @Schema(description = "内容", example = "请帮我优化这一段。")
            String content
    ) {}
}
