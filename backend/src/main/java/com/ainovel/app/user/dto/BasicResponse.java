package com.ainovel.app.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "基础布尔响应")
public record BasicResponse(
        @Schema(description = "是否成功", example = "false")
        boolean success,
        @Schema(description = "响应信息", example = "PASSWORD_MANAGED_BY_SSO")
        String message
) {}
