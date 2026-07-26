package com.ainovel.app.adminauth;

import com.ainovel.app.security.JwtService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminSessionServiceTest {
    @Test
    void issuesRecoverySessionWithItsOwnLifetimeAndIdleTimeout() {
        AdminAuthStore store = mock(AdminAuthStore.class);
        JwtService jwtService = mock(JwtService.class);
        when(jwtService.generateToken(eq("configured-admin"), any(Map.class), any(Duration.class))).thenReturn("token");

        AdminLocalAuthProperties properties = new AdminLocalAuthProperties();
        properties.setRecoverySessionMinutes(15);
        properties.setRecoverySessionIdleMinutes(10);
        AdminSessionService service = new AdminSessionService(store, properties, jwtService);

        AdminSessionService.Issued issued = service.issue("configured-admin", "RECOVERY");

        assertTrue(issued.expiresAt().isAfter(Instant.now().plus(Duration.ofMinutes(14))));
        verify(store).insertSession(any(), eq("configured-admin"), eq("RECOVERY"), any(), any(), any());
        verify(jwtService).generateToken(eq("configured-admin"), any(Map.class), eq(Duration.ofMinutes(15)));
    }
}
