package com.ainovel.app.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class SceneGenerationSchemaAssertions {
    private static final String TABLE_NAME = "scene_generation_runs";
    private static final List<ColumnDefinition> EXPECTED_COLUMNS = List.of(
            required("id", "binary(16)"), required("manuscript_id", "binary(16)"),
            required("scene_id", "binary(16)"), required("generation_version_id", "binary(16)"),
            required("created_by", "binary(16)"), nullable("previous_run_id", "binary(16)"),
            nullable("superseded_by_run_id", "binary(16)"), textRequired("status", "varchar(32)"),
            textRequired("attribution_status", "varchar(32)"), textRequired("mode", "varchar(16)"),
            textRequired("model_key", "varchar(120)"), textRequired("prompt_version", "varchar(120)"),
            requiredDefault("attempt_count", "int", "1"), textNullable("context_hash", "char(64)"),
            textRequired("prompt_hash", "char(64)"), textNullable("context_manifest_json", "longtext"),
            textRequired("generated_content_hash", "char(64)"),
            textNullable("current_content_hash", "char(64)"), required("generated_characters", "int"),
            nullable("current_characters", "int"), nullable("retained_characters", "int"),
            nullable("added_characters", "int"), nullable("deleted_characters", "int"),
            nullable("retention_rate", "decimal(8,6)"), nullable("exact_match", "bit(1)"),
            textNullable("diff_json", "longtext"), textNullable("tags_json", "longtext"),
            textNullable("note", "varchar(500)"), requiredDefault("preference_confirmed", "bit(1)", "b'0'"),
            nullable("preference_confirmed_at", "datetime(6)"), nullable("first_edited_at", "datetime(6)"),
            nullable("last_edited_at", "datetime(6)"), nullable("recomputed_at", "datetime(6)"),
            nullable("created_at", "datetime(6)"), nullable("updated_at", "datetime(6)"));
    private static final Map<String, IndexDefinition> EXPECTED_INDEXES = Map.of(
            "PRIMARY", new IndexDefinition(false, List.of("id")),
            "idx_scene_generation_run_active",
            new IndexDefinition(true, List.of("manuscript_id", "scene_id", "status")),
            "idx_scene_generation_run_created_by", new IndexDefinition(true, List.of("created_by")),
            "idx_scene_generation_run_previous", new IndexDefinition(true, List.of("previous_run_id")),
            "idx_scene_generation_run_scene",
            new IndexDefinition(true, List.of("manuscript_id", "scene_id", "created_at")),
            "idx_scene_generation_run_superseded_by",
            new IndexDefinition(true, List.of("superseded_by_run_id")),
            "idx_scene_generation_run_version", new IndexDefinition(true, List.of("generation_version_id")));
    private static final Map<String, ForeignKeyDefinition> EXPECTED_FOREIGN_KEYS = Map.of(
            "fk_scene_generation_run_manuscript",
            new ForeignKeyDefinition("manuscript_id", "manuscripts", "id", "CASCADE"),
            "fk_scene_generation_run_version",
            new ForeignKeyDefinition("generation_version_id", "manuscript_versions", "id", "CASCADE"),
            "fk_scene_generation_run_created_by",
            new ForeignKeyDefinition("created_by", "users", "id", "CASCADE"),
            "fk_scene_generation_run_previous",
            new ForeignKeyDefinition("previous_run_id", TABLE_NAME, "id", "SET NULL"),
            "fk_scene_generation_run_superseded_by",
            new ForeignKeyDefinition("superseded_by_run_id", TABLE_NAME, "id", "SET NULL"));

    private SceneGenerationSchemaAssertions() {
    }

    static void assertV13Schema(String jdbcUrl,
                                String username,
                                String password,
                                String databaseName) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            assertEquals(1, tableCount(connection, databaseName), TABLE_NAME + " table count");
            assertEquals(EXPECTED_COLUMNS, columns(connection, databaseName), TABLE_NAME + " column definitions");
            assertEquals(EXPECTED_INDEXES, indexes(connection, databaseName), TABLE_NAME + " index definitions");
            assertEquals(EXPECTED_FOREIGN_KEYS, foreignKeys(connection, databaseName),
                    TABLE_NAME + " foreign keys and delete rules");
        }
    }

    private static int tableCount(Connection connection, String databaseName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, databaseName);
            statement.setString(2, TABLE_NAME);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private static List<ColumnDefinition> columns(Connection connection, String databaseName) throws SQLException {
        String sql = "SELECT column_name, column_type, is_nullable, column_default, collation_name "
                + "FROM information_schema.columns "
                + "WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position";
        List<ColumnDefinition> columns = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, databaseName);
            statement.setString(2, TABLE_NAME);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    columns.add(new ColumnDefinition(
                            resultSet.getString("column_name"),
                            resultSet.getString("column_type"),
                            "YES".equals(resultSet.getString("is_nullable")),
                            resultSet.getString("column_default"),
                            resultSet.getString("collation_name")));
                }
            }
        }
        return columns;
    }

    private static Map<String, IndexDefinition> indexes(Connection connection, String databaseName) throws SQLException {
        String sql = "SELECT index_name, non_unique, seq_in_index, column_name "
                + "FROM information_schema.statistics WHERE table_schema = ? AND table_name = ? "
                + "ORDER BY index_name, seq_in_index";
        Map<String, MutableIndexDefinition> indexes = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, databaseName);
            statement.setString(2, TABLE_NAME);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String name = resultSet.getString("index_name");
                    boolean nonUnique = resultSet.getInt("non_unique") != 0;
                    MutableIndexDefinition definition = indexes.get(name);
                    if (definition == null) {
                        definition = new MutableIndexDefinition(nonUnique);
                        indexes.put(name, definition);
                    } else if (definition.nonUnique != nonUnique) {
                        throw new SQLException("Inconsistent uniqueness metadata for index " + name);
                    }
                    definition.columns.add(resultSet.getString("column_name"));
                }
            }
        }
        Map<String, IndexDefinition> definitions = new LinkedHashMap<>();
        indexes.forEach((name, definition) -> definitions.put(
                name, new IndexDefinition(definition.nonUnique, List.copyOf(definition.columns))));
        return definitions;
    }

    private static Map<String, ForeignKeyDefinition> foreignKeys(Connection connection,
                                                                  String databaseName) throws SQLException {
        String sql = "SELECT kcu.constraint_name, kcu.column_name, kcu.referenced_table_name, "
                + "kcu.referenced_column_name, rc.delete_rule "
                + "FROM information_schema.key_column_usage kcu "
                + "JOIN information_schema.referential_constraints rc "
                + "ON rc.constraint_schema = kcu.constraint_schema "
                + "AND rc.table_name = kcu.table_name "
                + "AND rc.constraint_name = kcu.constraint_name "
                + "WHERE kcu.table_schema = ? AND kcu.table_name = ?";
        Map<String, ForeignKeyDefinition> foreignKeys = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, databaseName);
            statement.setString(2, TABLE_NAME);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    foreignKeys.put(resultSet.getString("constraint_name"), new ForeignKeyDefinition(
                            resultSet.getString("column_name"),
                            resultSet.getString("referenced_table_name"),
                            resultSet.getString("referenced_column_name"),
                            resultSet.getString("delete_rule")));
                }
            }
        }
        return foreignKeys;
    }

    private static ColumnDefinition required(String name, String type) {
        return new ColumnDefinition(name, type, false, null, null);
    }

    private static ColumnDefinition nullable(String name, String type) {
        return new ColumnDefinition(name, type, true, null, null);
    }

    private static ColumnDefinition requiredDefault(String name, String type, String defaultValue) {
        return new ColumnDefinition(name, type, false, defaultValue, null);
    }

    private static ColumnDefinition textRequired(String name, String type) {
        return new ColumnDefinition(name, type, false, null, "utf8mb4_unicode_ci");
    }

    private static ColumnDefinition textNullable(String name, String type) {
        return new ColumnDefinition(name, type, true, null, "utf8mb4_unicode_ci");
    }

    private record ColumnDefinition(String name,
                                    String type,
                                    boolean nullable,
                                    String defaultValue,
                                    String collation) {
    }

    private record IndexDefinition(boolean nonUnique, List<String> columns) {
    }

    private record ForeignKeyDefinition(String columnName,
                                        String referencedTableName,
                                        String referencedColumnName,
                                        String deleteRule) {
    }

    private static final class MutableIndexDefinition {
        private final boolean nonUnique;
        private final List<String> columns = new ArrayList<>();

        private MutableIndexDefinition(boolean nonUnique) {
            this.nonUnique = nonUnique;
        }
    }
}
