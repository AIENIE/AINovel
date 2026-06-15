package com.ainovel.app.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "管理端用户信息")
public record AdminUserDto(
        @Schema(description = "用户 ID（AINovel 本地 UUID）", example = "2f2ac8d9-3b9b-45f9-a4a0-6f1f0899a9d1")
        String id,
        @Schema(description = "统一用户 ID 快照", example = "1001")
        Long remoteUid,
        @Schema(description = "用户名", example = "demo")
        String username,
        @Schema(description = "邮箱", example = "demo@example.com")
        String email,
        @Schema(description = "角色（admin/user）", example = "user")
        String role,
        @Schema(description = "项目专属积分快照", example = "1200")
        long projectCredits,
        @Schema(description = "通用积分快照", example = "300")
        long publicCredits,
        @Schema(description = "总资产快照", example = "1500")
        long totalCredits,
        @Schema(description = "故事数", example = "3")
        long storyCount,
        @Schema(description = "世界观数", example = "2")
        long worldCount,
        @Schema(description = "是否为本项目本地禁用状态", example = "false")
        boolean isBanned,
        @Schema(description = "创建时间")
        Instant createdAt,
        @Schema(description = "更新时间")
        Instant updatedAt
) {}
