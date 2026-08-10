package com.ainovel.app.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {
    private static final String TEST_KEY = "test-only-signing-key-material-longer-than-thirty-two-bytes";

    @Test
    void generatedTokenRequiresConfiguredIssuerAndAudience() {
        JwtService service = new JwtService(TEST_KEY, 120, "ainovel", "ainovel-web");

        String token = service.generateToken("signed-user", Map.of(
                "uid", 18L,
                "sid", "session-001",
                "role", "USER"
        ), Duration.ofMinutes(5));
        Claims claims = service.parseClaims(token);

        assertEquals("signed-user", claims.getSubject());
        assertEquals("ainovel", claims.getIssuer());
        assertEquals("ainovel-web", claims.getAudience());
        assertThrows(JwtException.class,
                () -> new JwtService(TEST_KEY, 120, "another-issuer", "ainovel-web").parseClaims(token));
        assertThrows(JwtException.class,
                () -> new JwtService(TEST_KEY, 120, "ainovel", "another-audience").parseClaims(token));
    }
}
