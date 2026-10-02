package com.ainovel.app.integration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ExternalServiceApplicationYamlTests {

    @Test
    void shouldUseCanonicalExternalSecurityPlaceholders() throws IOException {
        String yaml = applicationYaml();

        assertTrue(yaml.contains("hmac-caller: "));
        assertTrue(yaml.contains("hmac-secret: ${GRPC_SHARED_SECRET}"));
        assertTrue(yaml.contains("caller-id: ainovel"));
        assertTrue(yaml.contains("issuer: ainovel"));
        assertTrue(yaml.contains("secret: ${GRPC_SHARED_SECRET}"));
        assertTrue(yaml.contains("audience: aienie-userservice-grpc"));
        assertTrue(yaml.contains("ttl-seconds: 300"));
        assertTrue(yaml.contains("scopes: user.auth.session.read"));
        assertTrue(yaml.contains("caller-id: ainovel"));
        assertTrue(yaml.contains("issuer: ainovel"));
        assertTrue(yaml.contains("service-name: ainovel"));
        assertTrue(yaml.contains("secret: ${GRPC_SHARED_SECRET}"));
        assertTrue(yaml.contains("audience: aienie-payservice-grpc"));
        assertTrue(yaml.contains("role: SERVICE"));
        assertTrue(yaml.contains("ttl-seconds: 300"));
        assertTrue(yaml.contains("scopes: billing.balance.read,billing.balance.convert,billing.grant.write,billing.usage.deduct,billing.redeem.write,billing.ledger.read"));
        assertFalse(yaml.contains("legacy-static-token:"));
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

        assertTrue(yaml.contains("enabled: true"));
        assertTrue(yaml.contains("locations: classpath:db/migration"));
        assertTrue(yaml.contains("clean-disabled: true"));
        assertTrue(yaml.contains("baseline-on-migrate: false"));
        assertTrue(yaml.contains("validate-on-migrate: true"));
        assertTrue(yaml.contains("project-key: ainovel"));
    }

    @Test
    void shouldKeepOptionalRedisOutOfDeploymentHealthGroups() throws IOException {
        String yaml = applicationYaml();

        assertEquals("true", property("management.endpoint.health.probes.enabled"));
        assertEquals("livenessState", property("management.endpoint.health.group.liveness.include"));
        assertEquals("readinessState,db,userService", property("management.endpoint.health.group.readiness.include"));
        assertTrue(property("management.endpoints.web.exposure.include").contains("health"));
    }

    private String property(String name) throws IOException {
        var sources = new org.springframework.boot.env.YamlPropertySourceLoader().load("application", new org.springframework.core.io.FileSystemResource("src/main/resources/application.yml"));
        return String.valueOf(sources.getFirst().getProperty(name));
    }
    private String applicationYaml() throws IOException {
        return Files.readString(Path.of("src/main/resources/application.yml"), StandardCharsets.UTF_8);
    }
}
