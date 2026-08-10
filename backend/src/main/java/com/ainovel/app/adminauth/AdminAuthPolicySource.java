package com.ainovel.app.adminauth;

/**
 * Administrator authentication policy. Production decisions are sourced only
 * from the exact OS environment variables ENV and AUTH_MODE.
 */
public interface AdminAuthPolicySource {
    String ENV_LOCAL = "local";
    String ENV_TEST = "test";
    String ENV_PRODUCTION = "production";
    String AUTH_MODE_PASSWORD = "password";
    String AUTH_MODE_TOTP = "totp";

    String env();
    String authMode();

    default boolean requiresTotp() {
        return AUTH_MODE_TOTP.equals(authMode());
    }

    default boolean isPasswordMode() {
        return AUTH_MODE_PASSWORD.equals(authMode());
    }
}
