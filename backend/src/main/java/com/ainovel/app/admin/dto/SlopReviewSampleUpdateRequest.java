package com.ainovel.app.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Slop 校准样本审核更新请求")
public record SlopReviewSampleUpdateRequest(
        @Schema(description = "审核状态", example = "APPROVED")
        String status,
        @Schema(description = "期望证据等级", example = "E2")
        String expectedEvidenceLevel,
        @Schema(description = "期望是否触发 AI review", example = "true")
        Boolean expectedRequiresAiReview,
        @Schema(description = "审核备注")
        String reviewerNote
) {}
