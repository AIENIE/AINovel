package com.ainovel.app.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "管理端项目积分流水项")
public record AdminCreditLedgerItemDto(
        @Schema(description = "流水 ID")
        String id,
        @Schema(description = "用户 ID（本地 UUID）")
        String userId,
        @Schema(description = "用户名")
        String username,
        @Schema(description = "类型", example = "AI_DEBIT")
        String type,
        @Schema(description = "变更值", example = "-5")
        long delta,
        @Schema(description = "变更后余额", example = "1295")
        long balanceAfter,
        @Schema(description = "引用类型", example = "AI")
        String referenceType,
        @Schema(description = "引用 ID")
        String referenceId,
        @Schema(description = "描述")
        String description,
        @Schema(description = "创建时间")
        Instant createdAt
) {
}
