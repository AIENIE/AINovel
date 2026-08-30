package com.ainovel.app.config;

import com.ainovel.app.adminauth.AdminAuthPolicyRules;
import com.ainovel.app.integration.ExternalServiceProperties;
import com.ainovel.app.integration.PayServiceJwtConfigurationValidator;
import com.ainovel.app.integration.UserServiceJwtConfigurationValidator;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Fail-fast validation for the deployment environment before Spring creates
 * any beans or opens external connections.
 */
public final class RuntimeEnvironmentPreflight {
    private static final List<String> REQUIRED = List.of(
            "ENV", "AUTH_MODE", "ADMIN_USERNAME", "ADMIN_PASSWORD_HASH",
            "JWT_SECRET", "JWT_ISSUER", "JWT_AUDIENCE",
            "MYSQL_PASSWORD", "SPRING_JPA_HIBERNATE_DDL_AUTO",
            "ADMIN_TRUSTED_ORIGINS", "ADMIN_SESSION_COOKIE_SECURE",
            "EXTERNAL_AI_HMAC_CALLER", "EXTERNAL_AI_HMAC_SECRET",
            "EXTERNAL_USER_SERVICE_JWT_CALLER_ID", "EXTERNAL_USER_SERVICE_JWT_ISSUER",
            "EXTERNAL_USER_SERVICE_JWT_SECRET", "EXTERNAL_USER_SERVICE_JWT_AUDIENCE",
            "EXTERNAL_USER_SERVICE_JWT_TTL_SECONDS", "EXTERNAL_USER_SERVICE_JWT_SCOPES",
            "EXTERNAL_PAY_SERVICE_JWT_CALLER_ID", "EXTERNAL_PAY_SERVICE_JWT_ISSUER",
            "EXTERNAL_PAY_SERVICE_JWT_SERVICE_NAME", "EXTERNAL_PAY_SERVICE_JWT_SECRET",
            "EXTERNAL_PAY_SERVICE_JWT_AUDIENCE", "EXTERNAL_PAY_SERVICE_JWT_ROLE",
            "EXTERNAL_PAY_SERVICE_JWT_TTL_SECONDS", "EXTERNAL_PAY_SERVICE_JWT_SCOPES"
    );
    private static final List<String> TOTP_REQUIRED = List.of(
            "ADMIN_TOTP_ENCRYPTION_KEYS", "ADMIN_TOTP_ACTIVE_KEY_VERSION"
    );
    private static final List<String> NON_LOCAL_DATA_SECURITY_REQUIRED = List.of(
            "DB_URL", "DB_USERNAME", "REDIS_HOST", "REDIS_PORT", "REDIS_SSL_ENABLED",
            "QDRANT_HOST", "QDRANT_PORT"
    );
    private static final List<String> PRODUCTION_DATA_CREDENTIALS_REQUIRED = List.of(
            "REDIS_USERNAME", "REDIS_PASSWORD", "QDRANT_API_KEY"
    );
    private static final String STAGING_DATA_HOST = "base.testhut.top";
    private static final String TEMPLATE_PLACEHOLDER_RESOURCE = "env-template-placeholders.properties";
    private static final Map<String, String> TEMPLATE_PLACEHOLDERS = loadTemplatePlaceholders();

    private RuntimeEnvironmentPreflight() {
    }

    public static void validate(Map<String, String> environment) {
        String env = exact(environment.get("ENV"), "ENV", "local", "test", "production");
        String authMode = exact(environment.get("AUTH_MODE"), "AUTH_MODE", "password", "totp");
        AdminAuthPolicyRules.requireAllowed(env, authMode);

        List<String> missing = new ArrayList<>();
        require(environment, REQUIRED, missing);
        if ("totp".equals(authMode)) {
            require(environment, TOTP_REQUIRED, missing);
        }
        if (!"local".equals(env)) {
            require(environment, NON_LOCAL_DATA_SECURITY_REQUIRED, missing);
        }
        if ("production".equals(env)) {
            require(environment, PRODUCTION_DATA_CREDENTIALS_REQUIRED, missing);
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Missing required runtime environment variables: "
                    + String.join(", ", missing));
        }

        List<String> placeholders = TEMPLATE_PLACEHOLDERS.entrySet().stream()
                .filter(entry -> entry.getValue().equals(environment.get(entry.getKey())))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        if (!placeholders.isEmpty()) {
            throw new IllegalStateException("Runtime environment contains template placeholder values for: "
                    + String.join(", ", placeholders));
        }

