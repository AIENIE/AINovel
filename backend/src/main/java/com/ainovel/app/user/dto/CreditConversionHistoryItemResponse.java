package com.ainovel.app.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "通用积分兑换项目积分历史项")
public record CreditConversionHistoryItemResponse(
        @Schema(description = "记录 ID")
        String id,
        @Schema(description = "订单号", example = "CVT-6A4C2710D8D845B4B09C")
        String orderNo,
        @Schema(description = "请求兑换数量", example = "100")
        long requestedAmount,
        @Schema(description = "实际兑换数量", example = "100")
        long convertedAmount,
        @Schema(description = "兑换前项目积分", example = "1200")
        long projectBefore,
        @Schema(description = "兑换后项目积分", example = "1300")
        long projectAfter,
        @Schema(description = "兑换前通用积分", example = "300")
        long publicBefore,
        @Schema(description = "兑换后通用积分", example = "200")
        long publicAfter,
        @Schema(description = "订单状态", example = "SUCCESS")
        String status,
        @Schema(description = "状态描述", example = "")
        String message,
        @Schema(description = "创建时间")
        Instant createdAt
) {
}
