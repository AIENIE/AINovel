package com.ainovel.app.integration;

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
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PayServiceJwtProviderTests {
    private static final String SECRET = "unit-test-pay-service-jwt-secret-with-at-least-32-bytes";
    @Test
    void shouldIssueFreshCanonicalHs256CallerTokenForEveryAttempt() {
        ExternalServiceProperties properties = validProperties();
        MutableClock clock = new MutableClock(Instant.parse("2026-08-19T00:00:00Z"));
        AtomicInteger sequence = new AtomicInteger();
        PayServiceJwtProvider provider = new PayServiceJwtProvider(
                properties, clock, () -> "pay-jti-" + sequence.incrementAndGet());

        String first = provider.currentToken();
        String second = provider.currentToken();
        assertNotEquals(first, second);
        assertEquals(2, sequence.get());

        Jws<Claims> parsed = parse(first, clock.instant());
        assertEquals(SignatureAlgorithm.HS256.getValue(), parsed.getHeader().getAlgorithm());
        assertEquals("ainovel", parsed.getBody().getIssuer());
        assertEquals("ainovel", parsed.getBody().getSubject());
        assertEquals("aienie-payservice-grpc", parsed.getBody().getAudience());
        assertEquals("pay-jti-1", parsed.getBody().getId());
        assertEquals("SERVICE", parsed.getBody().get("role", String.class));
        assertEquals("ainovel", parsed.getBody().get("service", String.class));
        assertEquals(PayServiceJwtConfigurationValidator.REQUIRED_SCOPES,
                parsed.getBody().get("scopes", List.class));
        assertEquals(parsed.getBody().getIssuedAt(), parsed.getBody().getNotBefore());
        assertEquals(300L, Duration.between(
                parsed.getBody().getIssuedAt().toInstant(),
                parsed.getBody().getExpiration().toInstant()).toSeconds());

        Jws<Claims> parsedSecond = parse(second, clock.instant());
        assertEquals("pay-jti-2", parsedSecond.getBody().getId());
        assertEquals(parsed.getBody().getIssuedAt(), parsedSecond.getBody().getIssuedAt());
    }

    @Test
    void shouldIssueUniqueJtiForConcurrentAttempts() {
        ExternalServiceProperties properties = validProperties();
        MutableClock clock = new MutableClock(Instant.parse("2026-08-19T00:00:00Z"));
        AtomicInteger sequence = new AtomicInteger();
        PayServiceJwtProvider provider = new PayServiceJwtProvider(
                properties, clock, () -> "pay-jti-" + sequence.incrementAndGet());
        Set<String> tokens = ConcurrentHashMap.newKeySet();

        IntStream.range(0, 64).parallel().forEach(ignored -> tokens.add(provider.currentToken()));

        assertEquals(64, tokens.size());
        assertEquals(64, sequence.get());
        assertEquals(64, tokens.stream()
                .map(token -> parse(token, clock.instant()).getBody().getId())
                .distinct()
                .count());
    }

    @Test
    void shouldRejectWeakSecretWithoutDisclosingIt() {
        ExternalServiceProperties properties = validProperties();
        String weakSecret = "do-not-disclose";
        properties.getSecurity().getPay().setSecret(weakSecret);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new PayServiceJwtProvider(properties,
                        Clock.systemUTC(), () -> "pay-jti-weak").currentToken());
        assertFalse(error.getMessage().contains(weakSecret));
    }

    @Test
    void shouldRejectExpandedScopesUnsafeTtlAndLegacyStaticToken() {
        ExternalServiceProperties expanded = validProperties();
        expanded.getSecurity().getPay().setScopes(
                String.join(",", PayServiceJwtConfigurationValidator.REQUIRED_SCOPES) + ",billing.admin");
        assertThrows(IllegalArgumentException.class,
                () -> provider(expanded).currentToken());

        ExternalServiceProperties unsafeTtl = validProperties();
        unsafeTtl.getSecurity().getPay().setTtlSeconds(301L);
        assertThrows(IllegalArgumentException.class,
                () -> provider(unsafeTtl).currentToken());

        ExternalServiceProperties legacy = validProperties();
        legacy.getSecurity().getPay().setLegacyStaticToken("header.payload.signature");
        assertThrows(IllegalArgumentException.class,
                () -> provider(legacy).currentToken());

        for (String rejected : List.of(
                "a".repeat(32),
                "<protected-ainovel-pay-jwt-secret>",
                "${PAY_CALLER_SECRET}",
                "pay-caller-example-secret-0123456789-abcdefghijklmnopqrstuvwxyz",
                "pay-caller-secret-with-an embedded-space-0123456789")) {
            ExternalServiceProperties invalid = validProperties();
            invalid.getSecurity().getPay().setSecret(rejected);
            assertThrows(IllegalArgumentException.class,
                    () -> provider(invalid).currentToken());
        }

    }

    private ExternalServiceProperties validProperties() {
        ExternalServiceProperties properties = new ExternalServiceProperties();
        properties.getSecurity().getAi().setHmacCaller("ainovel");
        properties.getSecurity().getAi().setHmacSecret("unit-test-ai-hmac-secret-with-at-least-32-bytes");
        properties.getSecurity().getUser().setSecret("unit-test-user-service-jwt-secret-32-bytes");
        ExternalServiceProperties.Pay pay = properties.getSecurity().getPay();
        pay.setCallerId("ainovel");
        pay.setIssuer("ainovel");
        pay.setServiceName("ainovel");
        pay.setSecret(SECRET);
        pay.setAudience("aienie-payservice-grpc");
        pay.setRole("SERVICE");
        pay.setTtlSeconds(300L);
        pay.setScopes(String.join(",", PayServiceJwtConfigurationValidator.REQUIRED_SCOPES));
        return properties;
    }

    private PayServiceJwtProvider provider(ExternalServiceProperties properties) {
        return new PayServiceJwtProvider(
                properties, Clock.systemUTC(), () -> "pay-jti-test");
    }

    private Jws<Claims> parse(String token, Instant verificationInstant) {
        return Jwts.parserBuilder()
                .requireIssuer("ainovel")
                .requireSubject("ainovel")
                .requireAudience("aienie-payservice-grpc")
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
