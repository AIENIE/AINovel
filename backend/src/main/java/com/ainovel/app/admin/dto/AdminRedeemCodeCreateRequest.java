package com.ainovel.app.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

@Schema(description = "创建兑换码请求")
public record AdminRedeemCodeCreateRequest(
        @Schema(description = "兑换码", example = "WELCOME2026")
        @NotBlank
        String code,
        @Schema(description = "奖励积分", example = "50")
        @Min(1)
        long grantAmount,
        @Schema(description = "最大可用次数，为空表示不限制", example = "1000")
        Integer maxUses,
        @Schema(description = "生效时间，可空")
        Instant startsAt,
        @Schema(description = "过期时间，可空")
        Instant expiresAt,
        @Schema(description = "是否启用", example = "true")
        Boolean enabled,
        @Schema(description = "是否允许同一用户重复领取", example = "true")
        Boolean stackable,
        @Schema(description = "备注", example = "新用户欢迎奖励")
        String description
) {
}

