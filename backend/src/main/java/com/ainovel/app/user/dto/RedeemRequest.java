package com.ainovel.app.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "兑换码请求")
public record RedeemRequest(
        @NotBlank
        @Schema(description = "兑换码", example = "VIP888")
        String code
) {}
