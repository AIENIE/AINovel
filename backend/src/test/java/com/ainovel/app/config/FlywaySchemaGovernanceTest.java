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

            assertEquals(8, result.migrationsExecuted);
            assertTableExists(mysql, databaseName, "stories");
            assertTableExists(mysql, databaseName, "slop_patterns");
            assertTableExists(mysql, databaseName, "workspace_layouts");
            assertTableExists(mysql, databaseName, "g2_evaluation_experiments");
            assertTableExists(mysql, databaseName, "g2_evaluation_votes");
            assertTableExists(mysql, databaseName, "creation_workflow_runs");
            assertTableExists(mysql, databaseName, "async_jobs");
            assertTableExists(mysql, databaseName, "ai_operation_runs");
            assertTableExists(mysql, databaseName, "ai_operation_steps");
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
            assertEquals(7, migrateResult.migrationsExecuted);
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

            assertEquals(7, migrateResult.migrationsExecuted);
            assertHistoryType(mysql, databaseName, "1", "BASELINE");
            assertV2PersistenceTablesExist(mysql, databaseName);
            assertTableExists(mysql, databaseName, "project_credit_accounts");
            assertTableExists(mysql, databaseName, "project_credit_ledger");
            assertIndexExists(mysql, databaseName, "project_credit_ledger", "idx_project_credit_ledger_reference");
            assertTableExists(mysql, databaseName, "creation_workflow_runs");
            assertTableExists(mysql, databaseName, "async_jobs");
            assertTableExists(mysql, databaseName, "ai_operation_runs");
            assertColumnExists(mysql, databaseName, "manuscripts", "current_branch_id");
            assertCurrentBranchForeignKeyExists(mysql, databaseName);
            assertNoDanglingCurrentBranchReferences(mysql, databaseName);
        }
    }

    @Test
    void backfillsMissingSlopQualityIssueColumnsForLegacyBaseline() throws Exception {
        try (MySQLContainer<?> mysql = new MySQLContainer<>(MYSQL_IMAGE)) {
            mysql.start();
            String databaseName = mysql.getDatabaseName();
            loadLegacySchema(mysql, databaseName);
            dropLegacySlopQualityIssueColumns(mysql, databaseName);

            Flyway flyway = Flyway.configure()
                    .dataSource(databaseUrl(mysql, databaseName), mysql.getUsername(), mysql.getPassword())
                    .locations("classpath:db/migration")
                    .baselineVersion("1")
                    .baselineDescription("AINovel legacy schema")
                    .load();

            flyway.baseline();
            var migrateResult = flyway.migrate();

            assertEquals(7, migrateResult.migrationsExecuted);
            for (String column : List.of(
                    "char_start", "char_end", "quote", "module", "pattern_id", "issue_type",
                    "evidence_level", "alternative_explanations_json", "repair_hint")) {
                assertColumnExists(mysql, databaseName, "slop_quality_issues", column);
            }
        }
    }

    @Test
    void cascadesCompleteStoryTreeAndQualityRuns() throws Exception {
        try (MySQLContainer<?> mysql = new MySQLContainer<>(MYSQL_IMAGE)) {
            mysql.start();
            String databaseName = mysql.getDatabaseName();
            Flyway.configure().dataSource(databaseUrl(mysql, databaseName), mysql.getUsername(), mysql.getPassword())
                    .locations("classpath:db/migration").load().migrate();

            try (Connection connection = DriverManager.getConnection(databaseUrl(mysql, databaseName), mysql.getUsername(), mysql.getPassword());
                 Statement statement = connection.createStatement()) {
                statement.execute("INSERT INTO users (id,banned,credits,email,password_hash,username) VALUES (UNHEX('01010101010101010101010101010101'),0,0,'cascade@test','x','cascade-user')");
                statement.execute("INSERT INTO stories (id,user_id,title) VALUES (UNHEX('02020202020202020202020202020202'),UNHEX('01010101010101010101010101010101'),'cascade')");
                statement.execute("INSERT INTO character_cards (id,story_id,name) VALUES (UNHEX('03030303030303030303030303030303'),UNHEX('02020202020202020202020202020202'),'card')");
                statement.execute("INSERT INTO outlines (id,story_id,title) VALUES (UNHEX('04040404040404040404040404040404'),UNHEX('02020202020202020202020202020202'),'outline')");
                statement.execute("INSERT INTO manuscripts (id,outline_id,title) VALUES (UNHEX('05050505050505050505050505050505'),UNHEX('04040404040404040404040404040404'),'manuscript')");
                statement.execute("INSERT INTO slop_drift_runs (id,story_id,manuscript_id,status,overall_risk_score,total_characters,window_count) VALUES (UNHEX('06060606060606060606060606060606'),UNHEX('02020202020202020202020202020202'),UNHEX('05050505050505050505050505050505'),'ACCEPTED',0,1,1)");
                statement.execute("INSERT INTO slop_quality_runs (id,story_id,manuscript_id,scene_id,status,max_severity,overall_risk_score,revised,revision_count) VALUES (UNHEX('07070707070707070707070707070707'),UNHEX('02020202020202020202020202020202'),UNHEX('05050505050505050505050505050505'),UNHEX('08080808080808080808080808080808'),'ACCEPTED','LOW',0,0,0)");
                statement.execute("INSERT INTO plot_quality_runs (id,story_id,manuscript_id,scene_id,status,max_severity,overall_risk_score,revision_applied) VALUES (UNHEX('09090909090909090909090909090909'),UNHEX('02020202020202020202020202020202'),UNHEX('05050505050505050505050505050505'),UNHEX('08080808080808080808080808080808'),'ACCEPTED','LOW',0,0)");
                statement.execute("DELETE FROM stories WHERE id=UNHEX('02020202020202020202020202020202')");
            }

            for (String table : List.of("stories", "character_cards", "outlines", "manuscripts",
                    "slop_drift_runs", "slop_quality_runs", "plot_quality_runs")) {
                assertRowCount(mysql, databaseName, table, 0);
            }
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

    private static void dropLegacySlopQualityIssueColumns(MySQLContainer<?> mysql, String databaseName) throws Exception {
        try (Connection connection = DriverManager.getConnection(databaseUrl(mysql, databaseName), mysql.getUsername(), mysql.getPassword());
             Statement statement = connection.createStatement()) {
            for (String column : List.of(
                    "char_start", "char_end", "quote", "module", "pattern_id", "issue_type",
                    "evidence_level", "alternative_explanations_json", "repair_hint")) {
                statement.execute("ALTER TABLE slop_quality_issues DROP COLUMN " + column);
            }
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
