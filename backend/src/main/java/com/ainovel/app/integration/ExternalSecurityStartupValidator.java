package com.ainovel.app.integration;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Validates only the external caller contracts required by the runtime. */
@Component
@Profile("!test")
public class ExternalSecurityStartupValidator implements ApplicationRunner {
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

        List<String> invalid = new ArrayList<>();
        if (isUnset(security.getAi().getHmacCaller())) {
            invalid.add("EXTERNAL_AI_HMAC_CALLER");
        }
        if (isUnset(security.getAi().getHmacSecret())
                || security.getAi().getHmacSecret().getBytes(StandardCharsets.UTF_8).length < 32) {
            invalid.add("EXTERNAL_AI_HMAC_SECRET");
        }
        try {
            ExternalServiceProperties.User user = security.getUser();
            UserServiceJwtConfigurationValidator.validate(
                    user.getCallerId(), user.getIssuer(), user.getSecret(), user.getAudience(),
                    user.getTtlSeconds(), user.getScopes());
        } catch (IllegalArgumentException ex) {
            invalid.add("EXTERNAL_USER_SERVICE_JWT_CONFIGURATION");
        }
        try {
            validatePayServiceBoundary(properties);
        } catch (IllegalArgumentException ex) {
            invalid.add("EXTERNAL_PAY_SERVICE_JWT_CONFIGURATION");
        }
        if (!properties.getGrpc().isTlsEnabled() && !properties.getGrpc().isPlaintextEnabled()) {
            invalid.add("EXTERNAL_GRPC_TLS_ENABLED/EXTERNAL_GRPC_PLAINTEXT_ENABLED");
        }
        if (!invalid.isEmpty()) {
            throw new IllegalStateException(
                    "Missing required external integration security configuration: " + String.join(", ", invalid));
        }
    }

    static void validatePayServiceBoundary(ExternalServiceProperties properties) {
        if (!"ainovel".equals(properties.getProjectKey())) {
            throw new IllegalArgumentException("External project key is not canonical");
        }
        PayServiceJwtConfigurationValidator.validate(properties.getSecurity().getPay());
    }

    private boolean isUnset(String value) {
        if (value == null || value.trim().isEmpty()) {
            return true;
        }
        String upper = value.trim().toUpperCase();
        return upper.startsWith("REPLACE_ME")
                || upper.contains("REPLACE_WITH_YOUR_OWN")
                || upper.contains("REPLACE-WITH-YOUR-OWN")
                || upper.contains("CHANGE-ME")
                || upper.contains("CHANGE_ME");
    }
}
