package com.ainovel.app.adminauth;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminAuthCryptoTest {
    @Test
    void recoveryCodesUseArgon2AndRemainVerifiable() {
        AdminAuthCrypto crypto = new AdminAuthCrypto(new AdminLocalAuthProperties(), policy("local", "password"));

        String hash = crypto.hashRecoveryCode("RECOVERY-CODE");

        assertTrue(hash.startsWith("$argon2"));
        assertTrue(crypto.matchesRecoveryCode("RECOVERY-CODE", hash));
    }

    @Test
    void totpKeyringRejectsDuplicateOrNonExactVersions() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        AdminLocalAuthProperties duplicate = new AdminLocalAuthProperties();
        duplicate.setEncryptionKeys("v1:" + key + ",v1:" + key);
        duplicate.setActiveKeyVersion("v1");
        assertThrows(IllegalStateException.class, () -> new AdminAuthCrypto(duplicate, policy("test", "totp")));

        AdminLocalAuthProperties whitespace = new AdminLocalAuthProperties();
        whitespace.setEncryptionKeys(" v1:" + key);
        whitespace.setActiveKeyVersion("v1");
        assertThrows(IllegalStateException.class, () -> new AdminAuthCrypto(whitespace, policy("test", "totp")));
    }

    private AdminAuthPolicySource policy(String env, String mode) {
        return new AdminAuthPolicySource() {
            @Override public String env() { return env; }
            @Override public String authMode() { return mode; }
        };
    }
}
