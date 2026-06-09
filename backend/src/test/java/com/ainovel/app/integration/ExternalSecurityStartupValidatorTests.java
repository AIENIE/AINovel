package com.ainovel.app.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExternalSecurityStartupValidatorTests {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String PAY_SCOPES = "billing.balance.read billing.balance.convert billing.grant.write billing.usage.deduct billing.checkin.read billing.checkin.write billing.redeem.write billing.ledger.read";


    @Test
    void shouldFailFastWhenRequiredSecurityValuesMissing() {
        ExternalServiceProperties properties = new ExternalServiceProperties();
        properties.getSecurity().setFailFast(true);
        ExternalSecurityStartupValidator validator = new ExternalSecurityStartupValidator(properties);

        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldPassWhenAllRequiredSecurityValuesPresent() {
        ExternalServiceProperties properties = new ExternalServiceProperties();
        properties.getSecurity().setFailFast(true);
        properties.getSecurity().getAi().setHmacCaller("caller");
        properties.getSecurity().getAi().setHmacSecret("secret-32-bytes-minimum-for-testing");
        properties.getSecurity().getUser().setInternalGrpcToken("internal-token");
        properties.getSecurity().getPay().setServiceJwt(validPayJwt());

        ExternalSecurityStartupValidator validator = new ExternalSecurityStartupValidator(properties);
        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldFailWhenPlaceholderValuesUsed() {
        ExternalServiceProperties properties = new ExternalServiceProperties();
        properties.getSecurity().setFailFast(true);
        properties.getSecurity().getAi().setHmacCaller("REPLACE_ME_AI_CALLER");
        properties.getSecurity().getAi().setHmacSecret("REPLACE_ME_AI_HMAC_SECRET_MIN_32_BYTES");
        properties.getSecurity().getUser().setInternalGrpcToken("REPLACE_ME_USER_INTERNAL_GRPC_TOKEN");
        properties.getSecurity().getPay().setServiceJwt("REPLACE_ME_PAY_SERVICE_JWT");

        ExternalSecurityStartupValidator validator = new ExternalSecurityStartupValidator(properties);
        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldFailWhenPayJwtInvalidFormat() {
        ExternalServiceProperties properties = new ExternalServiceProperties();
        properties.getSecurity().setFailFast(true);
        properties.getSecurity().getAi().setHmacCaller("caller");
        properties.getSecurity().getAi().setHmacSecret("secret-32-bytes-minimum-for-testing");
        properties.getSecurity().getUser().setInternalGrpcToken("internal-token");
        properties.getSecurity().getPay().setServiceJwt("not-a-jwt");

        ExternalSecurityStartupValidator validator = new ExternalSecurityStartupValidator(properties);
        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldFailWhenPayJwtClaimsInvalid() {
        ExternalServiceProperties properties = new ExternalServiceProperties();
        properties.getSecurity().setFailFast(true);
        properties.getSecurity().getAi().setHmacCaller("caller");
        properties.getSecurity().getAi().setHmacSecret("secret-32-bytes-minimum-for-testing");
        properties.getSecurity().getUser().setInternalGrpcToken("internal-token");
        properties.getSecurity().getPay().setServiceJwt(jwtWithClaims(Map.of(
                "role", "internal_service",
                "iss", "aienie-services",
                "aud", "aienie-payservice-grpc",
                "scopes", PAY_SCOPES,
                "exp", Instant.now().plusSeconds(1800).getEpochSecond()
        )));

        ExternalSecurityStartupValidator validator = new ExternalSecurityStartupValidator(properties);
        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldFailWhenPayJwtMissingExpiration() {
        ExternalServiceProperties properties = new ExternalServiceProperties();
        properties.getSecurity().setFailFast(true);
        properties.getSecurity().getAi().setHmacCaller("caller");
        properties.getSecurity().getAi().setHmacSecret("secret-32-bytes-minimum-for-testing");
        properties.getSecurity().getUser().setInternalGrpcToken("internal-token");
        properties.getSecurity().getPay().setServiceJwt(jwtWithClaims(Map.of(
                "role", "SERVICE",
                "iss", "aienie-services",
                "aud", "aienie-payservice-grpc",
                "scopes", PAY_SCOPES
        )));

        ExternalSecurityStartupValidator validator = new ExternalSecurityStartupValidator(properties);
        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments(new String[0])));
    }

    private String validPayJwt() {
        return jwtWithClaims(Map.of(
                "role", "SERVICE",
                "iss", "aienie-services",
                "aud", "aienie-payservice-grpc",
                "scopes", PAY_SCOPES,
                "exp", Instant.now().plusSeconds(1800).getEpochSecond()
        ));
    }

    private String jwtWithClaims(Map<String, Object> claims) {
        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payloadJson;
        try {
            payloadJson = OBJECT_MAPPER.writeValueAsString(claims);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        String payload = base64Url(payloadJson);
        return header + "." + payload + ".signature";
    }

    private String base64Url(String raw) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
