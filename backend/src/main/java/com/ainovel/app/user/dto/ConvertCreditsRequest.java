package com.ainovel.app.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "通用积分兑换项目积分请求")
public record ConvertCreditsRequest(
        @Schema(description = "兑换数量（1:1）", example = "100")
        @Min(1)
        long amount,
        @Schema(description = "幂等键，同一请求重试必须保持一致", example = "conv-20260220-001")
        @NotBlank
        String idempotencyKey
) {
}

