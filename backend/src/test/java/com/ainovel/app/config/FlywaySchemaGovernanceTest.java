package com.ainovel.app.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywaySchemaGovernanceTest {
    private static final String MYSQL_IMAGE = "mysql:8.0.36";

    @Test
    void buildsEmptyDatabaseFromBaselineMigration() throws Exception {
        try (MySQLContainer<?> mysql = new MySQLContainer<>(MYSQL_IMAGE)) {
            mysql.start();
            String databaseName = mysql.getDatabaseName();

            Flyway flyway = Flyway.configure()
                    .dataSource(databaseUrl(mysql, databaseName), mysql.getUsername(), mysql.getPassword())
                    .locations("classpath:db/migration")
                    .load();

            var result = flyway.migrate();

            assertEquals(1, result.migrationsExecuted);
            assertTableExists(mysql, databaseName, "stories");
            assertTableExists(mysql, databaseName, "flyway_schema_history");
        }
    }

    @Test
    void baselinesExistingSchemaBeforeFutureMigrations() throws Exception {
        try (MySQLContainer<?> mysql = new MySQLContainer<>(MYSQL_IMAGE)) {
            mysql.start();
            String databaseName = mysql.getDatabaseName();
            loadLegacySchema(mysql, databaseName);

            Flyway flyway = Flyway.configure()
                    .dataSource(databaseUrl(mysql, databaseName), mysql.getUsername(), mysql.getPassword())
                    .locations("classpath:db/migration")
                    .baselineVersion("1")
                    .baselineDescription("AINovel legacy schema")
                    .load();

            var baselineResult = flyway.baseline();
            var migrateResult = flyway.migrate();

            assertTrue(baselineResult.successfullyBaselined);
            assertEquals(0, migrateResult.migrationsExecuted);
            assertHistoryType(mysql, databaseName, "1", "BASELINE");
        }
    }

    private static void loadLegacySchema(MySQLContainer<?> mysql, String databaseName) throws Exception {
        try (Connection connection = DriverManager.getConnection(databaseUrl(mysql, databaseName), mysql.getUsername(), mysql.getPassword())) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V1__baseline.sql"));
        }
    }

    private static void assertTableExists(MySQLContainer<?> mysql, String databaseName, String tableName) throws Exception {
        try (Connection connection = DriverManager.getConnection(databaseUrl(mysql, databaseName), mysql.getUsername(), mysql.getPassword());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '" + databaseName + "' AND table_name = '" + tableName + "'")) {
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt(1));
        }
    }

    private static void assertHistoryType(MySQLContainer<?> mysql, String databaseName, String version, String type) throws Exception {
        try (Connection connection = DriverManager.getConnection(databaseUrl(mysql, databaseName), mysql.getUsername(), mysql.getPassword());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT type FROM flyway_schema_history WHERE version = '" + version + "'")) {
            assertTrue(resultSet.next());
            assertEquals(type, resultSet.getString(1));
        }
    }

    private static String databaseUrl(MySQLContainer<?> mysql, String databaseName) {
        return mysql.getJdbcUrl().replace("/" + mysql.getDatabaseName(), "/" + databaseName);
    }
}
