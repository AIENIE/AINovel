package com.ainovel.app.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "通用积分兑换响应")
public record ConvertCreditsResponse(
        @Schema(description = "兑换订单号", example = "CVT-6A4C2710D8D845B4B09C")
        String orderNo,
        @Schema(description = "本次兑换数量", example = "100")
        double amount,
        @Schema(description = "兑换前项目专属积分", example = "1200.0")
        double projectBefore,
        @Schema(description = "兑换后项目专属积分", example = "1300.0")
        double projectAfter,
        @Schema(description = "兑换前通用积分", example = "300.0")
        double publicBefore,
        @Schema(description = "兑换后通用积分", example = "200.0")
        double publicAfter,
        @Schema(description = "总余额", example = "1500.0")
        double totalCredits
) {
}
