package com.ainovel.app.auth;

import jakarta.validation.constraints.NotBlank;

public record SsoTokenExchangeRequest(
        @NotBlank String code,
        @NotBlank String redirect
) {
}
