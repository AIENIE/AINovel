package com.ainovel.app.material;

import com.ainovel.app.common.ApiStatusException;
import com.ainovel.app.material.repo.MaterialRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import static com.ainovel.app.material.MaterialFingerprintService.*;

@Service
public class MaterialDuplicateService {
    public record Job(UUID id, String status, int comparisons, boolean incomplete, String reason) {}
    public record Candidate(UUID sourceMaterialId, UUID targetMaterialId, String sourceTitle, String targetTitle,
                            long sourceVersion, long targetVersion, double score, List<String> reasons) {}
    public record ResultPage(List<Candidate> items, int page, int size, long total, boolean incomplete) {}
    private record Fingerprint(UUID id, long version, String title, Set<String> terms) {}
    private final JdbcTemplate jdbc;
    private final MaterialRepository materials;
    private final MaterialFingerprintService fingerprints;
    private final TransactionTemplate tx;
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1), r -> { Thread t = new Thread(r, "material-duplicates"); t.setDaemon(true); return t; });
    private final int candidateLimit;
    private final int pairLimit;
    @Value("${app.material.duplicates.dispatch-enabled:true}")
    private boolean dispatchEnabled = true;
    public MaterialDuplicateService(JdbcTemplate jdbc, MaterialRepository materials, MaterialFingerprintService fingerprints,
                                    TransactionTemplate tx,
                                    @Value("${app.material.duplicates.candidate-limit:50}") int candidateLimit,
                                    @Value("${app.material.duplicates.pair-limit:100000}") int pairLimit) {
        this.jdbc = jdbc; this.materials = materials; this.fingerprints = fingerprints; this.tx = tx;
        this.candidateLimit = Math.max(1, Math.min(50, candidateLimit)); this.pairLimit = Math.max(1, Math.min(100000, pairLimit));
    }
    public Job start() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into material_duplicate_jobs(id,status,created_at,updated_at) values(?,'queued',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", id.toString());
        return status(id);
    }
    @Scheduled(fixedDelayString = "${app.material.duplicates.poll-ms:5000}")
    public void dispatch() {
        if (!dispatchEnabled) return;
        if (worker.getActiveCount() > 0 || !worker.getQueue().isEmpty()) return;
        // A lost process cannot leave an apparently successful or indefinitely running job.
        jdbc.update("update material_duplicate_jobs set status='failed',incomplete=1,reason='WORKER_LEASE_EXPIRED' where status='running' and updated_at < ?",
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(600)));
        var ids = jdbc.queryForList("select id from material_duplicate_jobs where status='queued' order by created_at,id limit 1", String.class);
        if (ids.isEmpty()) return;
        String id = ids.get(0), token = UUID.randomUUID().toString();
        if (jdbc.update("update material_duplicate_jobs set status='running',execution_token=?,updated_at=CURRENT_TIMESTAMP where id=? and status='queued'", token, id) != 1) return;
        try { worker.execute(() -> run(id, token)); }
        catch (RejectedExecutionException ex) { jdbc.update("update material_duplicate_jobs set status='queued' where id=? and execution_token=? and status='running'", id, token); }
    }
    public Job status(UUID id) {
        return jdbc.query("select * from material_duplicate_jobs where id=?", (r, n) -> new Job(id, r.getString("status"), r.getInt("comparisons"), r.getBoolean("incomplete"), r.getString("reason")), id.toString())
                .stream().findFirst().orElseThrow(() -> new ApiStatusException(HttpStatus.NOT_FOUND, "DUPLICATE_JOB_NOT_FOUND"));
    }
    public Job cancel(UUID id) {
        jdbc.update("update material_duplicate_jobs set status='cancelled',incomplete=1,reason='CANCELLED',updated_at=CURRENT_TIMESTAMP where id=? and status in ('queued','running')", id.toString());
        return status(id);
    }
    public ResultPage results(UUID id, int page, int size) {
        if (page < 0 || page > 100000 || size < 1 || size > 100) throw new ApiStatusException(HttpStatus.BAD_REQUEST, "INVALID_PAGE");
        Job job = status(id);
        String join = " from material_duplicate_results r join materials a on a.id=r.source_id and a.content_version=r.source_version join materials b on b.id=r.target_id and b.content_version=r.target_version where r.job_id=? and a.status <> 'rejected' and b.status <> 'rejected'";
        var items = jdbc.query("select r.*,a.title source_title,b.title target_title" + join + " order by r.score desc,r.source_id,r.target_id limit ? offset ?",
                (r, n) -> new Candidate(uuid(r.getBytes("source_id")), uuid(r.getBytes("target_id")), r.getString("source_title"), r.getString("target_title"),
                        r.getLong("source_version"), r.getLong("target_version"), r.getDouble("score"), List.of("lexical-fingerprint-v1")), id.toString(), size, page * size);
        long total = Objects.requireNonNull(jdbc.queryForObject("select count(*)" + join, Long.class, id.toString()));
        return new ResultPage(items, page, size, total, job.incomplete() || !"completed".equals(job.status()));
    }
    private boolean alive(String id, String token, int count) {
        return jdbc.update("update material_duplicate_jobs set comparisons=?,updated_at=CURRENT_TIMESTAMP where id=? and execution_token=? and status='running'", count, id, token) == 1;
    }
    private List<Fingerprint> read(String sql, Object... args) {
        return jdbc.query(sql, (r, n) -> new Fingerprint(uuid(r.getBytes("material_id")), r.getLong("content_version"), r.getString("title"), fingerprints.decode(r.getString("terms_json"))), args);
    }
    private void run(String id, String token) {
        int comparisons = 0;
        boolean incomplete = false;
        try {
            // Backfill is bounded and resumable. Lock each material before reading its current version.
            while (true) {
                if (!alive(id, token, comparisons)) return;
                var missing = jdbc.query("select m.id from materials m left join material_duplicate_fingerprints f on f.material_id=m.id and f.content_version=m.content_version where m.status <> 'rejected' and f.material_id is null order by m.id limit 100",
                        (r, n) -> uuid(r.getBytes(1)));
                if (missing.isEmpty()) break;
                for (UUID materialId : missing) {
                    if (!alive(id, token, comparisons)) return;
                    tx.executeWithoutResult(s -> materials.findByIdForUpdate(materialId).ifPresent(fingerprints::update));
                }
            }
            byte[] cursor = new byte[16];
            boolean done = false;
            while (!done) {
                if (!alive(id, token, comparisons)) return;
                var batch = read("select f.* from material_duplicate_fingerprints f join materials m on m.id=f.material_id and m.content_version=f.content_version where f.material_id>? and m.status <> 'rejected' order by f.material_id limit 100", cursor);
                if (batch.isEmpty()) break;
                for (Fingerprint left : batch) {
                    if (!alive(id, token, comparisons)) return;
                    cursor = bytes(left.id());
                    if (left.terms().isEmpty()) continue;
                    String slots = String.join(",", Collections.nCopies(left.terms().size(), "?"));
                    List<Object> args = new ArrayList<>(left.terms()); args.add(bytes(left.id())); args.add(candidateLimit + 1);
                    var candidates = jdbc.query("select t.material_id,count(*) hits from material_duplicate_terms t join materials m on m.id=t.material_id and m.content_version=t.content_version where t.term in (" + slots + ") and t.material_id>? and m.status <> 'rejected' group by t.material_id order by hits desc,t.material_id limit ?",
                            (r, n) -> uuid(r.getBytes("material_id")), args.toArray());
                    if (candidates.size() > candidateLimit) incomplete = true;
                    for (UUID target : candidates.stream().limit(candidateLimit).toList()) {
                        if (comparisons >= pairLimit) { incomplete = true; done = true; break; }
                        if (!alive(id, token, ++comparisons)) return;
                        var rightRows = read("select * from material_duplicate_fingerprints where material_id=?", bytes(target));
                        if (rightRows.isEmpty()) continue;
                        var right = rightRows.get(0);
                        var intersection = new HashSet<>(left.terms()); intersection.retainAll(right.terms());
                        var union = new HashSet<>(left.terms()); union.addAll(right.terms());
                        double score = union.isEmpty() ? 0 : (double) intersection.size() / union.size();
                        if (score < 0.5) continue;
                        jdbc.update("insert into material_duplicate_results(job_id,source_id,target_id,source_version,target_version,score) select ?,?,?,?,?,? from material_duplicate_jobs j join materials a on a.id=? and a.content_version=? join materials b on b.id=? and b.content_version=? where j.id=? and j.execution_token=? and j.status='running'",
                                id, bytes(left.id()), bytes(right.id()), left.version(), right.version(), score,
                                bytes(left.id()), left.version(), bytes(right.id()), right.version(), id, token);
                    }
                    if (done) break;
                }
            }
            jdbc.update("update material_duplicate_jobs set status='completed',comparisons=?,incomplete=?,reason=?,updated_at=CURRENT_TIMESTAMP where id=? and execution_token=? and status='running'",
                    comparisons, incomplete, incomplete ? "CANDIDATE_OR_PAIR_BUDGET_REACHED" : null, id, token);
        } catch (RuntimeException ex) {
            jdbc.update("update material_duplicate_jobs set status='failed',incomplete=1,reason='DUPLICATE_SCAN_FAILED',updated_at=CURRENT_TIMESTAMP where id=? and execution_token=? and status='running'", id, token);
        }
    }
    @PreDestroy void close() { worker.shutdownNow(); }
}
