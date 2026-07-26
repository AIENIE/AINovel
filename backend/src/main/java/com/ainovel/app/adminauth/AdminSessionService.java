package com.ainovel.app.adminauth;

import com.ainovel.app.security.JwtService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminSessionService {
    private final AdminAuthStore store;
    private final AdminLocalAuthProperties properties;
    private final JwtService jwtService;
    public AdminSessionService(
            AdminAuthStore store,
            AdminLocalAuthProperties properties,
            JwtService jwtService
    ) {
        this.store = store;
        this.properties = properties;
        this.jwtService = jwtService;
    }

    public Issued issue(String subject, String scope) {
        Instant now = Instant.now();
        Duration lifetime = Duration.ofMinutes(minutesFor(scope));
        Duration idleTimeout = Duration.ofMinutes(idleMinutesFor(scope));
        String sessionId = UUID.randomUUID().toString();
        Instant expires = now.plus(lifetime);
        store.insertSession(sessionId, subject, scope, now, expires, now.plus(idleTimeout));
        String token = jwtService.generateToken(
                subject,
                Map.of(
                        "role", "ADMIN",
                        "local_admin", true,
                        "admin_session", sessionId,
                        "admin_scope", scope,
                        "uid", 0L,
                        "sid", sessionId
                ),
                lifetime
        );
        return new Issued(token, sessionId, scope, expires);
    }

    public boolean isActive(String sessionId, String scope) {
        if (sessionId == null || sessionId.isBlank()) return false;
        return store.touchActiveSession(
                sessionId,
                scope,
                Instant.now().plus(Duration.ofMinutes(idleMinutesFor(scope)))
        );
    }

    private int minutesFor(String scope) {
        return Math.max(1, "RECOVERY".equals(scope)
                ? properties.getRecoverySessionMinutes()
                : properties.getSessionMinutes());
    }

    private int idleMinutesFor(String scope) {
        return Math.max(1, "RECOVERY".equals(scope)
                ? properties.getRecoverySessionIdleMinutes()
                : properties.getSessionIdleMinutes());
    }

    public void revoke(String id) { store.revokeSession(id); }
    public void revokeAll(String subject) { store.revokeAll(subject); }
    public record Issued(String token, String sessionId, String scope, Instant expiresAt) {}
}
