package com.ainovel.app.admin.ops;

import com.ainovel.app.common.SafeLogThrowable;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class OpsRecordFileSink {
    public static final String AUDIT_RECORD = "ainovel_admin_audit";
    public static final String OPS_EVENT_RECORD = "ainovel_ops_event";
    public static final String DEPENDENCY_PROBE_RECORD = "ainovel_dependency_probe";

    private static final Logger log = LoggerFactory.getLogger(OpsRecordFileSink.class);
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneOffset.UTC);
    private static final Pattern MANAGED_FILE_NAME = Pattern.compile(
            "^ainovel-(?:admin-audit|ops-events|dependency-probes)-(\\d{4}-\\d{2}-\\d{2})(?:-\\d+)?\\.ndjson$"
    );
    private static final String REDACTED = "<redacted>";
    private static final String TRUNCATED = "<truncated>";
    private static final int MAX_NESTING_DEPTH = 4;
    private static final int MAX_CONTAINER_ELEMENTS = 64;
    private static final int MAX_RECORD_ELEMENTS = 256;
    private static final int MAX_STRING_INPUT_LENGTH = 8_192;
    private static final int MAX_STRING_LENGTH = 1_024;
    private static final int MAX_FIELD_NAME_INPUT_LENGTH = 512;
    private static final int MAX_FIELD_NAME_LENGTH = 128;
    private static final int MAX_RECORD_STRING_CHARACTERS = 16_384;
    private static final Set<String> RESERVED_RECORD_FIELDS = Set.of("recordType", "recordId", "createdAt");
    private static final String SENSITIVE_NAME_PATTERN =
            "(?:authorization|cookie|cookies|access[_-]?token|refresh[_-]?token|token|password|passwd|pwd|secret|"
                    + "api[_-]?key|apikey|session[_-]?id|sessionid|"
                    + "(?:redeem|recovery|totp|verification|auth|authorization|otp|mfa|captcha|invite|reset|activation|email|sms)[_-]?code|"
                    + "code|recovery|totp)";
    private static final Pattern URL_USERINFO = Pattern.compile(
            "(?i)(\\b[a-z][a-z0-9+.-]*://)[^\\s/@]+@"
    );
    private static final Pattern SENSITIVE_ASSIGNMENT = Pattern.compile(
            "(?i)([\"']?\\b" + SENSITIVE_NAME_PATTERN + "\\b[\"']?\\s*(?:=|:))\\s*"
                    + "(?:\"[^\"]*\"|'[^']*'|(?:bearer|basic)\\s+[^\\s,;&]+|[^\\s,;&}\\]]+)"
    );
    private static final Pattern SENSITIVE_WHITESPACE = Pattern.compile(
            "(?i)(\\b" + SENSITIVE_NAME_PATTERN + "\\b)\\s+(?:(?:bearer|basic)\\s+)?[^\\s,;&]+"
    );
    private static final Pattern BEARER_VALUE = Pattern.compile("(?i)\\bbearer\\s+[^\\s,;&]+");
    private static final Pattern LINE_BREAKS = Pattern.compile("[\\r\\n]+");
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("\\p{Cntrl}");
    private static final Pattern CAMEL_CASE_BOUNDARY = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Set<String> SENSITIVE_FIELD_TOKENS = Set.of(
            "authorization", "cookie", "cookies", "token", "password", "passwd", "pwd", "secret",
            "apikey", "sessionid", "recovery", "totp"
    );
    private static final Set<String> SENSITIVE_CODE_QUALIFIERS = Set.of(
            "redeem", "recovery", "totp", "verification", "auth", "authorization", "otp", "mfa",
            "captcha", "invite", "reset", "activation", "email", "sms"
    );
    private static final Set<String> SENSITIVE_CODE_FIELD_NAMES = Set.of(
            "code", "redeemcode", "recoverycode", "totpcode", "verificationcode", "authcode",
            "authorizationcode", "otpcode", "mfacode", "captchacode", "invitecode", "resetcode",
            "activationcode", "emailcode", "smscode"
    );
    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.GROUP_EXECUTE
    );
    private static final Set<PosixFilePermission> FILE_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.GROUP_READ
    );
    private static final boolean POSIX_SUPPORTED = FileSystems.getDefault()
            .supportedFileAttributeViews()
            .contains("posix");

    private final ObjectMapper objectMapper;

    @Value("${app.records.dir:${APP_RECORD_DIR:/app/records}}")
    private String recordDir;

    @Value("${app.records.max-file-size-bytes:${APP_RECORD_MAX_FILE_SIZE_BYTES:10485760}}")
    private long maxFileSizeBytes = 10L * 1024 * 1024;

    @Value("${app.records.max-history-days:${APP_RECORD_MAX_HISTORY_DAYS:14}}")
    private int maxHistoryDays = 14;

    @Value("${app.records.max-total-size-bytes:${APP_RECORD_MAX_TOTAL_SIZE_BYTES:1073741824}}")
    private long maxTotalSizeBytes = 1024L * 1024 * 1024;

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
            doc.putAll(sanitizeMap(fields, 0, new SanitizationBudget()));
        }

        try {
            Path directory = Path.of(recordDir).toAbsolutePath().normalize();
            createSecureDirectory(directory);
            pruneSafely(directory, null, recordType, now);

            byte[] line = (objectMapper.writeValueAsString(doc) + System.lineSeparator())
                    .getBytes(StandardCharsets.UTF_8);
            Path path = resolveWritablePath(directory, filePrefix, now, line.length);
            rejectSymbolicLink(path);
            createSecureFile(path);
            Files.write(path, line, StandardOpenOption.APPEND, LinkOption.NOFOLLOW_LINKS);
            pruneSafely(directory, path, recordType, now);
        } catch (Exception ex) {
            log.error("Failed to append AINovel ops record type={} errorType={}",
                    recordType, ex.getClass().getSimpleName(), SafeLogThrowable.stackOnly(ex));
        }
    }

    private Map<String, Object> sanitizeMap(Map<?, ?> source, int depth, SanitizationBudget budget) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        int elementCount = 0;
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (elementCount >= MAX_CONTAINER_ELEMENTS || !budget.consumeElement()) {
                break;
            }
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            elementCount++;
            String rawKey = String.valueOf(entry.getKey());
            if (depth == 0 && RESERVED_RECORD_FIELDS.contains(rawKey)) {
                continue;
            }
            boolean sensitive = isSensitiveFieldName(rawKey);
            String safeKey = sanitizeString(rawKey, MAX_FIELD_NAME_LENGTH, budget);
            if (safeKey.isBlank()) {
                continue;
            }
            sanitized.put(safeKey, sensitive ? REDACTED : sanitizeValue(entry.getValue(), depth, budget));
        }
        return sanitized;
    }

    private Object sanitizeValue(Object value, int depth, SanitizationBudget budget) {
        if (value instanceof CharSequence || value instanceof Character) {
            return sanitizeString(String.valueOf(value), MAX_STRING_LENGTH, budget);
        }
        if (value instanceof Boolean
                || value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Float
                || value instanceof Double) {
            return value;
        }
        if (value instanceof Number) {
            return sanitizeString(String.valueOf(value), MAX_STRING_LENGTH, budget);
        }
        if (value instanceof Map<?, ?> map) {
            return depth >= MAX_NESTING_DEPTH ? TRUNCATED : sanitizeMap(map, depth + 1, budget);
        }
        if (value instanceof Iterable<?> iterable) {
            return depth >= MAX_NESTING_DEPTH ? TRUNCATED : sanitizeIterable(iterable, depth + 1, budget);
        }
        if (value.getClass().isArray()) {
            return depth >= MAX_NESTING_DEPTH ? TRUNCATED : sanitizeArray(value, depth + 1, budget);
        }
        return sanitizeString(String.valueOf(value), MAX_STRING_LENGTH, budget);
    }

    private List<Object> sanitizeIterable(Iterable<?> source, int depth, SanitizationBudget budget) {
        List<Object> sanitized = new ArrayList<>();
        for (Object value : source) {
            if (sanitized.size() >= MAX_CONTAINER_ELEMENTS || !budget.consumeElement()) {
                break;
            }
            sanitized.add(value == null ? null : sanitizeValue(value, depth, budget));
        }
        return sanitized;
    }

    private List<Object> sanitizeArray(Object source, int depth, SanitizationBudget budget) {
        List<Object> sanitized = new ArrayList<>();
        int length = Math.min(Array.getLength(source), MAX_CONTAINER_ELEMENTS);
        for (int index = 0; index < length && budget.consumeElement(); index++) {
            Object value = Array.get(source, index);
            sanitized.add(value == null ? null : sanitizeValue(value, depth, budget));
        }
        return sanitized;
    }

    private boolean isSensitiveFieldName(String fieldName) {
        if (fieldName.length() > MAX_FIELD_NAME_INPUT_LENGTH) {
            return true;
        }
        String separated = CAMEL_CASE_BOUNDARY.matcher(fieldName).replaceAll("_").toLowerCase(Locale.ROOT);
        String[] tokens = NON_ALPHANUMERIC.split(separated);
        String collapsed = NON_ALPHANUMERIC.matcher(separated).replaceAll("");
        if (SENSITIVE_CODE_FIELD_NAMES.contains(collapsed)) {
            return true;
        }
        boolean codeField = false;
        for (int index = 0; index < tokens.length; index++) {
            String token = tokens[index];
            if (SENSITIVE_FIELD_TOKENS.contains(token)) {
                return true;
            }
            if ("code".equals(token)) {
                codeField = true;
            }
            if (index > 0 && (("key".equals(token) && "api".equals(tokens[index - 1]))
                    || ("id".equals(token) && "session".equals(tokens[index - 1])))) {
                return true;
            }
        }
        if (codeField) {
            for (String token : tokens) {
                if (SENSITIVE_CODE_QUALIFIERS.contains(token)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String sanitizeString(String value, int maxLength, SanitizationBudget budget) {
        String sanitized = value.substring(0, Math.min(value.length(), MAX_STRING_INPUT_LENGTH));
        sanitized = LINE_BREAKS.matcher(sanitized).replaceAll("");
        sanitized = CONTROL_CHARACTERS.matcher(sanitized).replaceAll(" ");
        sanitized = URL_USERINFO.matcher(sanitized).replaceAll("$1<redacted>@");
        sanitized = SENSITIVE_ASSIGNMENT.matcher(sanitized).replaceAll("$1<redacted>");
        sanitized = SENSITIVE_WHITESPACE.matcher(sanitized).replaceAll("$1 <redacted>");
        sanitized = BEARER_VALUE.matcher(sanitized).replaceAll("Bearer <redacted>");
        sanitized = sanitized.replaceAll(" {2,}", " ").strip();
        return budget.consumeString(sanitized, maxLength);
    }

    private Path resolveWritablePath(Path directory, String filePrefix, Instant now, int recordSize) throws Exception {
        String day = DAY_FORMATTER.format(now);
        long sizeLimit = Math.max(1_024L, maxFileSizeBytes);
        for (int segment = 0; segment < 10_000; segment++) {
            String suffix = segment == 0 ? "" : "-" + segment;
            Path candidate = directory.resolve(filePrefix + day + suffix + ".ndjson");
            rejectSymbolicLink(candidate);
            if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(candidate) + recordSize <= sizeLimit) {
                return candidate;
            }
        }
        throw new IllegalStateException("AINovel ops record segment limit exceeded");
    }

    private void createSecureDirectory(Path directory) throws Exception {
        if (POSIX_SUPPORTED) {
            Files.createDirectories(directory, PosixFilePermissions.asFileAttribute(DIRECTORY_PERMISSIONS));
            Files.setPosixFilePermissions(directory, DIRECTORY_PERMISSIONS);
        } else {
            Files.createDirectories(directory);
        }
    }

    private void createSecureFile(Path path) throws Exception {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            if (POSIX_SUPPORTED) {
                Files.createFile(path, PosixFilePermissions.asFileAttribute(FILE_PERMISSIONS));
            } else {
                Files.createFile(path);
            }
        } else if (POSIX_SUPPORTED) {
            Files.setPosixFilePermissions(path, FILE_PERMISSIONS);
        }
    }

    private void rejectSymbolicLink(Path path) {
        if (Files.isSymbolicLink(path)) {
            throw new IllegalStateException("AINovel ops record path must not be a symbolic link");
        }
    }

    private void pruneSafely(Path directory, Path currentFile, String recordType, Instant now) {
        try {
            prune(directory, currentFile, now);
        } catch (Exception ex) {
            log.warn("Failed to prune AINovel ops records type={} errorType={}",
                    recordType, ex.getClass().getSimpleName(), SafeLogThrowable.stackOnly(ex));
        }
    }

    private void prune(Path directory, Path currentFile, Instant now) throws Exception {
        List<Path> files = managedRecordFiles(directory);
        FileTime cutoff = FileTime.from(now.minus(Duration.ofDays(Math.max(1, maxHistoryDays))));
        for (Path file : files) {
            if (!file.equals(currentFile) && Files.getLastModifiedTime(file).compareTo(cutoff) < 0) {
                Files.deleteIfExists(file);
            }
        }

        files = managedRecordFiles(directory);
        files.sort(Comparator.comparing(this::lastModifiedSafely));
        long totalSize = 0L;
        for (Path file : files) {
            totalSize += Files.size(file);
        }
        long totalSizeLimit = Math.max(Math.max(1_024L, maxFileSizeBytes), maxTotalSizeBytes);
        for (Path file : files) {
            if (totalSize <= totalSizeLimit) {
                break;
            }
            if (file.equals(currentFile)) {
                continue;
            }
            long size = Files.size(file);
            if (Files.deleteIfExists(file)) {
                totalSize -= size;
            }
        }
    }

    private List<Path> managedRecordFiles(Path directory) throws Exception {
        List<Path> files = new ArrayList<>();
        try (var stream = Files.list(directory)) {
            stream.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(this::isManagedRecordFile)
                    .forEach(files::add);
        }
        return files;
    }

    private boolean isManagedRecordFile(Path path) {
        Matcher matcher = MANAGED_FILE_NAME.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            return false;
        }
        try {
            LocalDate.parse(matcher.group(1), DateTimeFormatter.ISO_LOCAL_DATE);
            return true;
        } catch (DateTimeParseException ignored) {
            return false;
        }
    }

    private FileTime lastModifiedSafely(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (Exception ex) {
            log.warn("Failed to read AINovel ops record modification time file={} errorType={}",
                    path.getFileName(), ex.getClass().getSimpleName(), SafeLogThrowable.stackOnly(ex));
            return FileTime.fromMillis(0L);
        }
    }

    private static final class SanitizationBudget {
        private int remainingElements = MAX_RECORD_ELEMENTS;
        private int remainingStringCharacters = MAX_RECORD_STRING_CHARACTERS;

        private boolean consumeElement() {
            if (remainingElements <= 0) {
                return false;
            }
            remainingElements--;
            return true;
        }

        private String consumeString(String value, int maxLength) {
            if (remainingStringCharacters <= 0) {
                return TRUNCATED;
            }
            int length = Math.min(Math.min(value.length(), maxLength), remainingStringCharacters);
            remainingStringCharacters -= length;
            return value.substring(0, length);
        }
    }
}
