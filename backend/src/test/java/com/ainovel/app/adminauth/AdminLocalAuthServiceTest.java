package com.ainovel.app.adminauth;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminLocalAuthServiceTest {
    private static final String PASSWORD_HASH = "$2a$10$dEnQNJUtxcEVsri32i2KmuRrrOBNtvoyn8j1HBUHpRRBjB14bNUgq";

    @Test
    void localPasswordModeIssuesSessionWithoutReadingTotpStateOrCrypto() {
        Fixture fixture = fixture("local", "password");
        AdminSessionService.Issued issued = new AdminSessionService.Issued(
                "opaque", "hash", "FULL", "PASSWORD", Instant.now().plusSeconds(300)
        );
        when(fixture.sessions.issue(anyString(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(issued);

        AdminLocalAuthService.LoginStart result = fixture.service.login(
                "admin", "test-admin-password", "127.0.0.1"
        );

        assertEquals("AUTHENTICATED", result.status());
        assertEquals("PASSWORD", result.session().assurance());
        verify(fixture.store, never()).credentialExists(anyString());
        verify(fixture.crypto, never()).encrypt(anyString(), anyString());
    }

    @Test
    void totpModeVerifiesPasswordBeforeCreatingOneUseLoginChallenge() {
        Fixture fixture = fixture("local", "totp");
        when(fixture.store.credentialExists(AdminLocalAuthService.SUBJECT)).thenReturn(true);
        when(fixture.crypto.randomBase32(32)).thenReturn("LOGIN_CHALLENGE");

        AdminLocalAuthService.LoginStart result = fixture.service.login(
                "admin", "test-admin-password", "127.0.0.1"
        );

        assertEquals("TOTP_REQUIRED", result.status());
        assertNotNull(result.challengeId());
        verify(fixture.store).insertChallenge(
                anyString(), anyString(), anyString(), any(), any(), any()
        );
        verify(fixture.sessions, never()).issue(anyString(), anyString(), anyString(), any(), any(), any());
    }

    private Fixture fixture(String env, String mode) {
        AdminLocalAuthProperties properties = new AdminLocalAuthProperties();
        properties.setUsername("admin");
        properties.setPasswordHash(PASSWORD_HASH);
        properties.setEncryptionKeys("v1:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        properties.setActiveKeyVersion("v1");
        AdminAuthStore store = mock(AdminAuthStore.class);
        AdminAuthCrypto crypto = mock(AdminAuthCrypto.class);
        AdminSessionService sessions = mock(AdminSessionService.class);
        AdminRateLimiter limiter = mock(AdminRateLimiter.class);
        when(limiter.allow(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        AdminAuthPolicySource policy = new AdminAuthPolicySource() {
            @Override public String env() { return env; }
            @Override public String authMode() { return mode; }
        };
        AdminLocalAuthService service = new AdminLocalAuthService(
                properties, policy, store, crypto, sessions, limiter, new BCryptPasswordEncoder()
        );
        return new Fixture(service, store, crypto, sessions);
    }

    private record Fixture(
            AdminLocalAuthService service,
            AdminAuthStore store,
            AdminAuthCrypto crypto,
            AdminSessionService sessions
    ) {}
}
