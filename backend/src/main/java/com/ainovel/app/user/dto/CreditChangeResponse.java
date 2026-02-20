package com.ainovel.app.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "积分/Token 变更响应")
public record CreditChangeResponse(
        @Schema(description = "是否成功", example = "true")
        boolean success,
        @Schema(description = "本次变更值", example = "500.0")
        double points,
        @Schema(description = "兼容字段：变更后总量（= totalCredits）", example = "1500.0")
        double newTotal,
        @Schema(description = "项目专属积分余额", example = "1200.0")
        double projectCredits,
        @Schema(description = "通用积分余额", example = "300.0")
        double publicCredits,
        @Schema(description = "总余额（项目 + 通用）", example = "1500.0")
        double totalCredits,
        @Schema(description = "业务状态码", example = "CHECKIN_SUCCESS")
        String message
) {}
