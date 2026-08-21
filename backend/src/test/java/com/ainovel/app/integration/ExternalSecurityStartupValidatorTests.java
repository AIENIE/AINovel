package com.ainovel.app.integration;

import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExternalSecurityStartupValidatorTests {
    @Test
    void shouldFailFastWhenRequiredSecurityValuesMissing() {
        ExternalServiceProperties properties = new ExternalServiceProperties();

        assertThrows(IllegalStateException.class, () -> validate(properties));
    }

    @Test
    void shouldPassWhenAllCallerCredentialsAreCanonical() {
        ExternalServiceProperties properties = validProperties();

        assertDoesNotThrow(() -> validate(properties));
    }

    @Test
    void shouldRejectInvalidPayServiceCallerJwtConfiguration() {
        for (Consumer<ExternalServiceProperties.Pay> mutation : java.util.List.<Consumer<ExternalServiceProperties.Pay>>of(
                pay -> pay.setCallerId("other"),
                pay -> pay.setIssuer("aienie-services"),
                pay -> pay.setServiceName("other"),
                pay -> pay.setSecret("short"),
                pay -> pay.setSecret("REPLACE_ME_PAY_SERVICE_JWT_SECRET"),
                pay -> pay.setAudience("wrong-audience"),
                pay -> pay.setRole("ADMIN"),
                pay -> pay.setTtlSeconds(29L),
                pay -> pay.setTtlSeconds(301L),
                pay -> pay.setSecret("a".repeat(32)),
                pay -> pay.setSecret("<protected-ainovel-pay-jwt-secret>"),
                pay -> pay.setSecret("${PAY_CALLER_SECRET}"),
                pay -> pay.setScopes("billing.balance.read"),
                pay -> pay.setLegacyStaticToken("header.payload.signature")
        )) {
            ExternalServiceProperties properties = validProperties();
            mutation.accept(properties.getSecurity().getPay());

            assertThrows(IllegalStateException.class, () -> validate(properties));
        }
    }

    @Test
    void shouldRejectPlaceholderAiAndUserCredentials() {
        ExternalServiceProperties properties = validProperties();
        properties.getSecurity().getAi().setHmacCaller("REPLACE_ME_AI_CALLER");
        properties.getSecurity().getUser().setSecret("REPLACE_ME_USER_SERVICE_JWT_SECRET");

        assertThrows(IllegalStateException.class, () -> validate(properties));
    }

    @Test
    void shouldRejectDisabledTransportEvenWhenAllSecretsAreValid() {
        ExternalServiceProperties disabled = validProperties();
        disabled.getGrpc().setTlsEnabled(false);
        disabled.getGrpc().setPlaintextEnabled(false);
        assertThrows(IllegalStateException.class, () -> validate(disabled));
    }

    @Test
    void shouldRejectProjectKeyDrift() {
        ExternalServiceProperties wrongProject = validProperties();
        wrongProject.setProjectKey("other");
        assertThrows(IllegalStateException.class, () -> validate(wrongProject));
    }

    private void validate(ExternalServiceProperties properties) {
        new ExternalSecurityStartupValidator(properties).run(null);
    }

    private ExternalServiceProperties validProperties() {
        ExternalServiceProperties properties = new ExternalServiceProperties();
        properties.getSecurity().getAi().setHmacCaller("ai-novel");
        properties.getSecurity().getAi().setHmacSecret("unit-test-ai-hmac-secret-with-at-least-32-bytes");

        properties.getSecurity().getUser().setCallerId("ainovel");
        properties.getSecurity().getUser().setIssuer("ainovel");
        properties.getSecurity().getUser().setSecret("unit-test-user-service-jwt-secret-32-bytes");
        properties.getSecurity().getUser().setAudience("aienie-userservice-grpc");
        properties.getSecurity().getUser().setTtlSeconds(300L);
        properties.getSecurity().getUser().setScopes("user.auth.session.read");

        ExternalServiceProperties.Pay pay = properties.getSecurity().getPay();
        pay.setCallerId("ainovel");
        pay.setIssuer("ainovel");
        pay.setServiceName("ainovel");
        pay.setSecret("unit-test-pay-service-jwt-secret-with-at-least-32-bytes");
        pay.setAudience("aienie-payservice-grpc");
        pay.setRole("SERVICE");
        pay.setTtlSeconds(300L);
        pay.setScopes(String.join(",", PayServiceJwtConfigurationValidator.REQUIRED_SCOPES));
        pay.setLegacyStaticToken("");
        return properties;
    }
}
