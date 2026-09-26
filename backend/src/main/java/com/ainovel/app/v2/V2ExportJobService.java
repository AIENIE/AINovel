package com.ainovel.app.v2;

import com.ainovel.app.common.ApiStatusException;
import com.ainovel.app.common.BusinessException;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.user.User;
import com.ainovel.app.user.UserRepository;
import com.ainovel.app.v2.model.V2ExportJob;
import com.ainovel.app.v2.repo.V2ExportJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Service
public class V2ExportJobService {
    private static final Logger log = LoggerFactory.getLogger(V2ExportJobService.class);
    private static final Set<String> ACTIVE = Set.of("queued", "processing");
    private static final long MAX_INPUT_BYTES = 5L * 1024 * 1024;
    private static final long MAX_OUTPUT_BYTES = 25L * 1024 * 1024;
    private final V2ExportPersistenceService persistence;
    private final V2ExportJobRepository jobs;
    private final UserRepository users;
    private final V2ExportRenderer renderer;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Executor executor;
    private final Set<UUID> dispatched = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> activeExecutions = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Path, DownloadSpool> downloads = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Integer> userDownloads = new HashMap<>();
    private int downloadCount;
    @org.springframework.beans.factory.annotation.Value("${app.export.download.global-concurrency:4}")
    private int globalDownloadLimit = 4;
    @org.springframework.beans.factory.annotation.Value("${app.export.download.user-concurrency:2}")
    private int userDownloadLimit = 2;
    @org.springframework.beans.factory.annotation.Value("${app.export.download.temp-budget-bytes:104857600}")
    private long downloadTempBudgetBytes = 100L * 1024 * 1024;
    private final Path downloadDirectory = Path.of(System.getProperty("java.io.tmpdir"), "ainovel-export-downloads");

    public V2ExportJobService(V2ExportPersistenceService persistence, V2ExportJobRepository jobs,
                              UserRepository users, V2ExportRenderer renderer, JdbcTemplate jdbc,
                              TransactionTemplate transactions, @Qualifier("v2ExportExecutor") Executor executor) {
        this.persistence = persistence;
        this.jobs = jobs;
        this.users = users;
        this.renderer = renderer;
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.executor = executor;
    }

    public Map<String, Object> create(User user, Manuscript manuscript, Map<String, Object> payload) {
        Object rawFormat = payload.get("format");
        String format = (rawFormat == null ? "txt" : rawFormat.toString()).toLowerCase(Locale.ROOT);
        if (!Set.of("txt", "docx", "epub", "pdf").contains(format)) throw new BusinessException("不支持的导出格式: " + format);
        String fileName = safeFileName(manuscript.getTitle(), format);
        Map<String, Object> created = transactions.execute(status -> {
            users.findByIdForUpdate(user.getId()).orElseThrow(() -> new BusinessException("用户不存在"));
            if (jobs.countByUserIdAndStatusIn(user.getId(), ACTIVE) >= 3) {
                throw new ApiStatusException(HttpStatus.TOO_MANY_REQUESTS, "EXPORT_CONCURRENCY_LIMIT");
            }
            return persistence.createJob(user, manuscript, payload, fileName, contentType(format));
        });
        UUID id = UUID.fromString(String.valueOf(Objects.requireNonNull(created).get("id")));
        afterCommit(() -> dispatch(id));
        return created;
    }

    public List<Map<String, Object>> list(UUID manuscriptId) { return persistence.listJobs(manuscriptId); }
    public Map<String, Object> get(UUID manuscriptId, UUID jobId) { return persistence.getJob(manuscriptId, jobId); }

