package com.ainovel.app.auth;

public record SsoTokenExchangeResponse(
        String accessToken,
        Long userId,
        String username,
        String sessionId,
        Integer rememberDays,
        Long expiresIn
) {
}
