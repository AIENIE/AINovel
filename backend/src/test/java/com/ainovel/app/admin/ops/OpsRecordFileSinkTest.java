package com.ainovel.app.admin.ops;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsRecordFileSinkTest {

    @TempDir
    Path tempDir;

    @Test
    void appendAuditWritesNdjsonForFilebeatRecords() throws Exception {
        OpsRecordFileSink sink = sink(tempDir);

        sink.appendAudit(Map.of(
                "action", "maintenance.update",
                "actor", "admin",
                "targetType", "system-config",
                "result", "SUCCESS"
        ));

        Path[] files;
        try (var stream = Files.list(tempDir)) {
            files = stream.toArray(Path[]::new);
        }
        assertEquals(1, files.length);
        assertTrue(files[0].getFileName().toString().startsWith("ainovel-admin-audit-"));

        String line = Files.readString(files[0]);
        assertTrue(line.contains("\"recordType\":\"ainovel_admin_audit\""));
        assertTrue(line.contains("\"action\":\"maintenance.update\""));
        assertTrue(line.contains("\"recordId\":"));
        assertTrue(line.endsWith(System.lineSeparator()));
        if (Files.getFileStore(files[0]).supportsFileAttributeView("posix")) {
            assertEquals(Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.GROUP_READ
            ), Files.getPosixFilePermissions(files[0]));
        }
    }

    @Test
    void appendAuditRotatesWhenDailySegmentReachesConfiguredSize() throws Exception {
        OpsRecordFileSink sink = sink(tempDir);
        ReflectionTestUtils.setField(sink, "maxFileSizeBytes", 1_024L);
        String payload = "x".repeat(900);

        sink.appendAudit(Map.of("action", "first", "details", payload));
        sink.appendAudit(Map.of("action", "second", "details", payload));

        try (var files = Files.list(tempDir)) {
            assertEquals(2L, files.filter(path -> path.getFileName().toString().endsWith(".ndjson")).count());
        }
    }

    @Test
    void appendAuditRemovesExpiredManagedRecordFiles() throws Exception {
        OpsRecordFileSink sink = sink(tempDir);
        ReflectionTestUtils.setField(sink, "maxHistoryDays", 1);
        Path stale = tempDir.resolve("ainovel-admin-audit-2000-01-01.ndjson");
        Files.writeString(stale, "{}\n");
        Files.setLastModifiedTime(stale, FileTime.from(Instant.now().minus(3, ChronoUnit.DAYS)));

        sink.appendAudit(Map.of("action", "maintenance.update"));

        assertFalse(Files.exists(stale));
    }

    @Test
    void appendAuditDoesNotPruneAdjacentOrInvalidDateNdjsonFiles() throws Exception {
        OpsRecordFileSink sink = sink(tempDir);
        ReflectionTestUtils.setField(sink, "maxHistoryDays", 1);
        Path unrelated = tempDir.resolve("ainovel-unrelated.ndjson");
        Path invalidDate = tempDir.resolve("ainovel-admin-audit-2026-99-99.ndjson");
        Files.writeString(unrelated, "{}\n");
        Files.writeString(invalidDate, "{}\n");
        FileTime staleTime = FileTime.from(Instant.now().minus(3, ChronoUnit.DAYS));
        Files.setLastModifiedTime(unrelated, staleTime);
        Files.setLastModifiedTime(invalidDate, staleTime);

        sink.appendAudit(Map.of("action", "maintenance.update"));

        assertTrue(Files.exists(unrelated));
        assertTrue(Files.exists(invalidDate));
    }

    @Test
    @SuppressWarnings("unchecked")
    void appendAuditRedactsSensitiveFieldsAndBoundsNestedValues() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        OpsRecordFileSink sink = sink(tempDir, objectMapper);
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("sessionId", "nested-session-secret");
        nested.put("authorization", "field-authorization-secret");
        nested.put("cookie", "field-cookie-secret");
        nested.put("password", "field-password-secret");
        nested.put("secret", "field-secret-value");
        nested.put("apiKey", "field-api-key-secret");
        nested.put("recoveryCode", "field-recovery-secret");
        nested.put("totpSeed", "field-totp-secret");
        nested.put("safe", "kept");
        String details = "errorCode=REMOTE_FAILURE statusCode=503 reasonCode=UPSTREAM_TIMEOUT httpStatusCode=502 "
                + "line-one\r\nAuthorization: Bearer auth-secret; token=query-secret "
                + "https://user:pass@example.test/path?apiKey=url-secret&safe=ok "
                + "{\"password\":\"json-secret\"} " + "x".repeat(2_000);
        Map<String, Object> deep = Map.of(
                "level1", Map.of(
                        "level2", Map.of(
                                "level3", Map.of(
                                        "level4", Map.of("value", "too-deep-secret")
                                )
                        )
                )
        );
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("recordType", "forged-record-type");
        fields.put("recordId", "forged-record-id");
        fields.put("createdAt", "forged-created-at");
        fields.put("targetId", "redeem-id-42");
        fields.put("code", "plain-redeem-secret");
        fields.put("errorCode", "REMOTE_FAILURE");
        fields.put("statusCode", 503);
        fields.put("reasonCode", "UPSTREAM_TIMEOUT");
        fields.put("httpStatusCode", 502);
        fields.put("details", details);
        fields.put("nested", nested);
        fields.put("items", IntStream.range(0, 100).boxed().toList());
        fields.put("deep", deep);
        fields.put("largeValues", IntStream.range(0, 100).mapToObj(index -> "y".repeat(2_000)).toList());

        sink.appendAudit(fields);

        Path recordFile;
        try (var files = Files.list(tempDir)) {
            recordFile = files.filter(path -> path.getFileName().toString().endsWith(".ndjson"))
                    .findFirst()
                    .orElseThrow();
        }
        Map<String, Object> record = objectMapper.readValue(Files.readString(recordFile), Map.class);
        String serialized = objectMapper.writeValueAsString(record);
        assertEquals(OpsRecordFileSink.AUDIT_RECORD, record.get("recordType"));
        assertTrue(((String) record.get("recordId")).startsWith(OpsRecordFileSink.AUDIT_RECORD + ":"));
        assertFalse("forged-created-at".equals(record.get("createdAt")));
        assertEquals("redeem-id-42", record.get("targetId"));
        assertEquals("<redacted>", record.get("code"));
        assertEquals("REMOTE_FAILURE", record.get("errorCode"));
        assertEquals(503, record.get("statusCode"));
        assertEquals("UPSTREAM_TIMEOUT", record.get("reasonCode"));
        assertEquals(502, record.get("httpStatusCode"));
        Map<String, Object> sanitizedNested = (Map<String, Object>) record.get("nested");
        assertEquals("<redacted>", sanitizedNested.get("sessionId"));
        assertEquals("<redacted>", sanitizedNested.get("authorization"));
        assertEquals("<redacted>", sanitizedNested.get("cookie"));
        assertEquals("<redacted>", sanitizedNested.get("password"));
        assertEquals("<redacted>", sanitizedNested.get("secret"));
        assertEquals("<redacted>", sanitizedNested.get("apiKey"));
        assertEquals("<redacted>", sanitizedNested.get("recoveryCode"));
        assertEquals("<redacted>", sanitizedNested.get("totpSeed"));
        assertEquals("kept", sanitizedNested.get("safe"));
        assertEquals(64, ((List<Object>) record.get("items")).size());
        assertEquals(64, ((List<Object>) record.get("largeValues")).size());
        String sanitizedDetails = (String) record.get("details");
        assertTrue(sanitizedDetails.length() <= 1_024);
        assertTrue(sanitizedDetails.contains("errorCode=REMOTE_FAILURE"));
        assertTrue(sanitizedDetails.contains("statusCode=503"));
        assertTrue(sanitizedDetails.contains("reasonCode=UPSTREAM_TIMEOUT"));
        assertTrue(sanitizedDetails.contains("httpStatusCode=502"));
        assertTrue(serialized.contains("<truncated>"));
        assertTrue(Files.size(recordFile) < 40_000);
        assertFalse(sanitizedDetails.contains("\r"));
        assertFalse(sanitizedDetails.contains("\n"));
        assertFalse(serialized.contains("plain-redeem-secret"));
        assertFalse(serialized.contains("forged-record-type"));
        assertFalse(serialized.contains("forged-record-id"));
        assertFalse(serialized.contains("forged-created-at"));
        assertFalse(serialized.contains("nested-session-secret"));
        assertFalse(serialized.contains("field-authorization-secret"));
        assertFalse(serialized.contains("field-cookie-secret"));
        assertFalse(serialized.contains("field-password-secret"));
        assertFalse(serialized.contains("field-secret-value"));
        assertFalse(serialized.contains("field-api-key-secret"));
        assertFalse(serialized.contains("field-recovery-secret"));
        assertFalse(serialized.contains("field-totp-secret"));
        assertFalse(serialized.contains("too-deep-secret"));
        assertFalse(serialized.contains("auth-secret"));
        assertFalse(serialized.contains("query-secret"));
        assertFalse(serialized.contains("user:pass"));
        assertFalse(serialized.contains("url-secret"));
        assertFalse(serialized.contains("json-secret"));
    }

    @Test
    void appendFailureKeepsThrowableInApplicationLog() throws Exception {
        Path blockingFile = tempDir.resolve("not-a-directory");
        Files.writeString(blockingFile, "blocked");
        OpsRecordFileSink sink = sink(blockingFile);
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(OpsRecordFileSink.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            sink.appendAudit(Map.of("action", "maintenance.update"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertEquals(1, appender.list.size());
        assertNotNull(appender.list.get(0).getThrowableProxy());
        assertNull(appender.list.get(0).getThrowableProxy().getMessage());
        assertFalse(appender.list.get(0).getFormattedMessage().contains(blockingFile.toString()));
    }

    private OpsRecordFileSink sink(Path directory) {
        return sink(directory, new ObjectMapper());
    }

    private OpsRecordFileSink sink(Path directory, ObjectMapper objectMapper) {
        OpsRecordFileSink sink = new OpsRecordFileSink(objectMapper);
        ReflectionTestUtils.setField(sink, "recordDir", directory.toString());
        return sink;
    }
}
