package com.ainovel.app.integration;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Shared fail-closed validation for the caller-scoped UserService JWT contract.
 */
public final class UserServiceJwtConfigurationValidator {
    public static final String REQUIRED_CALLER_ID = "ainovel";
    public static final String REQUIRED_AUDIENCE = "aienie-userservice-grpc";
    public static final String REQUIRED_SCOPE = "user.auth.session.read";
    public static final long MIN_TTL_SECONDS = 1L;
    public static final long MAX_TTL_SECONDS = 300L;

    private static final Pattern CALLER_NAME = Pattern.compile("[a-z0-9][a-z0-9._-]{1,63}");

    private UserServiceJwtConfigurationValidator() {
    }

    public static void validate(String callerId,
                                String issuer,
                                String secret,
                                String audience,
                                long ttlSeconds,
                                String scopes) {
        requireExactCaller(callerId, "caller id");
        requireExactCaller(issuer, "issuer");
        ExternalSigningSecretValidator.validate(secret, "UserService caller JWT");
        if (!REQUIRED_AUDIENCE.equals(audience)) {
            throw new IllegalArgumentException("UserService caller JWT audience is not canonical");
        }
        if (ttlSeconds < MIN_TTL_SECONDS || ttlSeconds > MAX_TTL_SECONDS) {
            throw new IllegalArgumentException("UserService caller JWT TTL is outside the safe bound");
        }
        if (!Set.of(REQUIRED_SCOPE).equals(normalizedScopes(scopes))) {
            throw new IllegalArgumentException("UserService caller JWT scopes violate least privilege");
        }
    }

    public static Set<String> normalizedScopes(String raw) {
        Set<String> result = new LinkedHashSet<>();
        if (raw == null) {
            return Set.of();
        }
        Arrays.stream(raw.split("[,\\s]+"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .forEach(result::add);
        return Set.copyOf(result);
    }

    private static void requireExactCaller(String value, String label) {
        if (isPlaceholder(value)
                || value == null
                || !CALLER_NAME.matcher(value).matches()
                || !REQUIRED_CALLER_ID.equals(value)) {
            throw new IllegalArgumentException("UserService caller JWT " + label + " is not canonical");
        }
    }

    private static boolean isPlaceholder(String value) {
        if (value == null) {
            return true;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.isEmpty()
                || normalized.startsWith("REPLACE_ME")
                || normalized.contains("REPLACE-WITH")
                || normalized.contains("REPLACE_WITH")
                || normalized.contains("CHANGE-ME")
                || normalized.contains("CHANGE_ME");
    }
}
