package com.ainovel.app.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfSystemProperty(named = "externalMysql.enabled", matches = "true")
class ExternalMySqlMigrationVerificationTest {
    private static final String DATABASE_PREFIX = "ainovel_verify_";
    private static final Pattern DATABASE_NAME_PATTERN =
            Pattern.compile("^" + DATABASE_PREFIX + "[0-9a-f]{32}$");
    private static final Pattern SAFE_HOST_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]+$");
    private static final String JDBC_OPTIONS =
            "useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
                    + "&allowPublicKeyRetrieval=true&useSSL=false"
                    + "&connectTimeout=10000&socketTimeout=60000";

    @Test
    void migratesFreshExternalDatabaseFromV1ThroughV13() throws Exception {
        ExternalMySqlConfig config = ExternalMySqlConfig.load();
        withIsolatedDatabase(config, (databaseName, databaseUrl) -> {
            var result = flyway(config, databaseUrl).migrate();

            assertEquals(13, result.migrationsExecuted);
            SceneGenerationSchemaAssertions.assertV13Schema(
                    databaseUrl, config.username, config.password, databaseName);
        });
    }

    @Test
    void upgradesExternalDatabaseFromV12ToV13() throws Exception {
        ExternalMySqlConfig config = ExternalMySqlConfig.load();
        withIsolatedDatabase(config, (databaseName, databaseUrl) -> {
            var v12Result = Flyway.configure()
                    .dataSource(databaseUrl, config.username, config.password)
                    .locations("classpath:db/migration")
                    .target("12")
                    .load()
                    .migrate();
            assertEquals(12, v12Result.migrationsExecuted);

            var v13Result = flyway(config, databaseUrl).migrate();
            assertEquals(1, v13Result.migrationsExecuted);
            SceneGenerationSchemaAssertions.assertV13Schema(
                    databaseUrl, config.username, config.password, databaseName);
        });
    }

    private static Flyway flyway(ExternalMySqlConfig config, String databaseUrl) {
        return Flyway.configure()
                .dataSource(databaseUrl, config.username, config.password)
                .locations("classpath:db/migration")
                .load();
    }

    private static void withIsolatedDatabase(ExternalMySqlConfig config,
                                             IsolatedDatabaseOperation operation) throws Exception {
        String databaseName = DATABASE_PREFIX + UUID.randomUUID().toString().replace("-", "");
        requireSafeDatabaseName(databaseName);
        boolean created = false;
        Throwable operationFailure = null;
        try {
            try (Connection connection = DriverManager.getConnection(
                    config.serverUrl(), config.username, config.password);
                 Statement statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE `" + databaseName
                        + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                created = true;
            }
            operation.run(databaseName, config.databaseUrl(databaseName));
        } catch (Exception | Error failure) {
            operationFailure = failure;
            throw failure;
        } finally {
            if (created) {
                try {
                    dropCreatedDatabase(config, databaseName);
                } catch (Exception | Error cleanupFailure) {
                    if (operationFailure != null) {
                        operationFailure.addSuppressed(cleanupFailure);
                    } else {
                        throw cleanupFailure;
                    }
                }
            }
        }
    }

    private static void dropCreatedDatabase(ExternalMySqlConfig config, String databaseName) throws Exception {
        requireSafeDatabaseName(databaseName);
        try (Connection connection = DriverManager.getConnection(config.serverUrl(), config.username, config.password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE `" + databaseName + "`");
        }
    }

    private static void requireSafeDatabaseName(String databaseName) {
        if (!DATABASE_NAME_PATTERN.matcher(databaseName).matches()) {
            throw new IllegalArgumentException("Refusing database operation for a non-verification database name");
        }
    }

    @FunctionalInterface
    private interface IsolatedDatabaseOperation {
        void run(String databaseName, String databaseUrl) throws Exception;
    }

    private static final class ExternalMySqlConfig {
        private final String host;
        private final int port;
        private final String username;
        private final String password;

        private ExternalMySqlConfig(String host, int port, String username, String password) {
            this.host = host;
            this.port = port;
            this.username = username;
            this.password = password;
        }

        private static ExternalMySqlConfig load() throws Exception {
            Path envFile = Path.of(requiredSystemProperty("externalMysql.envFile"))
                    .toAbsolutePath()
                    .normalize();
            if (!Files.isRegularFile(envFile, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(envFile)) {
                throw new IllegalStateException(
                        "externalMysql.envFile must point to a non-symlink regular file");
            }

            Map<String, String> values = parseEnvFile(envFile);
            String host = propertyOverride("externalMysql.host", values, "MYSQL_HOST");
            if (!SAFE_HOST_PATTERN.matcher(host).matches()) {
                throw new IllegalStateException("External MySQL host contains unsupported characters");
            }
            int port = parsePort(propertyOverride("externalMysql.port", values, "MYSQL_PORT"));
            String username = requiredEnvValue(values, "MYSQL_USER");
            String password = requiredEnvValue(values, "MYSQL_PASSWORD");
            return new ExternalMySqlConfig(host, port, username, password);
        }

        private String serverUrl() {
            return "jdbc:mysql://" + host + ":" + port + "/?" + JDBC_OPTIONS;
        }

        private String databaseUrl(String databaseName) {
            requireSafeDatabaseName(databaseName);
            return "jdbc:mysql://" + host + ":" + port + "/" + databaseName + "?" + JDBC_OPTIONS;
        }

        private static Map<String, String> parseEnvFile(Path envFile) throws Exception {
            Map<String, String> values = new LinkedHashMap<>();
            for (String line : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                if (trimmed.startsWith("export ")) {
                    trimmed = trimmed.substring("export ".length()).trim();
                }
                int separator = trimmed.indexOf('=');
                if (separator <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, separator).trim();
                String value = unquote(trimmed.substring(separator + 1).trim());
                values.put(key, value);
            }
            return values;
        }

        private static String requiredSystemProperty(String key) {
            String value = System.getProperty(key, "").trim();
            if (value.isEmpty()) {
                throw new IllegalStateException("Missing required system property: " + key);
            }
            return value;
        }

        private static String propertyOverride(String propertyName,
                                               Map<String, String> values,
                                               String envKey) {
            String propertyValue = System.getProperty(propertyName, "").trim();
            return propertyValue.isEmpty() ? requiredEnvValue(values, envKey) : propertyValue;
        }

        private static String requiredEnvValue(Map<String, String> values, String key) {
            String value = values.getOrDefault(key, "").trim();
            if (value.isEmpty() || value.startsWith("replace-")) {
                throw new IllegalStateException("Missing usable key in external MySQL env file: " + key);
            }
            return value;
        }

        private static int parsePort(String rawPort) {
            try {
                int parsed = Integer.parseInt(rawPort);
                if (parsed < 1 || parsed > 65535) {
                    throw new IllegalStateException("External MySQL port must be between 1 and 65535");
                }
                return parsed;
            } catch (NumberFormatException exception) {
                throw new IllegalStateException("External MySQL port must be numeric", exception);
            }
        }

        private static String unquote(String value) {
            if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) {
                return value.substring(1, value.length() - 1);
            }
            return value;
        }
    }
}
