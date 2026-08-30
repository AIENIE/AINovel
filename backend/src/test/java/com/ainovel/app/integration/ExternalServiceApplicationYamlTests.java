package com.ainovel.app.integration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalServiceApplicationYamlTests {

    @Test
    void shouldUseCanonicalExternalSecurityPlaceholders() throws IOException {
        String yaml = applicationYaml();

        assertTrue(yaml.contains("hmac-caller: ${EXTERNAL_AI_HMAC_CALLER:}"));
        assertTrue(yaml.contains("hmac-secret: ${EXTERNAL_AI_HMAC_SECRET:}"));
        assertTrue(yaml.contains("caller-id: ${EXTERNAL_USER_SERVICE_JWT_CALLER_ID:ainovel}"));
        assertTrue(yaml.contains("issuer: ${EXTERNAL_USER_SERVICE_JWT_ISSUER:ainovel}"));
        assertTrue(yaml.contains("secret: ${EXTERNAL_USER_SERVICE_JWT_SECRET:}"));
        assertTrue(yaml.contains("audience: ${EXTERNAL_USER_SERVICE_JWT_AUDIENCE:aienie-userservice-grpc}"));
        assertTrue(yaml.contains("ttl-seconds: ${EXTERNAL_USER_SERVICE_JWT_TTL_SECONDS:300}"));
        assertTrue(yaml.contains("scopes: ${EXTERNAL_USER_SERVICE_JWT_SCOPES:user.auth.session.read}"));
        assertTrue(yaml.contains("caller-id: ${EXTERNAL_PAY_SERVICE_JWT_CALLER_ID:ainovel}"));
        assertTrue(yaml.contains("issuer: ${EXTERNAL_PAY_SERVICE_JWT_ISSUER:ainovel}"));
        assertTrue(yaml.contains("service-name: ${EXTERNAL_PAY_SERVICE_JWT_SERVICE_NAME:ainovel}"));
        assertTrue(yaml.contains("secret: ${EXTERNAL_PAY_SERVICE_JWT_SECRET:}"));
        assertTrue(yaml.contains("audience: ${EXTERNAL_PAY_SERVICE_JWT_AUDIENCE:aienie-payservice-grpc}"));
        assertTrue(yaml.contains("role: ${EXTERNAL_PAY_SERVICE_JWT_ROLE:SERVICE}"));
        assertTrue(yaml.contains("ttl-seconds: ${EXTERNAL_PAY_SERVICE_JWT_TTL_SECONDS:300}"));
        assertTrue(yaml.contains("scopes: ${EXTERNAL_PAY_SERVICE_JWT_SCOPES:billing.balance.read,billing.balance.convert,billing.grant.write,billing.usage.deduct,billing.redeem.write,billing.ledger.read}"));
        assertTrue(yaml.contains("legacy-static-token: ${EXTERNAL_PAY_SERVICE_JWT:}"));
    }

    @Test
    void shouldNotReferenceDeprecatedAppExternalFallbackKeys() throws IOException {
        String yaml = applicationYaml();

        assertFalse(yaml.contains("APP_EXTERNAL_AI_HMAC_CALLER"));
        assertFalse(yaml.contains("APP_EXTERNAL_AI_HMAC_SECRET"));
        assertFalse(yaml.contains("APP_EXTERNAL_USER_INTERNAL_TOKEN"));
        assertFalse(yaml.contains("EXTERNAL_USER_INTERNAL_GRPC_TOKEN"));
        assertFalse(yaml.contains("APP_EXTERNAL_PAY_SERVICE_JWT"));
    }

    @Test
    void shouldKeepFlywayGovernanceEnabledByDefault() throws IOException {
        String yaml = applicationYaml();

        assertTrue(yaml.contains("enabled: ${SPRING_FLYWAY_ENABLED:true}"));
        assertTrue(yaml.contains("locations: classpath:db/migration"));
        assertTrue(yaml.contains("clean-disabled: true"));
        assertTrue(yaml.contains("baseline-on-migrate: false"));
        assertTrue(yaml.contains("validate-on-migrate: true"));
        assertTrue(yaml.contains("project-key: ${EXTERNAL_PROJECT_KEY:ainovel}"));
    }

    @Test
    void shouldKeepOptionalRedisOutOfDeploymentHealthGroups() throws IOException {
        String yaml = applicationYaml();

        assertTrue(yaml.contains("probes:\n        enabled: true"));
        assertTrue(yaml.contains("liveness:\n          include: livenessState"));
        assertTrue(yaml.contains("readiness:\n          include: readinessState,db,userService"));
        assertTrue(yaml.contains("exposure:\n        include: health"));
    }

    private String applicationYaml() throws IOException {
        return Files.readString(Path.of("src/main/resources/application.yml"), StandardCharsets.UTF_8);
    }
}
