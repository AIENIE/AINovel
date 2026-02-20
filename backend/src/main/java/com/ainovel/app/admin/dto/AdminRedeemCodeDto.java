package com.ainovel.app.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "兑换码信息")
public record AdminRedeemCodeDto(
        @Schema(description = "兑换码 ID")
        String id,
        @Schema(description = "兑换码", example = "WELCOME2026")
        String code,
        @Schema(description = "奖励积分", example = "50")
        double grantAmount,
        @Schema(description = "最大可用次数", example = "1000")
        Integer maxUses,
        @Schema(description = "已用次数", example = "122")
        int usedCount,
        @Schema(description = "生效时间")
        Instant startsAt,
        @Schema(description = "过期时间")
        Instant expiresAt,
        @Schema(description = "是否启用")
        boolean enabled,
        @Schema(description = "是否允许同一用户重复领取")
        boolean stackable,
        @Schema(description = "备注")
        String description
) {
}

