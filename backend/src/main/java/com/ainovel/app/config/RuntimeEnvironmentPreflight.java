package com.ainovel.app.config;

import com.ainovel.app.adminauth.AdminAuthPolicyRules;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
            "EXTERNAL_USER_INTERNAL_GRPC_TOKEN", "EXTERNAL_PAY_SERVICE_JWT"
    );
    private static final List<String> TOTP_REQUIRED = List.of(
            "ADMIN_TOTP_ENCRYPTION_KEYS", "ADMIN_TOTP_ACTIVE_KEY_VERSION"
    );
    private static final List<String> NON_LOCAL_DATA_SECURITY_REQUIRED = List.of(
            "DB_URL", "DB_USERNAME", "REDIS_SSL_ENABLED", "REDIS_USERNAME",
            "REDIS_PASSWORD", "QDRANT_HOST", "QDRANT_API_KEY"
    );
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

        if ("local".equals(env)) {
            System.getLogger(RuntimeEnvironmentPreflight.class.getName()).log(
                    System.Logger.Level.WARNING,
                    "Local profile permits plaintext MySQL/Redis/Qdrant for development only; never reuse it for test or production"
            );
            return;
        }

        String dbUrl = environment.get("DB_URL");
        if (dbUrl == null || !dbUrl.toLowerCase(java.util.Locale.ROOT).contains("sslmode=verify_identity")) {
            throw new IllegalStateException("DB_URL must require sslMode=VERIFY_IDENTITY outside local");
        }
        String dbUsername = environment.get("DB_USERNAME");
        if ("root".equalsIgnoreCase(dbUsername) || "ainovel".equalsIgnoreCase(dbUsername)) {
            throw new IllegalStateException("DB_USERNAME must be a non-template least-privilege account outside local");
        }
        if (!"true".equals(environment.get("REDIS_SSL_ENABLED"))) {
            throw new IllegalStateException("REDIS_SSL_ENABLED must be exactly true outside local");
        }
        String qdrantHost = environment.get("QDRANT_HOST");
        if (qdrantHost == null || !qdrantHost.toLowerCase(java.util.Locale.ROOT).startsWith("https://")) {
            throw new IllegalStateException("QDRANT_HOST must use https outside local");
        }
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
