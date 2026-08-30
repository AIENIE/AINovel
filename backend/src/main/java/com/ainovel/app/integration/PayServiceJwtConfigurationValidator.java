package com.ainovel.app.integration;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Shared fail-closed validation for AINovel's caller-scoped pay-service JWT. */
public final class PayServiceJwtConfigurationValidator {
    public static final String REQUIRED_CALLER_ID = "ainovel";
    public static final String REQUIRED_AUDIENCE = "aienie-payservice-grpc";
    public static final String REQUIRED_ROLE = "SERVICE";
    public static final List<String> REQUIRED_SCOPES = List.of(
            "billing.balance.read",
            "billing.balance.convert",
            "billing.grant.write",
            "billing.usage.deduct",
            "billing.redeem.write",
            "billing.ledger.read"
    );
    public static final long TTL_SECONDS = 300L;

    private PayServiceJwtConfigurationValidator() {
    }

    public static void validate(ExternalServiceProperties.Pay pay) {
        if (pay == null) {
            throw new IllegalArgumentException("PayService caller JWT configuration is required");
        }
        requireExact(pay.getCallerId(), REQUIRED_CALLER_ID, "caller id");
        requireExact(pay.getIssuer(), REQUIRED_CALLER_ID, "issuer");
        requireExact(pay.getServiceName(), REQUIRED_CALLER_ID, "service name");
        requireExact(pay.getAudience(), REQUIRED_AUDIENCE, "audience");
        requireExact(pay.getRole(), REQUIRED_ROLE, "role");
        if (pay.getTtlSeconds() != TTL_SECONDS) {
            throw new IllegalArgumentException("PayService caller JWT TTL is not canonical");
        }
        if (!Set.copyOf(REQUIRED_SCOPES).equals(normalizedScopes(pay.getScopes()))) {
            throw new IllegalArgumentException("PayService caller JWT scopes violate least privilege");
        }
        if (pay.getLegacyStaticToken() != null && !pay.getLegacyStaticToken().isBlank()) {
            throw new IllegalArgumentException("Legacy PayService static JWT is forbidden");
        }
        ExternalSigningSecretValidator.validate(pay.getSecret(), "PayService caller JWT");
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

    private static void requireExact(String actual, String expected, String label) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("PayService caller JWT " + label + " is not canonical");
        }
    }

}
