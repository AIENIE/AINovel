package com.ainovel.app.config;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;

/** One-shot production Flyway runner. It never starts the web application. */
public final class ProductionNovelMigrationMain {

    private static final String COMPONENT = "ai-novel";
    private static final String LEDGER = "/app/release/migrations/flyway-ledger.json";
    private static final String LOCATION = "filesystem:/app/release/migrations/sql";
    private static final String CHECKPOINT = "/run/aienie/migration-restore/checkpoint.sql";
    private static final String TARGET = "V14";
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_]{1,64}");
    private static final Pattern NUMERIC_LITERAL = Pattern.compile(
            "[-+]?(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)(?:[eE][-+]?[0-9]+)?");
    private static final Pattern DIGEST = Pattern.compile("sha256:[0-9a-f]{64}");
    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private ProductionNovelMigrationMain() {
    }

    public static void main(String[] args) {
        PrintStream receiptOutput = System.out;
        System.setOut(System.err);
        try {
            if (args.length != 1 || !Set.of("checkpoint", "precheck", "execute", "reconcile").contains(args[0])) {
                throw new IllegalArgumentException("invalid action");
            }
            String url = required("DB_URL");
            String username = required("DB_USERNAME");
            String password = databasePassword();
            requireProductionAuthority(url, username);
            receiptOutput.print(run(args[0], url, username, password, Path.of(LEDGER), LOCATION, Path.of(CHECKPOINT)));
        } catch (Exception error) {
            System.err.println("AINovel one-shot migration rejected");
            System.exit(70);
        }
    }

    static String runForTest(
            String action,
            String url,
            String username,
            String password,
            Path ledger,
            String location,
            Path checkpoint
    ) throws Exception {
        return run(action, url, username, password, ledger, location, checkpoint);
    }

    static void validateCurrentFromEnvironment() throws Exception {
        String url = required("DB_URL");
        String username = required("DB_USERNAME");
        requireProductionAuthority(url, username);
        run("reconcile", url, username, databasePassword(), Path.of(LEDGER), LOCATION, Path.of(CHECKPOINT));
    }

    private static String run(
            String action,
            String url,
            String username,
            String password,
            Path ledger,
            String location,
            Path checkpoint
    ) throws Exception {
        if (!Set.of("checkpoint", "precheck", "execute", "reconcile").contains(action)) {
            throw new IllegalArgumentException("invalid action");
        }
        MigrationLedger authority = loadLedger(ledger, location);
        if ("checkpoint".equals(action)) {
            try (Connection connection = DriverManager.getConnection(url, username, password)) {
                writeCheckpoint(connection, checkpoint);
            }
            return receipt(action, "checkpoint-created", "none", "none", 0, 0, 0, false);
        }
        Flyway flyway = Flyway.configure()
                .dataSource(url, username, password)
                .locations(location)
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .load();
        int pendingBefore = flyway.info().pending().length;
        if ("precheck".equals(action)) {
            if (pendingBefore == 0) {
                flyway.validate();
            }
            return receipt(action, pendingBefore == 0 ? "current" : "pending", TARGET,
                    authority.checksum(), pendingBefore, pendingBefore, 0, false);
        }
        if ("reconcile".equals(action)) {
            if (pendingBefore != 0) {
                throw new IllegalStateException("Flyway migration remains pending");
            }
            flyway.validate();
            return receipt(action, "current", TARGET, authority.checksum(), 0, 0, 0, false);
        }
        int applied = pendingBefore == 0 ? 0 : flyway.migrate().migrationsExecuted;
        int pendingAfter = flyway.info().pending().length;
        flyway.validate();
        if (pendingAfter != 0 || applied != pendingBefore) {
            throw new IllegalStateException("Flyway migration count drifted");
        }
        return receipt(action, "current", TARGET, authority.checksum(), pendingBefore, 0,
                applied, applied > 0);
    }

    private static MigrationLedger loadLedger(Path ledger, String location) throws Exception {
        if (!ledger.isAbsolute() || !ledger.normalize().equals(ledger) || !Files.isRegularFile(ledger)
                || Files.isSymbolicLink(ledger)) {
            throw new IllegalStateException("Flyway ledger path is invalid");
        }
        byte[] raw = Files.readAllBytes(ledger);
        JsonNode value = JSON.readTree(raw);
        requireFields(value, Set.of(
                "authorization", "canonical_component_id", "latest_version", "location",
                "migrations", "schema_version"));
        if (!"aienie-production-flyway-ledger-v2".equals(value.path("schema_version").textValue())
                || !COMPONENT.equals(value.path("canonical_component_id").textValue())
                || !"14".equals(value.path("latest_version").textValue())
                || !location.equals(value.path("location").textValue())
                || !value.path("migrations").isArray()
                || value.path("migrations").size() != 14) {
            throw new IllegalStateException("Flyway ledger identity drifted");
        }
        JsonNode authorization = value.path("authorization");
        requireFields(authorization, Set.of(
                "minimum_release_manifest_version", "outer_signature_required", "restore_point_required_before_execute"));
        if (authorization.path("minimum_release_manifest_version").intValue() != 4
                || !authorization.path("outer_signature_required").booleanValue()
                || !authorization.path("restore_point_required_before_execute").booleanValue()) {
            throw new IllegalStateException("Flyway ledger authority drifted");
        }
        Path sqlRoot = ledger.getParent().resolve("sql").normalize();
        for (int index = 0; index < 14; index++) {
            JsonNode migration = value.path("migrations").get(index);
            requireFields(migration, Set.of("path", "sha256", "version"));
            String version = Integer.toString(index + 1);
            String relative = migration.path("path").textValue();
            String checksum = migration.path("sha256").textValue();
            if (!version.equals(migration.path("version").textValue())
                    || relative == null
                    || !relative.matches("release/migrations/sql/V" + version + "__[A-Za-z0-9_.-]+\\.sql")
                    || checksum == null
                    || !DIGEST.matcher(checksum).matches()) {
                throw new IllegalStateException("Flyway ledger entry drifted");
            }
            Path sql = ledger.getParent().getParent().getParent()
                    .resolve(relative).normalize();
            if (!sql.startsWith(sqlRoot) || !Files.isRegularFile(sql) || Files.isSymbolicLink(sql)
                    || !checksum.equals(sha256(Files.readAllBytes(sql)))) {
                throw new IllegalStateException("Flyway SQL digest drifted");
            }
        }
        return new MigrationLedger(sha256(raw));
    }

    private static void requireFields(JsonNode value, Set<String> expected) {
        Set<String> observed = new TreeSet<>();
        if (value != null) {
            value.fieldNames().forEachRemaining(observed::add);
        }
        if (value == null || !value.isObject() || !observed.equals(new TreeSet<>(expected))) {
            throw new IllegalStateException("migration JSON field closure drifted");
        }
    }

    private static void requireProductionAuthority(String url, String username) {
        if ("root".equalsIgnoreCase(username) || "ainovel".equalsIgnoreCase(username)
                || !url.startsWith("jdbc:mysql://")) {
            throw new IllegalStateException("production database principal is invalid");
        }
        URI uri = URI.create(url.substring(5));
        if (!"mysql".equalsIgnoreCase(uri.getScheme())
                || !"base.seekerhut.com".equalsIgnoreCase(uri.getHost())
                || uri.getPort() != 13306
                || !"/ainovel".equals(uri.getPath())
                || !hasOnlyQueryValue(uri.getRawQuery(), "sslMode", "VERIFY_IDENTITY")
                || !hasOnlyQueryValue(uri.getRawQuery(), "allowPublicKeyRetrieval", "false")) {
            throw new IllegalStateException("production database endpoint drifted");
        }
    }

    private static boolean hasOnlyQueryValue(String query, String key, String expected) {
        if (query == null) {
            return false;
        }
        boolean found = false;
        for (String item : query.split("&", -1)) {
            int equals = item.indexOf('=');
            if (equals <= 0 || !key.equalsIgnoreCase(item.substring(0, equals))) {
                continue;
            }
            if (!expected.equalsIgnoreCase(item.substring(equals + 1)) || found) {
                return false;
            }
            found = true;
        }
        return found;
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank() || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalStateException("required environment value is unavailable");
        }
        return value;
    }

    private static String databasePassword() {
        String value = System.getenv("DB_PASSWORD");
        if (value == null || value.isBlank()) {
            value = System.getenv("MYSQL_PASSWORD");
        }
        if (value == null || value.isBlank() || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalStateException("database password is unavailable");
        }
        return value;
    }

    private static String sha256(byte[] raw) throws Exception {
        return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
    }

    private static String receipt(
            String action,
            String status,
            String currentId,
            String checksum,
            int pendingBefore,
            int pendingAfter,
            int applied,
            boolean mutationPerformed
    ) {
        return "{\"action\":\"" + action + "\",\"applied_entry_count\":" + applied
                + ",\"canonical_component_id\":\"" + COMPONENT + "\",\"current_id\":\"" + currentId
                + "\",\"migration_checksum_sha256\":\"" + checksum
                + "\",\"mutation_performed\":" + mutationPerformed
                + ",\"pending_entry_count_after\":" + pendingAfter
                + ",\"pending_entry_count_before\":" + pendingBefore
                + ",\"schema_version\":\"aienie-production-migration-database-result-v1\",\"status\":\""
                + status + "\"}";
    }

    static void writeCheckpoint(Connection connection, Path output) throws SQLException, IOException {
        if (!output.isAbsolute() || !output.normalize().equals(output)
                || Files.exists(output) || Files.isSymbolicLink(output)) {
            throw new IllegalStateException("checkpoint path is unavailable");
        }
        Path parent = output.getParent();
        if (!Files.isDirectory(parent) || Files.isSymbolicLink(parent)) {
            throw new IllegalStateException("checkpoint directory is unavailable");
        }
        ensureNoUncapturedObjects(connection);
        connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        connection.setReadOnly(true);
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET TRANSACTION READ ONLY");
            statement.execute("START TRANSACTION WITH CONSISTENT SNAPSHOT");
        }
        Path temporary = parent.resolve(".checkpoint.sql.tmp");
        if (Files.exists(temporary) || Files.isSymbolicLink(temporary)) {
            throw new IllegalStateException("checkpoint temporary path is unavailable");
        }
        try {
            Files.createFile(temporary);
            try {
                Files.setPosixFilePermissions(temporary, EnumSet.of(
                        PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            } catch (UnsupportedOperationException ignored) {
                // Production is Linux; CREATE_NEW still protects tests on other filesystems.
            }
            try (BufferedWriter writer = Files.newBufferedWriter(
                    temporary, StandardCharsets.UTF_8, StandardOpenOption.WRITE)) {
                writer.write("-- aienie-consistent-mysql-restore-point-v1\n");
                writer.write("SET NAMES utf8mb4;\nSET FOREIGN_KEY_CHECKS=0;\n");
                writer.write("SET SESSION group_concat_max_len=1048576;\n");
                writer.write("SET @aienie_tables=(SELECT GROUP_CONCAT(CONCAT('`',REPLACE(table_name,'`','``'),'`') SEPARATOR ',') FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE');\n");
                writer.write("SET @aienie_drop=IF(@aienie_tables IS NULL,'SELECT 1',CONCAT('DROP TABLE ',@aienie_tables));\n");
                writer.write("PREPARE aienie_drop_statement FROM @aienie_drop;\nEXECUTE aienie_drop_statement;\nDEALLOCATE PREPARE aienie_drop_statement;\n");
                for (String table : tableNames(connection)) {
                    writeTable(connection, writer, table);
                }
                writer.write("SET FOREIGN_KEY_CHECKS=1;\n");
            }
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.READ)) {
                channel.force(true);
            }
            Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE);
            try (FileChannel directory = FileChannel.open(parent, StandardOpenOption.READ)) {
                directory.force(true);
            }
        } finally {
            connection.rollback();
            Files.deleteIfExists(temporary);
        }
    }

    private static void ensureNoUncapturedObjects(Connection connection) throws SQLException {
        String sql = """
                SELECT
                  (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type<>'BASE TABLE')
                + (SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE())
                + (SELECT COUNT(*) FROM information_schema.routines WHERE routine_schema=DATABASE())
                + (SELECT COUNT(*) FROM information_schema.events WHERE event_schema=DATABASE())
                """;
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            if (!result.next() || result.getLong(1) != 0 || result.next()) {
                throw new IllegalStateException("database contains uncaptured schema objects");
            }
        }
    }

    private static List<String> tableNames(Connection connection) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("""
                SELECT table_name, engine FROM information_schema.tables
                 WHERE table_schema=DATABASE() AND table_type='BASE TABLE' ORDER BY table_name
                """)) {
            while (result.next()) {
                String table = result.getString(1);
                String engine = result.getString(2);
                if (!IDENTIFIER.matcher(table).matches() || !"InnoDB".equalsIgnoreCase(engine)) {
                    throw new IllegalStateException("database table is outside checkpoint policy");
                }
                values.add(table);
            }
        }
        return values;
    }

    private static void writeTable(Connection connection, BufferedWriter writer, String table)
            throws SQLException, IOException {
        String quoted = "`" + table + "`";
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SHOW CREATE TABLE " + quoted)) {
            if (!result.next()) {
                throw new IllegalStateException("table DDL is unavailable");
            }
            writer.write("DROP TABLE IF EXISTS " + quoted + ";\n");
            writer.write(result.getString(2));
            writer.write(";\n");
        }
        List<String> columns = insertableColumns(connection, table);
        if (columns.isEmpty()) {
            return;
        }
        String columnList = columns.stream().map(value -> "`" + value + "`")
                .reduce((left, right) -> left + "," + right).orElseThrow();
        try (Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            statement.setFetchSize(Integer.MIN_VALUE);
            try (ResultSet result = statement.executeQuery("SELECT " + columnList + " FROM " + quoted)) {
                ResultSetMetaData metadata = result.getMetaData();
                while (result.next()) {
                    writer.write("INSERT INTO " + quoted + " (" + columnList + ") VALUES (");
                    for (int index = 1; index <= columns.size(); index++) {
                        if (index > 1) {
                            writer.write(',');
                        }
                        writeValue(result, metadata.getColumnType(index), index, writer);
                    }
                    writer.write(");\n");
                }
            }
        }
    }

    private static void writeValue(ResultSet result, int jdbcType, int index, BufferedWriter writer)
            throws SQLException, IOException {
        switch (jdbcType) {
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT,
                    Types.REAL, Types.FLOAT, Types.DOUBLE, Types.NUMERIC, Types.DECIMAL -> {
                String value = result.getString(index);
                if (result.wasNull()) {
                    writer.write("NULL");
                } else if (!NUMERIC_LITERAL.matcher(value).matches()) {
                    throw new IllegalStateException("numeric checkpoint value is outside SQL literal policy");
                } else {
                    writer.write(value);
                }
            }
            case Types.BOOLEAN -> {
                boolean value = result.getBoolean(index);
                writer.write(result.wasNull() ? "NULL" : value ? "1" : "0");
            }
            case Types.BIT, Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB ->
                    writeHexBytes(result, index, writer, false);
            case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR,
                    Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR,
                    Types.CLOB, Types.NCLOB,
                    Types.DATE, Types.TIME, Types.TIME_WITH_TIMEZONE,
                    Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE ->
                    writeHexBytes(result, index, writer, true);
            default -> throw new IllegalStateException("checkpoint column type is outside restore policy");
        }
    }

    private static void writeHexBytes(ResultSet result, int index, BufferedWriter writer, boolean decodeUtf8)
            throws SQLException, IOException {
        byte[] value = result.getBytes(index);
        if (result.wasNull()) {
            writer.write("NULL");
            return;
        }
        if (decodeUtf8) {
            writer.write("CONVERT(");
        }
        writer.write("X'");
        writer.write(HexFormat.of().formatHex(value));
        writer.write('\'');
        if (decodeUtf8) {
            writer.write(" USING utf8mb4)");
        }
    }

    private static List<String> insertableColumns(Connection connection, String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT column_name FROM information_schema.columns
                 WHERE table_schema=DATABASE() AND table_name=?
                   AND extra NOT LIKE '%GENERATED%'
                 ORDER BY ordinal_position
                """)) {
            statement.setString(1, table);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String value = result.getString(1);
                    if (!IDENTIFIER.matcher(value).matches()) {
                        throw new IllegalStateException("database column is outside checkpoint policy");
                    }
                    columns.add(value);
                }
            }
        }
        return columns;
    }

    private record MigrationLedger(String checksum) {
    }
}
