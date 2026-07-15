package com.ainovel.app.config;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import org.springframework.core.io.ByteArrayResource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
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

            assertEquals(5, result.migrationsExecuted);
            assertTableExists(mysql, databaseName, "stories");
            assertTableExists(mysql, databaseName, "slop_patterns");
            assertTableExists(mysql, databaseName, "workspace_layouts");
            assertTableExists(mysql, databaseName, "g2_evaluation_experiments");
            assertTableExists(mysql, databaseName, "g2_evaluation_votes");
            assertTableExists(mysql, databaseName, "creation_workflow_runs");
            assertTableExists(mysql, databaseName, "async_jobs");
            assertRowCount(mysql, databaseName, "slop_patterns", 38);
            assertTableExists(mysql, databaseName, "flyway_schema_history");
        }
    }

    @Test
    void baselinesCompleteLegacySchemaBeforeFutureMigrations() throws Exception {
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
            assertEquals(4, migrateResult.migrationsExecuted);
            assertHistoryType(mysql, databaseName, "1", "BASELINE");
            assertTableExists(mysql, databaseName, "slop_patterns");
            assertTableExists(mysql, databaseName, "workspace_layouts");
            assertTableExists(mysql, databaseName, "g2_evaluation_samples");
            assertTableExists(mysql, databaseName, "creation_workflow_runs");
            assertRowCount(mysql, databaseName, "slop_patterns", 38);
        }
    }

    @Test
    void backfillsV2PersistenceForLegacyDatabaseAlreadyBaselinedAtV1() throws Exception {
        try (MySQLContainer<?> mysql = new MySQLContainer<>(MYSQL_IMAGE)) {
            mysql.start();
            String databaseName = mysql.getDatabaseName();
            loadPreV2LegacySchema(mysql, databaseName);
            addDanglingCurrentBranchReference(mysql, databaseName);

            Flyway flyway = Flyway.configure()
                    .dataSource(databaseUrl(mysql, databaseName), mysql.getUsername(), mysql.getPassword())
                    .locations("classpath:db/migration")
                    .baselineVersion("1")
                    .baselineDescription("AINovel legacy schema")
                    .load();

            flyway.baseline();
            var migrateResult = flyway.migrate();

            assertEquals(4, migrateResult.migrationsExecuted);
            assertHistoryType(mysql, databaseName, "1", "BASELINE");
            assertV2PersistenceTablesExist(mysql, databaseName);
            assertTableExists(mysql, databaseName, "project_credit_accounts");
            assertTableExists(mysql, databaseName, "project_credit_ledger");
            assertIndexExists(mysql, databaseName, "project_credit_ledger", "idx_project_credit_ledger_reference");
            assertTableExists(mysql, databaseName, "creation_workflow_runs");
            assertTableExists(mysql, databaseName, "async_jobs");
            assertColumnExists(mysql, databaseName, "manuscripts", "current_branch_id");
            assertCurrentBranchForeignKeyExists(mysql, databaseName);
            assertNoDanglingCurrentBranchReferences(mysql, databaseName);
        }
    }

    private static void loadLegacySchema(MySQLContainer<?> mysql, String databaseName) throws Exception {
        try (Connection connection = DriverManager.getConnection(databaseUrl(mysql, databaseName), mysql.getUsername(), mysql.getPassword())) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V1__baseline.sql"));
        }
    }

    private static void loadPreV2LegacySchema(MySQLContainer<?> mysql, String databaseName) throws Exception {
        ClassPathResource resource = new ClassPathResource("db/migration/V1__baseline.sql");
        String baselineSql = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int v2Start = baselineSql.indexOf("-- v2 incremental schema");
        String preV2Sql = baselineSql.substring(0, v2Start) + "\nSET FOREIGN_KEY_CHECKS = 1;\n";

        try (Connection connection = DriverManager.getConnection(databaseUrl(mysql, databaseName), mysql.getUsername(), mysql.getPassword())) {
            ScriptUtils.executeSqlScript(connection,
                    new EncodedResource(new ByteArrayResource(preV2Sql.getBytes(StandardCharsets.UTF_8))));
        }
    }

    private static void addDanglingCurrentBranchReference(MySQLContainer<?> mysql, String databaseName) throws Exception {
        try (Connection connection = DriverManager.getConnection(databaseUrl(mysql, databaseName), mysql.getUsername(), mysql.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE manuscripts ADD COLUMN current_branch_id binary(16) DEFAULT NULL");
            statement.execute("INSERT INTO manuscripts (id, current_branch_id) VALUES (UNHEX(REPLACE(UUID(),'-','')), UNHEX(REPLACE(UUID(),'-','')))");
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

    private static void assertV2PersistenceTablesExist(MySQLContainer<?> mysql, String databaseName) throws Exception {
        for (String tableName : List.of(
                "lorebook_entries", "entity_extractions", "knowledge_graph_relationships",
                "style_profiles", "style_profile_scene_overrides", "character_voices", "style_analysis_jobs",
                "beta_reader_reports", "continuity_issues", "analysis_jobs",
                "manuscript_branches", "manuscript_versions", "version_diffs", "auto_save_config",
                "export_templates", "export_jobs", "model_registry", "task_model_routing", "user_model_preferences",
                "model_usage_logs", "workspace_layouts", "writing_sessions", "writing_goals", "keyboard_shortcuts")) {
            assertTableExists(mysql, databaseName, tableName);
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

    private static void assertColumnExists(MySQLContainer<?> mysql, String databaseName, String tableName, String columnName) throws Exception {
        try (Connection connection = DriverManager.getConnection(databaseUrl(mysql, databaseName), mysql.getUsername(), mysql.getPassword());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = '" + databaseName
                             + "' AND table_name = '" + tableName + "' AND column_name = '" + columnName + "'")) {
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt(1));
        }
    }

    private static void assertIndexExists(MySQLContainer<?> mysql,
                                          String databaseName,
                                          String tableName,
                                          String indexName) throws Exception {
        try (Connection connection = DriverManager.getConnection(databaseUrl(mysql, databaseName), mysql.getUsername(), mysql.getPassword());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = '" + databaseName
                             + "' AND table_name = '" + tableName + "' AND index_name = '" + indexName + "'")) {
            assertTrue(resultSet.next());
            assertTrue(resultSet.getInt(1) >= 1);
        }
    }

    private static void assertCurrentBranchForeignKeyExists(MySQLContainer<?> mysql, String databaseName) throws Exception {
        try (Connection connection = DriverManager.getConnection(databaseUrl(mysql, databaseName), mysql.getUsername(), mysql.getPassword());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.key_column_usage WHERE table_schema = '" + databaseName
                             + "' AND table_name = 'manuscripts' AND column_name = 'current_branch_id'"
                             + " AND referenced_table_name = 'manuscript_branches' AND referenced_column_name = 'id'")) {
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt(1));
        }
    }

    private static void assertNoDanglingCurrentBranchReferences(MySQLContainer<?> mysql, String databaseName) throws Exception {
        try (Connection connection = DriverManager.getConnection(databaseUrl(mysql, databaseName), mysql.getUsername(), mysql.getPassword());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM manuscripts WHERE current_branch_id IS NOT NULL")) {
            assertTrue(resultSet.next());
            assertEquals(0, resultSet.getInt(1));
        }
    }

    private static void assertRowCount(MySQLContainer<?> mysql, String databaseName, String tableName, int expected) throws Exception {
        try (Connection connection = DriverManager.getConnection(databaseUrl(mysql, databaseName), mysql.getUsername(), mysql.getPassword());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            assertTrue(resultSet.next());
            assertEquals(expected, resultSet.getInt(1));
        }
    }

    private static String databaseUrl(MySQLContainer<?> mysql, String databaseName) {
        return mysql.getJdbcUrl().replace("/" + mysql.getDatabaseName(), "/" + databaseName);
    }
}
