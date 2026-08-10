package com.ainovel.app.adminauth;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AdminSessionServiceTest {
    @Test
    void issuesRecoverySessionWithItsOwnLifetimeAndIdleTimeout() {
        AdminAuthStore store = mock(AdminAuthStore.class);

        AdminLocalAuthProperties properties = new AdminLocalAuthProperties();
        properties.setPasswordHash("$2a$10$dEnQNJUtxcEVsri32i2KmuRrrOBNtvoyn8j1HBUHpRRBjB14bNUgq");
        properties.setRecoverySessionMinutes(15);
        properties.setRecoverySessionIdleMinutes(10);
        AdminAuthPolicySource policy = new AdminAuthPolicySource() {
            @Override public String env() { return "test"; }
            @Override public String authMode() { return "totp"; }
        };
        AdminSessionService service = new AdminSessionService(store, properties, policy);

        Instant passwordAt = Instant.now().minusSeconds(5);
        AdminSessionService.Issued issued = service.issue(
                "configured-admin", "RECOVERY", "PASSWORD_RECOVERY", passwordAt, null, "v1"
        );

        assertTrue(issued.expiresAt().isAfter(Instant.now().plusSeconds(14 * 60)));
        assertTrue(issued.token().length() >= 40);
        verify(store).insertSession(
                any(), eq("configured-admin"), eq("RECOVERY"), eq("test"), eq("totp"),
                eq("PASSWORD_RECOVERY"), eq(passwordAt), eq(null), eq("v1"),
                eq(AdminSessionService.hash(properties.getPasswordHash())), any(), any(), any()
        );
    }
}
