package com.ainovel.app.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 模型选项")
public record AiModelDto(
        @Schema(description = "模型 ID（第三方服务侧）", example = "2")
        String id,
        @Schema(description = "模型键", example = "2")
        String name,
        @Schema(description = "展示名称", example = "Gemini 2.5 Flash")
        String displayName,
        @Schema(description = "模型类型（text/embedding/unspecified）", example = "text")
        String modelType,
        @Schema(description = "输入计费倍率", example = "1.0")
        double inputMultiplier,
        @Schema(description = "输出计费倍率", example = "1.0")
        double outputMultiplier,
        @Schema(description = "提供方", example = "PackyAPI")
        String poolId,
        @Schema(description = "是否可用", example = "true")
        boolean isEnabled
) {}
