package com.ainovel.app.adminauth;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

@Component
public class AdminAuthPolicyValidator {
    private static final Pattern BCRYPT = Pattern.compile(
            "^\\$2[aby]\\$(?:0[4-9]|[12][0-9]|3[01])\\$[./A-Za-z0-9]{53}$"
    );
    private final AdminAuthPolicySource policy;
    private final AdminLocalAuthProperties properties;

    public AdminAuthPolicyValidator(AdminAuthPolicySource policy, AdminLocalAuthProperties properties) {
        this.policy = policy;
        this.properties = properties;
    }

    @PostConstruct
    public void validate() {
        String env = policy.env();
        String mode = policy.authMode();
        AdminAuthPolicyRules.requireAllowed(env, mode);
        requireText(properties.getUsername(), "ADMIN_USERNAME");
        String passwordHash = requireText(properties.getPasswordHash(), "ADMIN_PASSWORD_HASH");
        if (!BCRYPT.matcher(passwordHash).matches()) {
            throw new IllegalStateException("ADMIN_PASSWORD_HASH must be a BCrypt hash");
        }
        int bcryptCost = Integer.parseInt(passwordHash.substring(4, 6));
        if (bcryptCost < 10) {
            throw new IllegalStateException("ADMIN_PASSWORD_HASH BCrypt cost must be at least 10");
        }
        if (properties.getSessionMinutes() < 1 || properties.getSessionIdleMinutes() < 1
                || properties.getRecoverySessionMinutes() < 1 || properties.getRecoverySessionIdleMinutes() < 1) {
            throw new IllegalStateException("Administrator session lifetimes must be positive");
        }
        if (properties.getSessionIdleMinutes() > properties.getSessionMinutes()
                || properties.getRecoverySessionIdleMinutes() > properties.getRecoverySessionMinutes()) {
            throw new IllegalStateException("Administrator idle timeout must not exceed its absolute lifetime");
        }
        if (policy.requiresTotp()) {
            requireText(properties.getEncryptionKeys(), "ADMIN_TOTP_ENCRYPTION_KEYS");
            requireText(properties.getActiveKeyVersion(), "ADMIN_TOTP_ACTIVE_KEY_VERSION");
        }
        if ((AdminAuthPolicySource.ENV_TEST.equals(env) || AdminAuthPolicySource.ENV_PRODUCTION.equals(env))
                && properties.getTrustedOrigins().isBlank()) {
            throw new IllegalStateException("ADMIN_TRUSTED_ORIGINS is required outside local environment");
        }
        if (!AdminAuthPolicySource.ENV_LOCAL.equals(env) && !properties.isCookieSecure()) {
            throw new IllegalStateException("ADMIN_SESSION_COOKIE_SECURE must be true outside local environment");
        }
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must not be blank");
        }
        if (!value.equals(value.trim()) || value.getBytes(StandardCharsets.UTF_8).length == 0) {
            throw new IllegalStateException(name + " contains invalid surrounding whitespace");
        }
        return value;
    }
}
