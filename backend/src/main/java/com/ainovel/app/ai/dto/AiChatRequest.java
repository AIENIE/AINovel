package com.ainovel.app.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonInclude;

@Schema(description = "AI 对话请求")
public record AiChatRequest(
        @Schema(description = "多轮消息列表")
        List<Message> messages,
        @Schema(description = "模型 ID", example = "2")
        String modelId,
        @Schema(description = "上下文对象（可选）", example = "{\"scene\":\"chapter-1\"}")
        JsonNode context,
        String featureKey,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Schema(hidden = true) String toolsJson,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Schema(hidden = true) Integer maxOutputTokens
) {
    public AiChatRequest(List<Message> messages,String modelId,JsonNode context) {this(messages,modelId,context,null,null,null);}
    public AiChatRequest(List<Message> messages,String modelId,JsonNode context,String featureKey) {this(messages,modelId,context,featureKey,null,null);}
    public AiChatRequest forFeature(String feature) {return new AiChatRequest(messages,modelId,context,feature,toolsJson,maxOutputTokens);}
    public AiChatRequest withNativeTools(String tools,int outputTokens) {return new AiChatRequest(messages,modelId,context,featureKey,tools,outputTokens);}
    public boolean nativeTools() {return toolsJson != null;}
    public boolean hasNativeFields() {return toolsJson != null || messages != null && messages.stream().anyMatch(m -> m != null && m.hasNativeFields());}
    @Schema(description = "单条对话消息")
    public record Message(
            @Schema(description = "角色", example = "user")
            String role,
            @Schema(description = "内容", example = "请帮我优化这一段。")
            String content,
            @JsonInclude(JsonInclude.Include.NON_NULL) @Schema(hidden = true) String toolCallId,
            @JsonInclude(JsonInclude.Include.NON_NULL) @Schema(hidden = true) String toolCallsJson,
            @JsonInclude(JsonInclude.Include.NON_NULL) @Schema(hidden = true) String reasoningContent,
            @JsonInclude(JsonInclude.Include.NON_NULL) @Schema(hidden = true) String providerState
    ) {
        public Message(String role,String content) {this(role,content,null,null,null,null);}
        public boolean hasNativeFields() {return toolCallId != null || toolCallsJson != null || reasoningContent != null || providerState != null;}
    }
}
