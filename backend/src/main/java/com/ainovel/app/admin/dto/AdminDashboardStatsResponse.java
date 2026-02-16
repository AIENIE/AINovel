package com.ainovel.app.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "管理看板统计响应")
public record AdminDashboardStatsResponse(
        @Schema(description = "系统总用户数", example = "12345")
        long totalUsers,
        @Schema(description = "今日新增用户数", example = "128")
        long todayNewUsers,
        @Schema(description = "累计消耗积分/Token", example = "998765.0")
        double totalCreditsConsumed,
        @Schema(description = "今日消耗积分/Token", example = "12345.0")
        double todayCreditsConsumed,
        @Schema(description = "接口错误率（0~1）", example = "0.012")
        double apiErrorRate,
        @Schema(description = "待审核素材数", example = "42")
        long pendingReviews
) {}
