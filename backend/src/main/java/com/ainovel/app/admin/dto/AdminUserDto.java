package com.ainovel.app.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "管理端用户信息")
public record AdminUserDto(
        @Schema(description = "用户 ID（UserService 侧）", example = "1001")
        String id,
        @Schema(description = "用户名", example = "demo")
        String username,
        @Schema(description = "邮箱", example = "demo@example.com")
        String email,
        @Schema(description = "角色（admin/user）", example = "user")
        String role,
        @Schema(description = "当前资产快照", example = "1200.0")
        double credits,
        @Schema(description = "是否封禁", example = "false")
        boolean isBanned,
        @Schema(description = "最近签到时间", example = "2026-02-16T08:00:00Z")
        Instant lastCheckIn
) {}
