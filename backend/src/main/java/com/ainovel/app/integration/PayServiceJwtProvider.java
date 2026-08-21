package com.ainovel.app.integration;

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

/** Issues a fresh caller-scoped JWT for every pay-service RPC attempt. */
@Component
public class PayServiceJwtProvider {
    private final ExternalServiceProperties properties;
    private final Clock clock;
    private final Supplier<String> jtiSupplier;

    @Autowired
    public PayServiceJwtProvider(ExternalServiceProperties properties) {
        this(properties, Clock.systemUTC(), () -> UUID.randomUUID().toString());
    }

    PayServiceJwtProvider(ExternalServiceProperties properties,
                          Clock clock,
                          Supplier<String> jtiSupplier) {
        this.properties = Objects.requireNonNull(properties);
        this.clock = Objects.requireNonNull(clock);
        this.jtiSupplier = Objects.requireNonNull(jtiSupplier);
    }

    public String currentToken() {
        Configuration configuration = snapshotAndValidate();
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = now.plusSeconds(configuration.ttlSeconds);
        return Jwts.builder()
                .setIssuer(configuration.issuer)
                .setSubject(configuration.callerId)
                .setAudience(configuration.audience)
                .setIssuedAt(Date.from(now))
                .setNotBefore(Date.from(now))
                .setExpiration(Date.from(expiresAt))
                .setId(requireJti(jtiSupplier.get()))
                .claim("role", configuration.role)
                .claim("service", configuration.serviceName)
                .claim("scopes", PayServiceJwtConfigurationValidator.REQUIRED_SCOPES)
                .signWith(Keys.hmacShaKeyFor(configuration.secret.getBytes(StandardCharsets.UTF_8)),
                        SignatureAlgorithm.HS256)
                .compact();
    }

    private Configuration snapshotAndValidate() {
        ExternalServiceProperties.Pay pay = properties.getSecurity().getPay();
        ExternalSecurityStartupValidator.validatePayServiceBoundary(properties);
        return new Configuration(
                pay.getCallerId(), pay.getIssuer(), pay.getServiceName(), pay.getSecret(),
                pay.getAudience(), pay.getRole(), pay.getTtlSeconds());
    }

    private String requireJti(String value) {
        String jti = value == null ? "" : value.trim();
        if (jti.isEmpty() || jti.length() > 128) {
            throw new IllegalStateException("Unable to issue PayService caller JWT with a valid jti");
        }
        return jti;
    }

    private record Configuration(String callerId,
                                 String issuer,
                                 String serviceName,
                                 String secret,
                                 String audience,
                                 String role,
                                 long ttlSeconds) {
    }

}
