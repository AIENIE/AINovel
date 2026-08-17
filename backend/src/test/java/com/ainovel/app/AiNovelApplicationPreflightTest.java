package com.ainovel.app;

import com.ainovel.app.config.RuntimeEnvironmentPreflight;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiNovelApplicationPreflightTest {
    @Test
    void acceptsOnlyDeclaredEnvironmentAndAuthenticationModeCombinations() {
        for (String[] policy : new String[][] {
                { "local", "password" }, { "local", "totp" },
                { "test", "totp" }, { "production", "totp" }
        }) {
            Map<String, String> environment = validEnvironment();
            environment.put("ENV", policy[0]);
            environment.put("AUTH_MODE", policy[1]);
            assertDoesNotThrow(() -> AiNovelApplication.preflight(environment));
        }

        for (String[] policy : new String[][] {
                { "LOCAL", "totp" }, { "development", "totp" },
                { "local", "TOTP" }, { "local", "disabled" },
                { "test", "password" }, { "production", "password" }
        }) {
            Map<String, String> environment = validEnvironment();
            environment.put("ENV", policy[0]);
            environment.put("AUTH_MODE", policy[1]);
            assertThrows(IllegalStateException.class, () -> AiNovelApplication.preflight(environment));
        }
    }

    @Test
    void everyFixedEnvExamplePlaceholderFailsBeforeSpringStartup() throws IOException {
        Map<String, String> template = readEnvExample();
        Map<String, String> placeholders = RuntimeEnvironmentPreflight.templatePlaceholders();
        Map<String, String> fixedTemplateValues = template.entrySet().stream()
                .filter(entry -> entry.getValue().toLowerCase().contains("replace-")
                        || entry.getValue().toLowerCase().contains("replace_")
                        || entry.getValue().toLowerCase().contains("change-me")
                        || entry.getValue().toLowerCase().contains("change_me"))
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> right,
                        LinkedHashMap::new
                ));
        assertEquals(placeholders, fixedTemplateValues);

        placeholders.forEach((name, value) -> {
            Map<String, String> environment = validEnvironment();
            environment.put(name, value);
            IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> AiNovelApplication.preflight(environment),
                    name
            );
            assertTrue(error.getMessage().contains(name));
        });
    }

    @Test
    void failedPreflightNeverInvokesSpringLauncher() {
        Map<String, String> environment = validEnvironment();
        environment.put("JWT_SECRET", RuntimeEnvironmentPreflight.templatePlaceholders().get("JWT_SECRET"));
        AtomicBoolean launched = new AtomicBoolean();

        assertThrows(IllegalStateException.class, () ->
                AiNovelApplication.launch(environment, () -> launched.set(true))
        );

        assertTrue(!launched.get());
    }

    @Test
    void nonTemplateValuesContainingPlaceholderWordsAreAccepted() {
        Map<String, String> environment = validEnvironment();
        environment.put("JWT_ISSUER", "replace-service");
        environment.put("JWT_SECRET", "replace-with-a-different-runtime-value");

        assertDoesNotThrow(() -> AiNovelApplication.preflight(environment));
    }

    @Test
    void nonLocalRequiresAuthenticatedEncryptedDataServices() {
        Map<String, String> environment = validEnvironment();
        environment.put("ENV", "production");
        environment.keySet().removeAll(java.util.Set.of("DB_URL", "DB_USERNAME", "REDIS_SSL_ENABLED", "REDIS_USERNAME", "QDRANT_HOST", "QDRANT_API_KEY"));
        assertThrows(IllegalStateException.class, () -> AiNovelApplication.preflight(environment));

        environment.put("DB_URL", "jdbc:mysql://db.example/ainovel?sslMode=VERIFY_IDENTITY");
        environment.put("DB_USERNAME", "ainovel_runtime");
        environment.put("REDIS_SSL_ENABLED", "true");
        environment.put("REDIS_USERNAME", "ainovel_runtime");
        environment.put("QDRANT_HOST", "https://qdrant.example");
        environment.put("QDRANT_API_KEY", "unit-test-qdrant-key");
        assertDoesNotThrow(() -> AiNovelApplication.preflight(environment));
    }

    private Map<String, String> validEnvironment() {
        Map<String, String> environment = new HashMap<>();
        environment.put("ENV", "local");
        environment.put("AUTH_MODE", "totp");
        environment.put("ADMIN_USERNAME", "local-operator");
        environment.put("ADMIN_PASSWORD_HASH", "$2a$10$dEnQNJUtxcEVsri32i2KmuRrrOBNtvoyn8j1HBUHpRRBjB14bNUgq");
        environment.put("ADMIN_TOTP_ENCRYPTION_KEYS", "v1:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        environment.put("ADMIN_TOTP_ACTIVE_KEY_VERSION", "v1");
        environment.put("ADMIN_TRUSTED_ORIGINS", "https://localainovel.testhut.top");
        environment.put("ADMIN_SESSION_COOKIE_SECURE", "true");
        environment.put("JWT_SECRET", "unit-test-jwt-secret-with-at-least-32-bytes");
        environment.put("JWT_ISSUER", "ainovel");
        environment.put("JWT_AUDIENCE", "ainovel-web");
        environment.put("MYSQL_PASSWORD", "unit-test-database-password");
        environment.put("REDIS_PASSWORD", "unit-test-redis-password");
        environment.put("SPRING_JPA_HIBERNATE_DDL_AUTO", "none");
        environment.put("EXTERNAL_AI_HMAC_CALLER", "ainovel-unit-test");
        environment.put("EXTERNAL_AI_HMAC_SECRET", "unit-test-hmac-secret-with-at-least-32-bytes");
        environment.put("EXTERNAL_USER_INTERNAL_GRPC_TOKEN", "unit-test-user-token");
        environment.put("EXTERNAL_PAY_SERVICE_JWT", "header.payload.signature");
        environment.put("DB_URL", "jdbc:mysql://db.example/ainovel?sslMode=VERIFY_IDENTITY");
        environment.put("DB_USERNAME", "ainovel_runtime");
        environment.put("REDIS_SSL_ENABLED", "true");
        environment.put("REDIS_USERNAME", "ainovel_runtime");
        environment.put("QDRANT_HOST", "https://qdrant.example");
        environment.put("QDRANT_API_KEY", "unit-test-qdrant-key");
        return environment;
    }

    private Map<String, String> readEnvExample() throws IOException {
        Path template = Files.exists(Path.of("env.example"))
                ? Path.of("env.example")
                : Path.of("..", "env.example");
        Map<String, String> values = new LinkedHashMap<>();
        for (String raw : Files.readAllLines(template)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                continue;
            }
            String name = line.substring(0, line.indexOf('=')).trim();
            String value = line.substring(line.indexOf('=') + 1).trim();
            if (value.length() >= 2
                    && ((value.startsWith("'") && value.endsWith("'"))
                    || (value.startsWith("\"") && value.endsWith("\"")))) {
                value = value.substring(1, value.length() - 1);
            }
            values.put(name, value);
        }
        return values;
    }
}
