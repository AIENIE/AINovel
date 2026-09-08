package com.ainovel.app.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProductionNovelMigrationExternalMySqlTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path temporary;

    @Test
    void oneShotFlywayIsReadOnlyThenIdempotentAndRestorable() throws Exception {
        String url = System.getenv("AIENIE_NOVEL_MIGRATION_TEST_URL");
        String username = System.getenv("AIENIE_NOVEL_MIGRATION_TEST_USERNAME");
        String password = System.getenv("AIENIE_NOVEL_MIGRATION_TEST_PASSWORD");
        Assumptions.assumeTrue(url != null && username != null && password != null);
        assertTrue(url.matches("jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/aienie_novel_migration_test_[A-Za-z0-9_]+\\?.*"));

        Path migrationRoot = temporary.resolve("release/migrations/sql");
        Files.createDirectories(migrationRoot);
        Path sourceRoot = Path.of("src/main/resources/db/migration");
        List<Map<String, String>> migrations = new ArrayList<>();
        for (int version = 1; version <= 15; version++) {
            int selectedVersion = version;
            Path source = Files.list(sourceRoot)
                    .filter(path -> path.getFileName().toString().startsWith("V" + selectedVersion + "__"))
                    .findFirst().orElseThrow();
            Path destination = migrationRoot.resolve(source.getFileName().toString());
            Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
            migrations.add(Map.of(
                    "path", "release/migrations/sql/" + destination.getFileName(),
                    "sha256", sha256(Files.readAllBytes(destination)),
                    "version", Integer.toString(version)));
        }
        Map<String, Object> ledger = new LinkedHashMap<>();
        ledger.put("authorization", Map.of(
                "minimum_release_manifest_version", 4,
                "outer_signature_required", true,
                "restore_point_required_before_execute", true));
        ledger.put("canonical_component_id", "ai-novel");
        ledger.put("latest_version", "15");
        ledger.put("location", "filesystem:" + migrationRoot.toAbsolutePath());
        ledger.put("migrations", migrations);
        ledger.put("schema_version", "aienie-production-flyway-ledger-v2");
        Path ledgerPath = temporary.resolve("release/migrations/flyway-ledger.json");
        Files.writeString(ledgerPath, JSON.writeValueAsString(ledger), StandardCharsets.UTF_8);

        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            int tablesBefore = scalar(connection, """
                    SELECT COUNT(*) FROM information_schema.tables
                     WHERE table_schema=DATABASE() AND table_type='BASE TABLE'
                    """);
            JsonNode precheck = result(ProductionNovelMigrationMain.runForTest(
                    "precheck", url, username, password, ledgerPath,
                    "filesystem:" + migrationRoot.toAbsolutePath(), temporary.resolve("unused.sql")));
            assertEquals("pending", precheck.path("status").textValue());
            assertEquals(15, precheck.path("pending_entry_count_before").intValue());
            assertFalse(precheck.path("mutation_performed").booleanValue());
            assertEquals(tablesBefore, scalar(connection, """
                    SELECT COUNT(*) FROM information_schema.tables
                     WHERE table_schema=DATABASE() AND table_type='BASE TABLE'
                    """));

            JsonNode execute = result(ProductionNovelMigrationMain.runForTest(
                    "execute", url, username, password, ledgerPath,
                    "filesystem:" + migrationRoot.toAbsolutePath(), temporary.resolve("unused.sql")));
            assertEquals("current", execute.path("status").textValue());
            assertEquals(15, execute.path("applied_entry_count").intValue());
            assertTrue(execute.path("mutation_performed").booleanValue());
            JsonNode noOp = result(ProductionNovelMigrationMain.runForTest(
                    "execute", url, username, password, ledgerPath,
                    "filesystem:" + migrationRoot.toAbsolutePath(), temporary.resolve("unused.sql")));
            assertEquals(0, noOp.path("applied_entry_count").intValue());
            assertFalse(noOp.path("mutation_performed").booleanValue());

            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE checkpoint_fixture(id INT PRIMARY KEY, value_text VARCHAR(64) NOT NULL) ENGINE=InnoDB");
                statement.executeUpdate("INSERT INTO checkpoint_fixture(id,value_text) VALUES(1,'preserved')");
            }
            Path checkpoint = temporary.resolve("checkpoint.sql").toAbsolutePath();
            JsonNode checkpointResult = result(ProductionNovelMigrationMain.runForTest(
                    "checkpoint", url, username, password, ledgerPath,
                    "filesystem:" + migrationRoot.toAbsolutePath(), checkpoint));
            assertEquals("checkpoint-created", checkpointResult.path("status").textValue());
            assertTrue(Files.size(checkpoint) > 0);
            assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                    Files.getPosixFilePermissions(checkpoint));

            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM checkpoint_fixture");
                statement.execute("CREATE TABLE post_checkpoint_table(id INT PRIMARY KEY) ENGINE=InnoDB");
            }
            executeRestoreScript(connection, checkpoint);
            assertEquals(1, scalar(connection,
                    "SELECT COUNT(*) FROM checkpoint_fixture WHERE id=1 AND value_text='preserved'"));
            assertEquals(0, scalar(connection, """
                    SELECT COUNT(*) FROM information_schema.tables
                     WHERE table_schema=DATABASE() AND table_name='post_checkpoint_table'
                    """));
            JsonNode reconcile = result(ProductionNovelMigrationMain.runForTest(
                    "reconcile", url, username, password, ledgerPath,
                    "filesystem:" + migrationRoot.toAbsolutePath(), checkpoint));
            assertEquals("current", reconcile.path("status").textValue());
            assertFalse(reconcile.path("mutation_performed").booleanValue());
        }
    }

    private static JsonNode result(String raw) throws Exception {
        JsonNode value = JSON.readTree(raw);
        assertEquals("aienie-production-migration-database-result-v1", value.path("schema_version").textValue());
        assertEquals("ai-novel", value.path("canonical_component_id").textValue());
        return value;
    }

    private static String sha256(byte[] raw) throws Exception {
        return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
    }

    private static int scalar(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            int value = result.getInt(1);
            assertFalse(result.next());
            return value;
        }
    }

    private static void executeRestoreScript(Connection connection, Path checkpoint) throws Exception {
        StringBuilder statementText = new StringBuilder();
        for (String line : Files.readAllLines(checkpoint, StandardCharsets.UTF_8)) {
            if (line.startsWith("--")) {
                continue;
            }
            statementText.append(line).append('\n');
            if (!line.stripTrailing().endsWith(";")) {
                continue;
            }
            String sql = statementText.toString().strip();
            sql = sql.substring(0, sql.length() - 1);
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
            statementText.setLength(0);
        }
        assertTrue(statementText.toString().isBlank());
    }
}
