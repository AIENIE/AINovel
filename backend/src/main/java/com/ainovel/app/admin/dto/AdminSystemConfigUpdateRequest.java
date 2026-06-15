package com.ainovel.app.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "系统配置更新请求")
public record AdminSystemConfigUpdateRequest(
        @Schema(description = "是否启用维护模式", example = "false")
        Boolean maintenanceMode
) {}
