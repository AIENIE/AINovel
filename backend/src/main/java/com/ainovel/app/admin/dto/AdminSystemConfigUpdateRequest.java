package com.ainovel.app.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "系统配置更新请求")
public record AdminSystemConfigUpdateRequest(
        @Schema(description = "是否允许新用户注册", example = "true")
        Boolean registrationEnabled,
        @Schema(description = "是否启用维护模式", example = "false")
        Boolean maintenanceMode,
        @Schema(description = "签到奖励最小值", example = "10")
        Integer checkInMinPoints,
        @Schema(description = "签到奖励最大值", example = "50")
        Integer checkInMaxPoints,
        @Schema(description = "SMTP 主机", example = "smtp.example.com")
        String smtpHost,
        @Schema(description = "SMTP 端口", example = "587")
        Integer smtpPort,
        @Schema(description = "SMTP 用户名", example = "noreply@example.com")
        String smtpUsername,
        @Schema(description = "SMTP 密码（明文传输后由服务端存储）", example = "******")
        String smtpPassword
) {}
