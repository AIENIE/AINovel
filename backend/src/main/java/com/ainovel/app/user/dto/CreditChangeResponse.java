package com.ainovel.app.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "积分/Token 变更响应")
public record CreditChangeResponse(
        @Schema(description = "是否成功", example = "true")
        boolean success,
        @Schema(description = "本次变更值", example = "500.0")
        double points,
        @Schema(description = "变更后总量", example = "1500.0")
        double newTotal
) {}
