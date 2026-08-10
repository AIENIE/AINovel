package com.ainovel.app.adminauth;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
public class AdminSessionService {
    private final AdminAuthStore store;
    private final AdminLocalAuthProperties properties;
    private final AdminAuthPolicySource policy;
    private final SecureRandom random = new SecureRandom();

    public AdminSessionService(
            AdminAuthStore store,
            AdminLocalAuthProperties properties,
            AdminAuthPolicySource policy
    ) {
        this.store = store;
        this.properties = properties;
        this.policy = policy;
    }

    public Issued issue(
            String subject,
            String scope,
            String assurance,
            Instant passwordAuthenticatedAt,
            Instant totpAuthenticatedAt,
            String credentialKeyVersion
    ) {
        Instant now = Instant.now();
        Duration lifetime = Duration.ofMinutes(minutesFor(scope));
        Duration idleTimeout = Duration.ofMinutes(idleMinutesFor(scope));
        String token = newToken();
        String sessionHash = hash(token);
        Instant expires = now.plus(lifetime);
        store.insertSession(
                sessionHash,
                subject,
                scope,
                policy.env(),
                policy.authMode(),
                assurance,
                passwordAuthenticatedAt,
                totpAuthenticatedAt,
                credentialKeyVersion,
                passwordCredentialHash(),
                now,
                expires,
                now.plus(idleTimeout)
        );
        return new Issued(token, sessionHash, scope, assurance, expires);
    }

    public Resolved resolve(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String sessionHash = hash(token);
        Instant now = Instant.now();
        return store.touchActiveSession(
                        sessionHash,
                        policy.env(),
                        policy.authMode(),
                        passwordCredentialHash(),
                        now.plus(Duration.ofMinutes(Math.max(1, properties.getSessionIdleMinutes()))),
                        now.plus(Duration.ofMinutes(Math.max(1, properties.getRecoverySessionIdleMinutes())))
                )
                .map(session -> new Resolved(
                        session.sessionHash(),
                        session.subject(),
                        session.scope(),
                        session.assurance(),
                        session.passwordAuthenticatedAt(),
                        session.totpAuthenticatedAt(),
                        session.expiresAt()
                ))
                .orElse(null);
    }

    public boolean isActive(String sessionHash, String scope) {
        if (sessionHash == null || sessionHash.isBlank()) {
            return false;
        }
        return store.activeSessionWithScope(
                sessionHash, scope, policy.env(), policy.authMode(), passwordCredentialHash()
        );
    }

    public void revokeByHash(String sessionHash) {
        if (sessionHash != null && !sessionHash.isBlank()) {
            store.revokeSession(sessionHash);
        }
    }

    public void revokeAll(String subject) {
        store.revokeAll(subject);
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

    private String newToken() {
        byte[] value = new byte[32];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String passwordCredentialHash() {
        return hash(properties.getPasswordHash());
    }

    static String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash administrator session", ex);
        }
    }

    public record Issued(String token, String sessionHash, String scope, String assurance, Instant expiresAt) {}

    public record Resolved(
            String sessionHash,
            String subject,
            String scope,
            String assurance,
            Instant passwordAuthenticatedAt,
            Instant totpAuthenticatedAt,
            Instant expiresAt
    ) {}
}
