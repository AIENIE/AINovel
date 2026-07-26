package com.ainovel.app.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

@Service
public class JwtService {
    private final Key signingKey;
    private final long expirationMinutes;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        validateSecret(secret);
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    public String generateToken(String username, Map<String, Object> claims) {
        return generateToken(username, claims, Duration.ofMinutes(expirationMinutes));
    }

    public String generateToken(String username, Map<String, Object> claims, Duration lifetime) {
        Instant now = Instant.now();
        if (lifetime == null || lifetime.isNegative() || lifetime.isZero()) {
            throw new IllegalArgumentException("JWT lifetime must be positive");
        }
        return Jwts.builder()
                .setSubject(username)
                .addClaims(claims)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(lifetime)))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parserBuilder().setSigningKey(signingKey).build().parseClaimsJws(token).getBody();
    }

    private void validateSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.jwt.secret must not be blank");
        }
        String normalized = secret.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("REPLACE_ME")
                || normalized.contains("REPLACE_WITH_YOUR_OWN")
                || normalized.contains("REPLACE-WITH-YOUR-OWN")
                || normalized.contains("SUPER-SECRET-CHANGE-ME")
                || normalized.contains("CHANGE_ME")) {
            throw new IllegalStateException("app.jwt.secret must not use placeholder value");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.jwt.secret must be at least 32 bytes");
        }
    }
}
