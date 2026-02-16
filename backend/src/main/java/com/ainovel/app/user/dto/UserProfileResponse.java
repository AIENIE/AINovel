package com.ainovel.app.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "用户资料响应")
public record UserProfileResponse(
        @Schema(description = "本地用户 UUID", example = "2f2ac8d9-3b9b-45f9-a4a0-6f1f0899a9d1")
        UUID id,
        @Schema(description = "用户名", example = "demo")
        String username,
        @Schema(description = "邮箱", example = "demo@example.com")
        String email,
        @Schema(description = "头像 URL", example = "https://example.com/avatar.png")
        String avatar,
        @Schema(description = "角色（admin/user）", example = "user")
        String role,
        @Schema(description = "当前可用资产", example = "1200.0")
        double credits,
        @Schema(description = "是否封禁", example = "false")
        boolean isBanned,
        @Schema(description = "最近签到时间", example = "2026-02-16T08:00:00Z")
        Instant lastCheckIn
) {}
