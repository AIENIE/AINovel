package com.ainovel.app.security.remote;

import com.ainovel.app.integration.ExternalServiceProperties;
import com.ainovel.app.integration.UserServiceJwtConfigurationValidator;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Issues short-lived, caller-scoped JWTs for protected UserService gRPC calls.
 */
@Component
public class UserServiceJwtProvider {
    private final ExternalServiceProperties externalServiceProperties;
    private final Clock clock;
    private final Supplier<String> jtiSupplier;
    private final Object monitor = new Object();
    private volatile CachedToken cachedToken;

    @Autowired
    public UserServiceJwtProvider(ExternalServiceProperties externalServiceProperties) {
        this(externalServiceProperties, Clock.systemUTC(), () -> UUID.randomUUID().toString());
    }

    UserServiceJwtProvider(ExternalServiceProperties externalServiceProperties,
                           Clock clock,
                           Supplier<String> jtiSupplier) {
        this.externalServiceProperties = Objects.requireNonNull(externalServiceProperties);
        this.clock = Objects.requireNonNull(clock);
        this.jtiSupplier = Objects.requireNonNull(jtiSupplier);
    }

    public String currentToken() {
        Configuration configuration = snapshotAndValidate();
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        long refreshWindowSeconds = Math.min(30L, Math.max(5L, configuration.ttlSeconds / 5L));
        CachedToken observed = cachedToken;
        if (observed != null && observed.matches(configuration)
                && observed.expiresAt.isAfter(now.plusSeconds(refreshWindowSeconds))) {
            return observed.value;
        }

        synchronized (monitor) {
            observed = cachedToken;
            if (observed != null && observed.matches(configuration)
                    && observed.expiresAt.isAfter(now.plusSeconds(refreshWindowSeconds))) {
                return observed.value;
            }
            Instant expiresAt = now.plusSeconds(configuration.ttlSeconds);
            String token = Jwts.builder()
                    .setIssuer(configuration.issuer)
                    .setSubject(configuration.callerId)
                    .setAudience(configuration.audience)
                    .setIssuedAt(Date.from(now))
                    .setNotBefore(Date.from(now))
                    .setExpiration(Date.from(expiresAt))
                    .setId(requireJti(jtiSupplier.get()))
                    .claim("scopes", List.of(UserServiceJwtConfigurationValidator.REQUIRED_SCOPE))
                    .signWith(
                            Keys.hmacShaKeyFor(configuration.secret.getBytes(StandardCharsets.UTF_8)),
                            SignatureAlgorithm.HS256
                    )
                    .compact();
            cachedToken = new CachedToken(configuration, token, expiresAt);
            return token;
        }
    }

    private Configuration snapshotAndValidate() {
        ExternalServiceProperties.User source = externalServiceProperties.getSecurity().getUser();
        String callerId = trim(source.getCallerId());
        String issuer = trim(source.getIssuer());
        String secret = source.getSecret() == null ? "" : source.getSecret();
        String audience = trim(source.getAudience());
        long ttlSeconds = source.getTtlSeconds();
        String scopes = trim(source.getScopes());
        UserServiceJwtConfigurationValidator.validate(
                callerId, issuer, secret, audience, ttlSeconds, scopes);
        return new Configuration(callerId, issuer, secret, audience, ttlSeconds);
    }

    private String requireJti(String value) {
        String jti = trim(value);
        if (jti.isEmpty() || jti.length() > 128) {
            throw new IllegalStateException("Unable to issue UserService caller JWT with a valid jti");
        }
        return jti;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class Configuration {
        private final String callerId;
        private final String issuer;
        private final String secret;
        private final String audience;
        private final long ttlSeconds;

        private Configuration(String callerId, String issuer, String secret, String audience, long ttlSeconds) {
            this.callerId = callerId;
            this.issuer = issuer;
            this.secret = secret;
            this.audience = audience;
            this.ttlSeconds = ttlSeconds;
        }

        private boolean sameAs(Configuration other) {
            return other != null
                    && ttlSeconds == other.ttlSeconds
                    && callerId.equals(other.callerId)
                    && issuer.equals(other.issuer)
                    && secret.equals(other.secret)
                    && audience.equals(other.audience);
        }
    }

    private static final class CachedToken {
        private final Configuration configuration;
        private final String value;
        private final Instant expiresAt;

        private CachedToken(Configuration configuration, String value, Instant expiresAt) {
            this.configuration = configuration;
            this.value = value;
            this.expiresAt = expiresAt;
        }

        private boolean matches(Configuration candidate) {
            return configuration.sameAs(candidate);
        }
    }
}
