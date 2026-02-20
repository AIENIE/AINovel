package com.ainovel.app.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "积分流水项")
public record CreditLedgerItemResponse(
        @Schema(description = "流水 ID")
        String id,
        @Schema(description = "流水类型", example = "CHECKIN")
        String type,
        @Schema(description = "变化值", example = "50")
        double delta,
        @Schema(description = "变更后项目积分余额", example = "1050")
        double balanceAfter,
        @Schema(description = "引用类型", example = "REDEEM_CODE")
        String referenceType,
        @Schema(description = "引用 ID", example = "VIP888")
        String referenceId,
        @Schema(description = "描述", example = "每日签到奖励")
        String description,
        @Schema(description = "创建时间")
        Instant createdAt
) {
}

