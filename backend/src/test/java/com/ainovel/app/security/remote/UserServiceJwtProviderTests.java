package com.ainovel.app.security.remote;

import com.ainovel.app.integration.ExternalServiceProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserServiceJwtProviderTests {
    private static final String SECRET = "unit-test-user-service-jwt-secret-32-bytes";

    @Test
    void shouldIssueCanonicalHs256CallerTokenAndCacheUntilRefreshWindow() {
        ExternalServiceProperties properties = validProperties();
        MutableClock clock = new MutableClock(Instant.parse("2026-08-19T00:00:00Z"));
        AtomicInteger sequence = new AtomicInteger();
        UserServiceJwtProvider provider = new UserServiceJwtProvider(
                properties, clock, () -> "jti-" + sequence.incrementAndGet());

        String first = provider.currentToken();
        String cached = provider.currentToken();
        assertEquals(first, cached);
        assertEquals(1, sequence.get());

        Jws<Claims> parsed = parse(first, clock.instant());
        assertEquals(SignatureAlgorithm.HS256.getValue(), parsed.getHeader().getAlgorithm());
        assertEquals("ainovel", parsed.getBody().getIssuer());
        assertEquals("ainovel", parsed.getBody().getSubject());
        assertEquals("aienie-userservice-grpc", parsed.getBody().getAudience());
        assertEquals("jti-1", parsed.getBody().getId());
        assertEquals(List.of("user.auth.session.read"), parsed.getBody().get("scopes", List.class));
        assertEquals(parsed.getBody().getIssuedAt(), parsed.getBody().getNotBefore());
        assertEquals(300L, Duration.between(
                parsed.getBody().getIssuedAt().toInstant(),
                parsed.getBody().getExpiration().toInstant()).toSeconds());

        clock.advance(Duration.ofSeconds(271));
        String renewed = provider.currentToken();
        assertNotEquals(first, renewed);
        assertEquals("jti-2", parse(renewed, clock.instant()).getBody().getId());
    }

    @Test
    void shouldRejectWeakSecretWithoutDisclosingIt() {
        ExternalServiceProperties properties = validProperties();
        String weakSecret = "do-not-disclose";
        properties.getSecurity().getUser().setSecret(weakSecret);
        UserServiceJwtProvider provider = new UserServiceJwtProvider(properties);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, provider::currentToken);
        assertFalse(error.getMessage().contains(weakSecret));
    }

    @Test
    void shouldRejectExpandedScopesAndUnsafeTtl() {
        ExternalServiceProperties expandedScopeProperties = validProperties();
        expandedScopeProperties.getSecurity().getUser().setScopes("user.auth.session.read,user.directory.read");
        assertThrows(IllegalArgumentException.class,
                () -> new UserServiceJwtProvider(expandedScopeProperties).currentToken());

        ExternalServiceProperties invalidTtlProperties = validProperties();
        invalidTtlProperties.getSecurity().getUser().setTtlSeconds(301L);
        assertThrows(IllegalArgumentException.class,
                () -> new UserServiceJwtProvider(invalidTtlProperties).currentToken());
    }

    private ExternalServiceProperties validProperties() {
        ExternalServiceProperties properties = new ExternalServiceProperties();
        properties.getSecurity().getUser().setCallerId("ainovel");
        properties.getSecurity().getUser().setIssuer("ainovel");
        properties.getSecurity().getUser().setSecret(SECRET);
        properties.getSecurity().getUser().setAudience("aienie-userservice-grpc");
        properties.getSecurity().getUser().setTtlSeconds(300L);
        properties.getSecurity().getUser().setScopes("user.auth.session.read");
        return properties;
    }

    private Jws<Claims> parse(String token, Instant verificationInstant) {
        return Jwts.parserBuilder()
                .requireIssuer("ainovel")
                .requireSubject("ainovel")
                .requireAudience("aienie-userservice-grpc")
                .setClock(() -> Date.from(verificationInstant))
                .setSigningKey(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseClaimsJws(token);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("Only UTC is supported by this test clock");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
