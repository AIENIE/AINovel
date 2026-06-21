package com.ainovel.app.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Slop 校准样本创建请求")
public record SlopReviewSampleCreateRequest(
        @Schema(description = "样本外部编号", example = "P6-density-001")
        String sampleId,
        @NotBlank
        @Schema(description = "待审核文本")
        String text,
        @Schema(description = "题材", example = "都市悬疑")
        String genre,
        @Schema(description = "语气", example = "冷峻")
        String tone,
        @Schema(description = "角色语境")
        String characterContext,
        @Schema(description = "风格语境")
        String styleContext,
        @NotBlank
        @Schema(description = "期望证据等级", example = "E2")
        String expectedEvidenceLevel,
        @Schema(description = "期望是否触发 AI review", example = "true")
        Boolean expectedRequiresAiReview,
        @Schema(description = "审核备注")
        String reviewerNote
) {}