        if (hasText(environment.get("EXTERNAL_USER_INTERNAL_GRPC_TOKEN"))) {
            throw new IllegalStateException("Legacy UserService static gRPC token is forbidden");
        }
        validateUserServiceJwtConfiguration(environment);
        validatePayServiceJwtConfiguration(environment);

        if ("local".equals(env)) {
            System.getLogger(RuntimeEnvironmentPreflight.class.getName()).log(
                    System.Logger.Level.WARNING,
                    "Local profile permits unrestricted plaintext data services for development only; test allows only the rebaselined canonical endpoints"
            );
            return;
        }

        String dbUsername = environment.get("DB_USERNAME");
        if ("root".equalsIgnoreCase(dbUsername) || "ainovel".equalsIgnoreCase(dbUsername)) {
            throw new IllegalStateException("DB_USERNAME must be a non-template least-privilege account outside local");
        }
        if ("test".equals(env)) {
            validateStagingDataServices(environment);
            return;
        }

        String dbUrl = environment.get("DB_URL");
        if (!hasMysqlEndpoint(dbUrl, null, -1)
                || !hasOnlyQueryValue(dbUrl, "sslMode", "VERIFY_IDENTITY")
                || !hasOnlyQueryValue(dbUrl, "allowPublicKeyRetrieval", "false")) {
            throw new IllegalStateException(
                    "production DB_URL must use MySQL sslMode=VERIFY_IDENTITY and disable allowPublicKeyRetrieval");
        }
        if (!"true".equals(environment.get("REDIS_SSL_ENABLED"))) {
            throw new IllegalStateException("production REDIS_SSL_ENABLED must be exactly true");
        }
        String qdrantHost = environment.get("QDRANT_HOST");
        if (qdrantHost == null || !qdrantHost.toLowerCase(java.util.Locale.ROOT).startsWith("https://")) {
            throw new IllegalStateException("production QDRANT_HOST must use https");
        }
    }

    private static void validateStagingDataServices(Map<String, String> environment) {
        String dbUrl = environment.get("DB_URL");
        if (!hasMysqlEndpoint(dbUrl, STAGING_DATA_HOST, 13306)
                || !hasOnlyQueryValue(dbUrl, "sslMode", "DISABLED")
                || !hasOnlyQueryValue(dbUrl, "allowPublicKeyRetrieval", "false")) {
            throw new IllegalStateException(
                    "test DB_URL must use base.testhut.top:13306, sslMode=DISABLED, and allowPublicKeyRetrieval=false");
        }
        if (!STAGING_DATA_HOST.equalsIgnoreCase(environment.get("REDIS_HOST"))
                || !"16379".equals(environment.get("REDIS_PORT"))
                || !"false".equals(environment.get("REDIS_SSL_ENABLED"))) {
            throw new IllegalStateException(
                    "test Redis must use plaintext base.testhut.top:16379 exactly as rebaselined");
        }
        if (!("http://" + STAGING_DATA_HOST).equalsIgnoreCase(environment.get("QDRANT_HOST"))
                || !"16333".equals(environment.get("QDRANT_PORT"))) {
            throw new IllegalStateException(
                    "test Qdrant must use http://base.testhut.top:16333 exactly as rebaselined");
        }
        if (hasText(environment.get("REDIS_USERNAME")) || hasText(environment.get("REDIS_PASSWORD"))
                || hasText(environment.get("QDRANT_API_KEY"))) {
            throw new IllegalStateException(
                    "test Redis and Qdrant credentials must stay empty because the rebaselined listeners are unauthenticated");
        }
    }

    private static void validateUserServiceJwtConfiguration(Map<String, String> environment) {
        long ttlSeconds;
        try {
            ttlSeconds = Long.parseLong(environment.get("EXTERNAL_USER_SERVICE_JWT_TTL_SECONDS"));
        } catch (RuntimeException ex) {
            throw new IllegalStateException("EXTERNAL_USER_SERVICE_JWT_TTL_SECONDS must be an integer");
        }
        try {
            UserServiceJwtConfigurationValidator.validate(
                    environment.get("EXTERNAL_USER_SERVICE_JWT_CALLER_ID"),
                    environment.get("EXTERNAL_USER_SERVICE_JWT_ISSUER"),
                    environment.get("EXTERNAL_USER_SERVICE_JWT_SECRET"),
                    environment.get("EXTERNAL_USER_SERVICE_JWT_AUDIENCE"),
                    ttlSeconds,
                    environment.get("EXTERNAL_USER_SERVICE_JWT_SCOPES")
            );
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid UserService caller JWT configuration", ex);
        }
    }

    private static void validatePayServiceJwtConfiguration(Map<String, String> environment) {
        if (hasText(environment.get("EXTERNAL_PAY_SERVICE_JWT"))) {
            throw new IllegalStateException("Legacy PayService static JWT is forbidden");
        }
        long ttlSeconds;
        try {
            ttlSeconds = Long.parseLong(environment.get("EXTERNAL_PAY_SERVICE_JWT_TTL_SECONDS"));
        } catch (RuntimeException ex) {
            throw new IllegalStateException("EXTERNAL_PAY_SERVICE_JWT_TTL_SECONDS must be an integer");
        }
        ExternalServiceProperties.Pay pay = new ExternalServiceProperties.Pay();
        pay.setCallerId(environment.get("EXTERNAL_PAY_SERVICE_JWT_CALLER_ID"));
        pay.setIssuer(environment.get("EXTERNAL_PAY_SERVICE_JWT_ISSUER"));
        pay.setServiceName(environment.get("EXTERNAL_PAY_SERVICE_JWT_SERVICE_NAME"));
        pay.setSecret(environment.get("EXTERNAL_PAY_SERVICE_JWT_SECRET"));
        pay.setAudience(environment.get("EXTERNAL_PAY_SERVICE_JWT_AUDIENCE"));
        pay.setRole(environment.get("EXTERNAL_PAY_SERVICE_JWT_ROLE"));
        pay.setTtlSeconds(ttlSeconds);
        pay.setScopes(environment.get("EXTERNAL_PAY_SERVICE_JWT_SCOPES"));
        try {
            PayServiceJwtConfigurationValidator.validate(pay);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid PayService caller JWT configuration", ex);
        }
    }

    private static boolean hasMysqlEndpoint(String jdbcUrl, String expectedHost, int expectedPort) {
        if (jdbcUrl == null || !jdbcUrl.regionMatches(true, 0, "jdbc:mysql://", 0, 13)) {
            return false;
        }
        try {
            URI uri = URI.create(jdbcUrl.substring(5));
            if (!"mysql".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getPath() == null || uri.getPath().length() <= 1) {
                return false;
            }
            return expectedHost == null
                    || (expectedHost.equalsIgnoreCase(uri.getHost()) && expectedPort == uri.getPort());
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean hasOnlyQueryValue(String url, String expectedKey, String expectedValue) {
        if (url == null) {
            return false;
        }
        int queryStart = url.indexOf('?');
        if (queryStart < 0 || queryStart == url.length() - 1) {
            return false;
        }
        boolean found = false;
        for (String parameter : url.substring(queryStart + 1).split("&", -1)) {
            int equals = parameter.indexOf('=');
            if (equals <= 0 || !parameter.substring(0, equals).equalsIgnoreCase(expectedKey)) {
                continue;
            }
            if (!parameter.substring(equals + 1).equalsIgnoreCase(expectedValue)) {
                return false;
            }
            found = true;
        }
        return found;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String exact(String value, String name, String... allowed) {
        for (String candidate : allowed) {
            if (candidate.equals(value)) {
                return value;
            }
        }
        throw new IllegalStateException(name + " must be exactly one of " + String.join(", ", allowed));
    }

    private static void require(Map<String, String> environment, List<String> names, List<String> missing) {
        for (String name : names) {
            String value = environment.get(name);
            if (value == null || value.isBlank()) {
                missing.add(name);
            }
        }
    }

    public static Map<String, String> templatePlaceholders() {
        return TEMPLATE_PLACEHOLDERS;
    }

    private static Map<String, String> loadTemplatePlaceholders() {
        Properties properties = new Properties();
        try (InputStream stream = RuntimeEnvironmentPreflight.class.getClassLoader()
                .getResourceAsStream(TEMPLATE_PLACEHOLDER_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing runtime template placeholder manifest");
            }
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load runtime template placeholder manifest", ex);
        }
        Map<String, String> values = new LinkedHashMap<>();
        properties.stringPropertyNames().stream().sorted()
                .forEach(name -> values.put(name, properties.getProperty(name)));
        if (values.isEmpty()) {
            throw new IllegalStateException("Runtime template placeholder manifest is empty");
        }
        return Map.copyOf(values);
    }
}
