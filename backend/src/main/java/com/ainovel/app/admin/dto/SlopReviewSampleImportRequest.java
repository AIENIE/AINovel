package com.ainovel.app.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Slop 校准样本 JSONL 导入请求")
public record SlopReviewSampleImportRequest(
        @NotBlank
        @Schema(description = "JSONL 文本；每行一条样本，也支持 JSON 数组")
        String content
) {
}
