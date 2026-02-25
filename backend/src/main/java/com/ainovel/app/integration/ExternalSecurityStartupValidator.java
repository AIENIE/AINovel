package com.ainovel.app.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Profile("!test")
public class ExternalSecurityStartupValidator implements ApplicationRunner {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ExternalServiceProperties properties;

    public ExternalSecurityStartupValidator(ExternalServiceProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        ExternalServiceProperties.Security security = properties.getSecurity();
        if (!security.isFailFast()) {
            return;
        }

        List<String> missing = new ArrayList<>();
        if (isUnset(security.getAi().getHmacCaller())) {
            missing.add("EXTERNAL_AI_HMAC_CALLER");
        }
        if (isUnset(security.getAi().getHmacSecret())) {
            missing.add("EXTERNAL_AI_HMAC_SECRET");
        } else if (security.getAi().getHmacSecret().trim().getBytes(StandardCharsets.UTF_8).length < 32) {
            missing.add("EXTERNAL_AI_HMAC_SECRET(min 32 bytes)");
        }
        if (isUnset(security.getUser().getInternalGrpcToken())) {
            missing.add("EXTERNAL_USER_INTERNAL_GRPC_TOKEN");
        }
        if (isUnset(security.getPay().getServiceJwt())) {
            missing.add("EXTERNAL_PAY_SERVICE_JWT");
        } else if (!looksLikeJwt(security.getPay().getServiceJwt())) {
            missing.add("EXTERNAL_PAY_SERVICE_JWT(invalid jwt)");
        } else if (!hasRequiredPayClaims(security.getPay().getServiceJwt(), security.getPay())) {
            missing.add("EXTERNAL_PAY_SERVICE_JWT(invalid claims)");
        }

        if (missing.isEmpty() && !properties.getGrpc().isTlsEnabled() && !properties.getGrpc().isPlaintextEnabled()) {
            missing.add("EXTERNAL_GRPC_TLS_ENABLED/EXTERNAL_GRPC_PLAINTEXT_ENABLED");
        }

        if (!missing.isEmpty()) {
            throw new IllegalStateException("Missing required external integration security configuration: " + String.join(", ", missing));
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isUnset(String value) {
        if (isBlank(value)) {
            return true;
        }
        String trimmed = value.trim();
        String upper = trimmed.toUpperCase();
        return upper.startsWith("REPLACE_ME")
                || upper.contains("REPLACE_WITH_YOUR_OWN")
                || upper.contains("REPLACE-WITH-YOUR-OWN")
                || upper.contains("CHANGE-ME")
                || upper.contains("CHANGE_ME");
    }

    private boolean looksLikeJwt(String raw) {
        String token = raw == null ? "" : raw.trim();
        if (token.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            token = token.substring("Bearer ".length()).trim();
        }
        if (token.isEmpty()) {
            return false;
        }
        String[] parts = token.split("\\.");
        return parts.length == 3;
    }

    private boolean hasRequiredPayClaims(String raw, ExternalServiceProperties.Pay pay) {
        String token = raw == null ? "" : raw.trim();
        if (token.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            token = token.substring("Bearer ".length()).trim();
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return false;
        }
        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(padBase64(parts[1]));
            JsonNode payload = OBJECT_MAPPER.readTree(new String(payloadBytes, StandardCharsets.UTF_8));

            if (!matchesIgnoreCase(payload.path("role").asText(""), pay.getRequiredRole())) {
                return false;
            }
            if (!matches(payload.path("iss").asText(""), pay.getJwtIssuer())) {
                return false;
            }
            if (!containsAudience(payload.get("aud"), pay.getJwtAudience())) {
                return false;
            }

            Set<String> actualScopes = parseScopes(payload.get("scopes"));
            for (String requiredScope : parseCsv(pay.getRequiredScopes())) {
                if (actualScopes.stream().noneMatch(s -> requiredScope.equalsIgnoreCase(s))) {
                    return false;
                }
            }

            if (!payload.hasNonNull("exp")) {
                return false;
            }
            long exp = payload.path("exp").asLong(0L);
            if (exp <= Instant.now().getEpochSecond()) {
                return false;
            }
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean containsAudience(JsonNode audienceNode, String expectedAudience) {
        if (expectedAudience == null || expectedAudience.isBlank()) {
            return true;
        }
        if (audienceNode == null || audienceNode.isNull()) {
            return false;
        }
        if (audienceNode.isTextual()) {
            return Arrays.stream(audienceNode.asText("").split("[,\\s]+"))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .anyMatch(expectedAudience::equals);
        }
        if (audienceNode.isArray()) {
            for (JsonNode item : audienceNode) {
                if (item != null && item.isTextual() && expectedAudience.equals(item.asText())) {
                    return true;
                }
            }
        }
        return false;
    }

    private Set<String> parseScopes(JsonNode scopesNode) {
        Set<String> scopes = new LinkedHashSet<>();
        if (scopesNode == null || scopesNode.isNull()) {
            return scopes;
        }
        if (scopesNode.isTextual()) {
            Arrays.stream(scopesNode.asText("").split("[,\\s]+"))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .forEach(scopes::add);
            return scopes;
        }
        if (scopesNode.isArray()) {
            for (JsonNode node : scopesNode) {
                if (node == null || !node.isTextual()) {
                    continue;
                }
                String value = node.asText("").trim();
                if (!value.isBlank()) {
                    scopes.add(value);
                }
            }
        }
        return scopes;
    }

    private List<String> parseCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }

    private boolean matchesIgnoreCase(String actual, String expected) {
        if (expected == null || expected.isBlank()) {
            return true;
        }
        return expected.equalsIgnoreCase(actual);
    }

    private boolean matches(String actual, String expected) {
        if (expected == null || expected.isBlank()) {
            return true;
        }
        return expected.equals(actual);
    }

    private String padBase64(String text) {
        int mod = text.length() % 4;
        if (mod == 0) {
            return text;
        }
        return text + "====".substring(mod);
    }
}