    public Download download(UUID manuscriptId, UUID jobId, User user) {
        V2ExportJob job = jobs.findByManuscriptIdAndId(manuscriptId, jobId)
                .orElseThrow(() -> new BusinessException("导出任务不存在"));
        if (!job.getUser().getId().equals(user.getId())) throw new BusinessException("导出任务不存在");
        if (!"completed".equals(job.getStatus()) || job.getChecksum() == null) {
            throw new ApiStatusException(HttpStatus.CONFLICT, "EXPORT_NOT_READY");
        }
        reserveDownload(user.getId());
        DownloadSpool spool = null;
        try {
            Files.createDirectories(downloadDirectory);
            spool = new DownloadSpool(Files.createTempFile(downloadDirectory, "download-" + ProcessHandle.current().pid() + "-", ".part"), user.getId());
            DownloadSpool prepared = spool;
            // Finish copying before returning the HTTP body: the connection never follows a slow client.
            jdbc.execute((ConnectionCallback<Void>) connection -> {
                try (var statement = connection.prepareStatement("SELECT content_blob FROM export_jobs WHERE id = ? AND status = 'completed'")) {
                    statement.setBytes(1, uuidBytes(jobId));
                    try (var result = statement.executeQuery()) {
                        if (!result.next()) throw new BusinessException("导出产物不存在");
                        try (InputStream input = result.getBinaryStream(1); OutputStream output = Files.newOutputStream(prepared.path)) {
                            copyBounded(input, output, MAX_OUTPUT_BYTES);
                        } catch (java.io.IOException ex) { throw new UncheckedIOException(ex); }
                    }
                }
                return null;
            });
            downloads.put(spool.path, spool);
        } catch (Exception ex) {
            if (spool != null) spool.close(); else releaseDownload(user.getId());
            throw new BusinessException("EXPORT_DOWNLOAD_PREPARATION_FAILED");
        }
        DownloadSpool ready = spool;
        StreamingResponseBody body = output -> {
            ready.begin();
            try (InputStream input = Files.newInputStream(ready.path)) { copyBounded(input, output, MAX_OUTPUT_BYTES); }
            finally { ready.close(); }
        };
        return new Download(job.getFileName(), contentType(job.getFormat()), job.getFileSizeBytes(), job.getChecksum(), body);
    }

