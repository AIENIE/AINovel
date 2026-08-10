package com.ainovel.app.adminauth;

public final class AdminAuthPolicyRules {
    private AdminAuthPolicyRules() {
    }

    public static boolean isAllowed(String env, String mode) {
        return (AdminAuthPolicySource.ENV_LOCAL.equals(env)
                && (AdminAuthPolicySource.AUTH_MODE_PASSWORD.equals(mode)
                || AdminAuthPolicySource.AUTH_MODE_TOTP.equals(mode)))
                || ((AdminAuthPolicySource.ENV_TEST.equals(env)
                || AdminAuthPolicySource.ENV_PRODUCTION.equals(env))
                && AdminAuthPolicySource.AUTH_MODE_TOTP.equals(mode));
    }

    public static void requireAllowed(String env, String mode) {
        if (!isAllowed(env, mode)) {
            throw new IllegalStateException("Invalid administrator authentication policy. Allowed combinations: "
                    + "local/password, local/totp, test/totp, production/totp");
        }
    }
}
