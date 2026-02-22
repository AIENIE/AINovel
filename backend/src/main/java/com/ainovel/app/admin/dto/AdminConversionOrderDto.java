package com.ainovel.app.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "管理端通用积分兑换订单")
public record AdminConversionOrderDto(
        @Schema(description = "记录 ID")
        String id,
        @Schema(description = "订单号")
        String orderNo,
        @Schema(description = "用户 ID（本地 UUID）")
        String userId,
        @Schema(description = "用户名")
        String username,
        @Schema(description = "请求兑换数量")
        long requestedAmount,
        @Schema(description = "实际兑换数量")
        long convertedAmount,
        @Schema(description = "兑换前项目积分")
        long projectBefore,
        @Schema(description = "兑换后项目积分")
        long projectAfter,
        @Schema(description = "兑换前通用积分")
        long publicBefore,
        @Schema(description = "兑换后通用积分")
        long publicAfter,
        @Schema(description = "状态", example = "SUCCESS")
        String status,
        @Schema(description = "状态描述")
        String message,
        @Schema(description = "创建时间")
        Instant createdAt
) {
}
