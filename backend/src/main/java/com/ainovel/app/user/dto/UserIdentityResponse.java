package com.ainovel.app.user.dto;

import java.util.UUID;

public record UserIdentityResponse(UUID id, String username, String email, String avatar, String role,
                                   boolean isBanned) {
}
