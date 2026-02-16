package com.ainovel.app.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 润色请求")
public record AiRefineRequest(
        @Schema(description = "待润色文本", example = "原始文本内容")
        String text,
        @Schema(description = "润色指令", example = "保持语义不变，增强文学性")
        String instruction,
        @Schema(description = "模型 ID", example = "2")
        String modelId
) {}
