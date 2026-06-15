package com.ainovel.app.admin.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class OpsRecordFileSink {
    public static final String AUDIT_RECORD = "ainovel_admin_audit";
    public static final String OPS_EVENT_RECORD = "ainovel_ops_event";
    public static final String DEPENDENCY_PROBE_RECORD = "ainovel_dependency_probe";

    private static final Logger log = LoggerFactory.getLogger(OpsRecordFileSink.class);
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneOffset.UTC);

    private final ObjectMapper objectMapper;

    @Value("${app.records.dir:${APP_RECORD_DIR:/app/records}}")
    private String recordDir;

    public OpsRecordFileSink(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void appendAudit(Map<String, Object> fields) {
        append("ainovel-admin-audit-", AUDIT_RECORD, fields);
    }

    public void appendOpsEvent(Map<String, Object> fields) {
        append("ainovel-ops-events-", OPS_EVENT_RECORD, fields);
    }

    public void appendDependencyProbe(Map<String, Object> fields) {
        append("ainovel-dependency-probes-", DEPENDENCY_PROBE_RECORD, fields);
    }

    private synchronized void append(String filePrefix, String recordType, Map<String, Object> fields) {
        Instant now = Instant.now();
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("recordType", recordType);
        doc.put("recordId", recordType + ":" + UUID.randomUUID());
        doc.put("createdAt", now.toString());
        doc.put("severity", "INFO");
        doc.put("result", "SUCCESS");
        if (fields != null) {
            fields.forEach((key, value) -> {
                if (key != null && value != null) {
                    doc.put(key, value);
                }
            });
        }

        try {
            Path path = Path.of(recordDir, filePrefix + DAY_FORMATTER.format(now) + ".ndjson");
            Files.createDirectories(path.getParent());
            Files.writeString(
                    path,
                    objectMapper.writeValueAsString(doc) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (Exception ex) {
            log.warn("Failed to append AINovel ops record type={}: {}", recordType, ex.getMessage());
        }
    }
}
