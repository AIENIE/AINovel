package com.ainovel.app.adminauth;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminAuthPolicyValidatorTest {
    private static final String BCRYPT = "$2a$10$dEnQNJUtxcEVsri32i2KmuRrrOBNtvoyn8j1HBUHpRRBjB14bNUgq";

    @Test
    void exactOsPolicyRejectsMissingWhitespaceCaseQuotesAndUnknownValues() {
        for (String invalid : new String[] { null, "", " local", "local ", "LOCAL", "\"local\"", "dev" }) {
            SystemEnvAdminAuthPolicySource source = source(invalid, "totp");
            assertThrows(IllegalStateException.class, source::env, String.valueOf(invalid));
        }
        for (String invalid : new String[] { null, "", " totp", "totp ", "TOTP", "\"totp\"", "disabled" }) {
            SystemEnvAdminAuthPolicySource source = source("local", invalid);
            assertThrows(IllegalStateException.class, source::authMode, String.valueOf(invalid));
        }
    }

    @Test
    void allowsOnlyTheFourDeclaredEnvironmentModeCombinations() {
        assertDoesNotThrow(() -> validator("local", "password", false).validate());
        assertDoesNotThrow(() -> validator("local", "totp", true).validate());
        assertDoesNotThrow(() -> validator("test", "totp", true).validate());
        assertDoesNotThrow(() -> validator("production", "totp", true).validate());
        assertThrows(IllegalStateException.class, () -> validator("test", "password", false).validate());
        assertThrows(IllegalStateException.class, () -> validator("production", "password", false).validate());
    }

    @Test
    void passwordModeDoesNotRequireTotpKeyring() {
        AdminLocalAuthProperties properties = properties(false);
        properties.setEncryptionKeys("this-is-not-a-keyring");
        properties.setActiveKeyVersion("missing");
        assertDoesNotThrow(() -> new AdminAuthCrypto(properties, fixed("local", "password")));
    }

    @Test
    void rejectsAWeakBcryptCostInEveryEnvironment() {
        AdminLocalAuthProperties local = properties(false);
        local.setPasswordHash(BCRYPT.replace("$10$", "$04$"));
        assertThrows(IllegalStateException.class, () -> new AdminAuthPolicyValidator(
                fixed("local", "password"), local
        ).validate());

        AdminLocalAuthProperties production = properties(true);
        production.setPasswordHash(BCRYPT.replace("$10$", "$04$"));
        assertThrows(IllegalStateException.class, () -> new AdminAuthPolicyValidator(
                fixed("production", "totp"), production
        ).validate());
    }

    private SystemEnvAdminAuthPolicySource source(String env, String mode) {
        Map<String, String> values = new java.util.HashMap<>();
        values.put("ENV", env);
        values.put("AUTH_MODE", mode);
        return new SystemEnvAdminAuthPolicySource(values::get);
    }

    private AdminAuthPolicyValidator validator(String env, String mode, boolean totp) {
        AdminLocalAuthProperties properties = properties(totp);
        return new AdminAuthPolicyValidator(fixed(env, mode), properties);
    }

    private AdminLocalAuthProperties properties(boolean totp) {
        AdminLocalAuthProperties properties = new AdminLocalAuthProperties();
        properties.setUsername("admin");
        properties.setPasswordHash(BCRYPT);
        properties.setTrustedOrigins("https://localainovel.testhut.top");
        if (totp) {
            properties.setEncryptionKeys("v1:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
            properties.setActiveKeyVersion("v1");
        }
        return properties;
    }

    private AdminAuthPolicySource fixed(String env, String mode) {
        return new AdminAuthPolicySource() {
            @Override public String env() { return env; }
            @Override public String authMode() { return mode; }
        };
    }
}