    private synchronized void reserveDownload(UUID userId) {
        // Reserve the maximum 25 MiB per response, even before its actual size is copied.
        if (downloadCount >= Math.max(1, globalDownloadLimit)
                || userDownloads.getOrDefault(userId, 0) >= Math.max(1, userDownloadLimit)
                || (downloadCount + 1L) * MAX_OUTPUT_BYTES > downloadTempBudgetBytes)
            throw new ApiStatusException(HttpStatus.TOO_MANY_REQUESTS, "EXPORT_DOWNLOAD_CONCURRENCY_LIMIT");
        downloadCount++; userDownloads.merge(userId, 1, Integer::sum);
    }
    private synchronized void releaseDownload(UUID userId) {
        downloadCount--; userDownloads.computeIfPresent(userId, (id, count) -> count <= 1 ? null : count - 1);
    }
    static long copyBounded(InputStream input, OutputStream output, long maximum) throws java.io.IOException {
        byte[] buffer = new byte[8192]; long total = 0; int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > maximum) throw new java.io.IOException("EXPORT_OUTPUT_TOO_LARGE");
            output.write(buffer, 0, count);
        }
        return total;
    }
    @Scheduled(fixedDelay = 60000)
    public void cleanupAbandonedDownloads() {
        Instant cutoff = Instant.now().minusSeconds(600);
        downloads.values().forEach(spool -> spool.cleanupIfAbandoned(cutoff));
        if (!Files.isDirectory(downloadDirectory)) return;
        try (var files = Files.newDirectoryStream(downloadDirectory, "download-*.part")) {
            for (Path file : files) {
                if (downloads.containsKey(file) || Files.isSymbolicLink(file)) continue;
                String[] name = file.getFileName().toString().split("-",3);
                try {
                    long pid = Long.parseLong(name[1]);
                    if (ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) continue;
                    if (Files.getLastModifiedTime(file).toInstant().isBefore(cutoff)) Files.deleteIfExists(file);
                } catch (NumberFormatException ignored) { }
            }
        } catch (java.io.IOException ex) { log.warn("event=export_orphan_cleanup_failed"); }
    }
    private final class DownloadSpool implements AutoCloseable {
        final Path path; final UUID userId; final Instant createdAt = Instant.now();
        final java.util.concurrent.atomic.AtomicBoolean started = new java.util.concurrent.atomic.AtomicBoolean();
        final java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();
        boolean released;
        DownloadSpool(Path path, UUID userId) { this.path = path; this.userId = userId; }
        synchronized void begin() throws java.io.IOException {
            if (closed.get() || !started.compareAndSet(false, true)) throw new java.io.IOException("EXPORT_DOWNLOAD_EXPIRED");
        }
        synchronized void cleanupIfAbandoned(Instant cutoff) {
            if (closed.get() || (!started.get() && createdAt.isBefore(cutoff))) close();
        }
        public synchronized void close() {
            if (released) return;
            closed.set(true);
            downloads.putIfAbsent(path, this);
            try { Files.deleteIfExists(path); } catch (java.io.IOException ex) { log.warn("event=export_spool_cleanup_failed"); return; }
            released = true;
            downloads.remove(path, this);
            releaseDownload(userId);
        }
    }

    @Scheduled(fixedDelayString = "${app.export.dispatch-delay-ms:3000}")
    public void dispatchQueued() {
        recoverExpired();
        jobs.findTop100ByStatusOrderByCreatedAtAsc("queued").forEach(job -> dispatch(job.getId()));
    }

    @Scheduled(fixedDelayString = "${app.export.cleanup-delay-ms:3600000}")
    public void cleanupExpired() {
        persistence.cleanupExpiredJobs();
        try {
            jdbc.update("UPDATE export_jobs SET content_blob=NULL, snapshot_json=NULL, checksum=NULL WHERE status='expired' AND (content_blob IS NOT NULL OR snapshot_json IS NOT NULL)");
        } catch (org.springframework.dao.InvalidDataAccessResourceUsageException ex) {
            log.debug("event=export_cleanup_schema_not_ready");
        }
    }

    private void dispatch(UUID id) {
        if (!dispatched.add(id)) return;
        try {
            executor.execute(() -> { try { execute(id); } finally { dispatched.remove(id); } });
        } catch (RejectedExecutionException ex) {
            dispatched.remove(id);
        }
    }

    private void execute(UUID id) {
        String leaseOwner = UUID.randomUUID().toString();
        Claim claim = transactions.execute(status -> jobs.findByIdForUpdate(id).filter(job -> "queued".equals(job.getStatus())).map(job -> {
            if (job.getSnapshotJson() == null || job.getSnapshotJson().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_INPUT_BYTES) {
                job.setStatus("failed"); job.setErrorMessage("EXPORT_INPUT_TOO_LARGE"); job.setCompletedAt(Instant.now()); return null;
            }
            job.setStatus("processing"); job.setProgress(10); job.setStartedAt(Instant.now());
            job.setLeaseOwner(leaseOwner); job.setLeaseExpiresAt(Instant.now().plusSeconds(300));
            job.setAttemptCount(job.getAttemptCount() + 1);
            return new Claim(job.getId(), job.getFormat(), job.getSnapshotJson(), job.getConfigJson(), job.getChapterRange());
        }).orElse(null));
        if (claim == null) return;
        activeExecutions.put(id, leaseOwner);
        Path temp = null;
        try {
            temp = Files.createTempFile("ainovel-export-", "." + claim.format());
            renderer.render(claim.format(), claim.snapshotJson(), claim.configJson(), claim.chapterRange(), temp);
            long size = Files.size(temp);
            if (size <= 0 || size > MAX_OUTPUT_BYTES) throw new BusinessException("EXPORT_OUTPUT_TOO_LARGE");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream raw = Files.newInputStream(temp); DigestInputStream input = new DigestInputStream(raw, digest)) {
                input.transferTo(OutputStream.nullOutputStream());
            }
            String checksum = HexFormat.of().formatHex(digest.digest());
            Path completedFile = temp;
            try (InputStream input = Files.newInputStream(completedFile)) {
                transactions.executeWithoutResult(status -> jdbc.execute((ConnectionCallback<Void>) connection -> {
                    try (var statement = connection.prepareStatement("UPDATE export_jobs SET content_blob=?, checksum=?, file_size_bytes=?, status='completed', progress=100, completed_at=?, lease_owner=NULL, lease_expires_at=NULL WHERE id=? AND lease_owner=? AND status='processing' AND lease_expires_at>CURRENT_TIMESTAMP(6)")) {
                        statement.setBinaryStream(1, input, size);
                        statement.setString(2, checksum);
                        statement.setLong(3, size);
                        statement.setObject(4, java.sql.Timestamp.from(Instant.now()));
                        statement.setBytes(5, uuidBytes(id));
                        statement.setString(6, leaseOwner);
                        if (statement.executeUpdate() != 1) throw new IllegalStateException("Export lease lost");
                    }
                    return null;
                }));
            }
        } catch (Exception ex) {
            transactions.executeWithoutResult(status -> jobs.findByIdForUpdate(id).ifPresent(job -> {
                if (!leaseOwner.equals(job.getLeaseOwner())) return;
                job.setStatus("failed"); job.setProgress(100); job.setErrorMessage("EXPORT_FAILED");
                job.setCompletedAt(Instant.now()); job.setLeaseOwner(null); job.setLeaseExpiresAt(null);
            }));
            log.warn("event=export_failed jobId={} errorType={}", id, ex.getClass().getSimpleName());
        } finally {
            activeExecutions.remove(id, leaseOwner);
            if (temp != null) try { Files.deleteIfExists(temp); } catch (Exception ignored) { }
        }
    }

    private void recoverExpired() {
        Instant now = Instant.now();
        jobs.findByStatus("processing").stream().map(V2ExportJob::getId).forEach(id -> transactions.executeWithoutResult(status ->
                jobs.findByIdForUpdate(id).ifPresent(job -> {
                    if ("processing".equals(job.getStatus()) && job.getLeaseExpiresAt() != null && job.getLeaseExpiresAt().isBefore(now)) {
                        job.setStatus("queued"); job.setProgress(0); job.setLeaseOwner(null); job.setLeaseExpiresAt(null);
                    }
                })));
    }

    @Scheduled(fixedDelay = 30000)
    public void renewExportLeases() {
        activeExecutions.forEach((id, token) -> transactions.executeWithoutResult(status -> jobs.findByIdForUpdate(id).ifPresent(job -> {
            if (token.equals(job.getLeaseOwner()) && "processing".equals(job.getStatus()) && job.getLeaseExpiresAt() != null && job.getLeaseExpiresAt().isAfter(Instant.now()))
                job.setLeaseExpiresAt(Instant.now().plusSeconds(300));
        })));
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) { action.run(); return; }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }
    private byte[] uuidBytes(UUID id) { return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array(); }
    private String safeFileName(String title, String format) { String safe = (title == null ? "AINovel" : title).replaceAll("[\\\\/:*?\"<>|\\s]+", "_"); return (safe.isBlank() ? "AINovel" : safe) + "-" + Instant.now().toEpochMilli() + "." + format; }
    private String contentType(String format) { return switch (format) { case "txt" -> "text/plain; charset=UTF-8"; case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"; case "epub" -> "application/epub+zip"; case "pdf" -> "application/pdf"; default -> "application/octet-stream"; }; }
    private record Claim(UUID id, String format, String snapshotJson, String configJson, String chapterRange) { }
    public record Download(String fileName, String contentType, Long size, String checksum, StreamingResponseBody body) { }
}
