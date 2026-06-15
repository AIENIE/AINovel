package com.ainovel.app.admin.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsRecordFileSinkTest {

    @TempDir
    Path tempDir;

    @Test
    void appendAuditWritesNdjsonForFilebeatRecords() throws Exception {
        OpsRecordFileSink sink = new OpsRecordFileSink(new ObjectMapper());
        ReflectionTestUtils.setField(sink, "recordDir", tempDir.toString());

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
    }
}
