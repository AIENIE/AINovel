package com.ainovel.app.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "管理员发放项目积分请求")
public record AdminGrantCreditsRequest(
        @Schema(description = "目标用户 ID（支持本地 UUID 或 remoteUid）", example = "1001")
        @NotBlank
        String userId,
        @Schema(description = "发放积分", example = "100")
        @Min(1)
        long amount,
        @Schema(description = "发放原因", example = "活动补偿")
        String reason
) {
}

