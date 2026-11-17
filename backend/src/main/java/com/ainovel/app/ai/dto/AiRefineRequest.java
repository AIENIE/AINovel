package com.ainovel.app.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.ainovel.app.modelconfig.AiModelRole;

@Schema(description = "AI 润色请求")
public record AiRefineRequest(
        @Schema(description = "待润色文本", example = "原始文本内容")
        String text,
        @Schema(description = "润色指令", example = "保持语义不变，增强文学性")
        String instruction,
        @Schema(description = "模型 ID", example = "2")
        String modelId,
        String featureKey,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        @Schema(hidden = true) AiModelRole modelRole
) {
    public AiRefineRequest(String text, String instruction, String modelId) { this(text, instruction, modelId, null, null); }
    public AiRefineRequest forRole(AiModelRole role, String stepKey) {
        return new AiRefineRequest(text, instruction, modelId, stepKey, role);
    }
}
