package com.ainovel.app.material.evidence;

import static com.ainovel.app.material.MaterialFingerprintService.bytes;
import static com.ainovel.app.material.MaterialFingerprintService.uuid;
import static com.ainovel.app.material.evidence.MaterialEvidenceService.error;

import com.ainovel.app.material.*;
import com.ainovel.app.user.User;
import com.ainovel.app.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

/** Projection jobs never hold a database transaction while calling a provider or Qdrant. */
@Service
public class MaterialSemanticService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final MaterialNativeClient client;
    private final MaterialEvidenceService sources;
    private final UserRepository users;
    private final Map<String, QdrantMaterialVectorIndex> indexes = new HashMap<>();
    private final boolean enabled;
    private final ObjectMapper json;
    private final java.util.concurrent.ExecutorService worker =
            java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
    private final java.util.concurrent.atomic.AtomicBoolean working =
            new java.util.concurrent.atomic.AtomicBoolean();

    public MaterialSemanticService(
            JdbcTemplate jdbc,
            TransactionTemplate transactions,
            MaterialNativeClient client,
            MaterialEvidenceService sources,
            UserRepository users,
            ObjectMapper json,
            @Value("${app.material-evidence.semantic-enabled:false}") boolean enabled,
            @Value("${qdrant.host:http://localqdrant.testhut.top}") String host,
            @Value("${qdrant.http-port:26333}") int port,
            @Value("${qdrant.api-key:}") String key) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.client = client;
        this.sources = sources;
        this.users = users;
        this.enabled = enabled;
        this.json = json;
        for (String id : List.of("qwen-standard-1024-cp-v1", "qwen-flash-1024-cp-v1")) {
            var profile = MaterialNativeClient.profile(id);
            indexes.put(
                    id,
                    new QdrantMaterialVectorIndex(
                            json, host, port, profile.collection(), key, 2000, 5000, true));
        }
    }

    public boolean enabled() {
        return enabled;
    }

    /** Requeue the original identity only. The gateway decides whether a result can be replayed. */
    public EvidenceDtos.SemanticResume resume(
            User user, String revision, String profile, EvidenceDtos.SemanticResumeWrite request) {
        UUID.fromString(revision);
        MaterialNativeClient.profile(profile);
        return transactions.execute(
                tx -> {
                    jdbc.queryForObject(
                            "select id from users where id=? for update",
                            byte[].class,
                            bytes(user.getId()));
                    String scope = "semantic:" + revision + ":" + profile;
                    String hash = EvidenceText.hash(encode(request));
                    var receipts =
                            jdbc.queryForList(
                                    "select request_hash,result_json from"
                                        + " material_mutation_receipts where owner_id=? and scope=?"
                                        + " and request_key=? for update",
                                    bytes(user.getId()),
                                    scope,
                                    request.requestKey());
                    if (!receipts.isEmpty()) {
                        if (!hash.equals(receipts.getFirst().get("request_hash")))
                            throw error(
                                    org.springframework.http.HttpStatus.CONFLICT,
                                    "MATERIAL_REQUEST_CONFLICT");
                        try {
                            return json.readValue(
                                    (String) receipts.getFirst().get("result_json"),
                                    EvidenceDtos.SemanticResume.class);
                        } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
                            throw new IllegalStateException("Semantic receipt invalid", invalid);
                        }
                    }
                    if (!enabled)
                        throw error(
                                org.springframework.http.HttpStatus.CONFLICT,
                                "SEMANTIC_NOT_ENABLED");
                    var rows =
                            jdbc.queryForList(
                                    "select"
                                        + " j.status,j.gateway_user_id,j.evaluation_run_id,r.content_version"
                                        + " from material_semantic_jobs j join material_revisions r"
                                        + " on r.id=j.revision_id join materials m on"
                                        + " m.id=r.material_id and"
                                        + " m.content_version=r.content_version where"
                                        + " j.revision_id=? and j.profile=? and"
                                        + " j.request_owner_id=? and m.status='approved' and"
                                        + " (m.user_id=? or m.user_id is null) for update",
                                    revision,
                                    profile,
                                    bytes(user.getId()),
                                    bytes(user.getId()));
                    if (rows.isEmpty())
                        throw error(
                                org.springframework.http.HttpStatus.NOT_FOUND,
                                "SEMANTIC_JOB_UNAVAILABLE");
                    var row = rows.getFirst();
                    if (((Number) row.get("content_version")).longValue()
                            != request.expectedVersion())
                        throw error(
                                org.springframework.http.HttpStatus.CONFLICT,
                                "SOURCE_VERSION_CONFLICT");
                    if (!"RECONCILIATION_REQUIRED".equals(row.get("status")))
                        throw error(
                                org.springframework.http.HttpStatus.CONFLICT,
                                "SEMANTIC_JOB_NOT_RECONCILIATION");
                    if (row.get("gateway_user_id") == null || row.get("evaluation_run_id") == null)
                        throw error(
                                org.springframework.http.HttpStatus.CONFLICT,
                                "GATEWAY_REQUEST_IDENTITY_UNAVAILABLE");
                    User current = users.findById(user.getId()).orElseThrow();
                    if (current.getRemoteUid() == null
                            || current.getRemoteUid().longValue()
                                    != ((Number) row.get("gateway_user_id")).longValue())
                        throw error(
                                org.springframework.http.HttpStatus.CONFLICT,
                                "GATEWAY_REQUEST_IDENTITY_CHANGED");
                    jdbc.update(
                            "update material_semantic_jobs set"
                                + " status='QUEUED',error_code=null,lease_token=null,lease_until=null,budget_waits=0,not_before=null"
                                + " where revision_id=? and profile=?",
                            revision,
                            profile);
                    var result = new EvidenceDtos.SemanticResume(revision, profile, "QUEUED");
                    jdbc.update(
                            "insert into"
                                + " material_mutation_receipts(owner_id,scope,request_key,request_hash,result_json)"
                                + " values(?,?,?,?,?)",
                            bytes(user.getId()),
                            scope,
                            request.requestKey(),
                            hash,
                            encode(result));
                    return result;
                });
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
            throw new IllegalArgumentException("Semantic recovery request invalid", invalid);
        }
    }

    public List<VectorMatch> search(
            User user, String request, String profile, String query, List<UUID> allowed) {
        if (!enabled || allowed.isEmpty()) return List.of();
        if (allowed.size() > 500) throw new IllegalStateException("SEMANTIC_SCOPE_LIMIT");
        var selected = MaterialNativeClient.profile(profile);
        var vector = client.embed(user, request, selected, List.of(query), "query").getFirst();
        return indexes.get(profile).searchWithin(vector, 40, user.getId(), allowed);
    }

    @Scheduled(fixedDelayString = "${app.material-evidence.dispatch-ms:5000}")
    public void dispatch() {
        if (!working.compareAndSet(false, true)) return;
        worker.submit(
                () -> {
                    try {
                        dispatchOne();
                    } catch (RuntimeException failure) {
                        org.slf4j.LoggerFactory.getLogger(getClass())
                                .warn(
                                        "event=material_projection_dispatch_failed errorType={}",
                                        failure.getClass().getSimpleName());
                    } finally {
                        working.set(false);
                    }
                });
    }

    @jakarta.annotation.PreDestroy
    void stopWorker() {
        worker.close();
    }

    private void dispatchOne() {
        for (int i = 0; i < 10; i++) sources.backfill(1);
        if (!enabled) return;
        transactions.executeWithoutResult(
                tx -> {
                    jdbc.update(
                            "insert ignore into"
                                + " material_semantic_jobs(revision_id,profile,model,dimensions,template_version,chunk_version)"
                                + " select distinct r.id,w.semantic_profile,case when"
                                + " w.semantic_profile='qwen-standard-1024-cp-v1' then"
                                + " 'qwen3.7-text-embedding' else 'qwen3.7-text-embedding-flash'"
                                + " end,1024,'document-v1','cp900-120-v1' from material_revisions r"
                                + " join materials m on m.id=r.material_id and"
                                + " m.content_version=r.content_version join material_work_bindings"
                                + " b on b.material_id=m.id join material_work_settings w on"
                                + " w.story_id=b.story_id where m.status='approved' and"
                                + " w.semantic_profile in"
                                + " ('qwen-standard-1024-cp-v1','qwen-flash-1024-cp-v1')");
                    jdbc.update(
                            "update material_semantic_jobs set"
                                + " status='RECONCILIATION_REQUIRED',error_code='INDEX_LEASE_EXPIRED'"
                                + " where status='PROCESSING' and"
                                + " lease_until<CURRENT_TIMESTAMP(6)");
                });
        String token = UUID.randomUUID().toString();
        var claim =
                transactions.execute(
                        tx -> {
                            var rows =
                                    jdbc.queryForList(
                                            "select"
                                                + " j.revision_id,j.profile,b.story_id,s.user_id,j.gateway_user_id,j.evaluation_run_id,j.budget_waits"
                                                + " from material_semantic_jobs j join"
                                                + " material_revisions r on r.id=j.revision_id join"
                                                + " material_basic_jobs base on"
                                                + " base.revision_id=r.id and"
                                                + " base.status='COMPLETED' join materials m on"
                                                + " m.id=r.material_id and"
                                                + " m.content_version=r.content_version join"
                                                + " material_work_bindings b on b.material_id=m.id"
                                                + " join material_work_settings w on"
                                                + " w.story_id=b.story_id and"
                                                + " w.semantic_profile=j.profile join stories s on"
                                                + " s.id=b.story_id where j.status='QUEUED' and"
                                                + " (j.not_before is null or"
                                                + " j.not_before<=CURRENT_TIMESTAMP(6)) and"
                                                + " m.status='approved' and (j.request_owner_id is"
                                                + " null or j.request_owner_id=s.user_id) order by"
                                                + " r.created_at,j.profile limit 1 for update skip"
                                                + " locked");
                            if (rows.isEmpty()) return null;
                            var row = rows.getFirst();
                            if (row.get("gateway_user_id") == null) {
                                User owner =
                                        users.findById(uuid((byte[]) row.get("user_id")))
                                                .orElseThrow();
                                if (owner.getRemoteUid() == null || owner.getRemoteUid() <= 0)
                                    return null;
                                row.put("gateway_user_id", owner.getRemoteUid());
                                row.put(
                                        "evaluation_run_id",
                                        Objects.requireNonNullElse(client.evaluationRun(), ""));
                            }
                            jdbc.update(
                                    "update material_semantic_jobs set"
                                        + " request_owner_id=?,gateway_user_id=?,evaluation_run_id=?,status='PROCESSING',lease_token=?,lease_until=DATE_ADD(CURRENT_TIMESTAMP(6),interval"
                                        + " 10 minute) where revision_id=? and profile=?",
                                    row.get("user_id"),
                                    row.get("gateway_user_id"),
                                    row.get("evaluation_run_id"),
                                    token,
                                    row.get("revision_id"),
                                    row.get("profile"));
                            return row;
                        });
        if (claim == null) return;
        String revision = (String) claim.get("revision_id"),
                profile = (String) claim.get("profile");
        try {
            User owner = users.findById(uuid((byte[]) claim.get("user_id"))).orElseThrow();
            MaterialNativeClient.requireOriginalUser(
                    owner, ((Number) claim.get("gateway_user_id")).longValue());
            var rows =
                    jdbc.queryForList(
                            "select c.*,r.owner_id from material_evidence_chunks c join"
                                    + " material_revisions r on r.id=c.revision_id where"
                                    + " c.revision_id=? order by c.seq",
                            revision);
            for (int start = 0; start < rows.size(); start += 20) {
                var batch = rows.subList(start, Math.min(rows.size(), start + 20));
                List<EvidenceDtos.Hit> current = new ArrayList<>();
                for (var row : batch) current.add(sources.open(owner, (String) row.get("id")));
                var vectors =
                        client.embed(
                                owner,
                                "index:" + revision + ":" + profile + ":" + (start / 20),
                                MaterialNativeClient.profile(profile),
                                current.stream().map(EvidenceDtos.Hit::text).toList(),
                                "document",
                                (String) claim.get("evaluation_run_id"));
                for (int i = 0; i < batch.size(); i++) {
                    var row = batch.get(i);
                    var h = sources.open(owner, (String) row.get("id"));
                    var chunk =
                            new MaterialChunk(
                                    h.chunkId(),
                                    h.materialId(),
                                    h.title(),
                                    h.text(),
                                    ((Number) row.get("seq")).intValue(),
                                    "",
                                    row.get("owner_id") == null
                                            ? null
                                            : uuid((byte[]) row.get("owner_id")),
                                    "approved");
                    indexes.get(profile).upsert(chunk, vectors.get(i));
                }
            }
            finish(revision, profile, token, "COMPLETED", null);
        } catch (RuntimeException failed) {
            if (MaterialNativeClient.budgetBusy(failed)) {
                int waits = ((Number) claim.get("budget_waits")).intValue();
                transactions.executeWithoutResult(
                        tx ->
                                jdbc.update(
                                        "update material_semantic_jobs set"
                                            + " status=?,error_code=?,budget_waits=budget_waits+1,not_before=DATE_ADD(CURRENT_TIMESTAMP(6),interval"
                                            + " ? second),lease_token=null,lease_until=null where"
                                            + " revision_id=? and profile=? and lease_token=? and"
                                            + " status='PROCESSING'",
                                        waits < 3 ? "QUEUED" : "RECONCILIATION_REQUIRED",
                                        waits < 3
                                                ? "RETRIEVAL_BUDGET_IN_FLIGHT"
                                                : "BUDGET_WAIT_LIMIT",
                                        MaterialNativeClient.budgetWaitSeconds(waits),
                                        revision,
                                        profile,
                                        token));
                return;
            }
            // A stable request can recover a stored result; new keys and automatic re-inference are
            // forbidden.
            finish(
                    revision,
                    profile,
                    token,
                    "RECONCILIATION_REQUIRED",
                    failed instanceof io.grpc.StatusRuntimeException e
                            ? e.getStatus().getCode().name()
                            : "INDEX_FAILED");
        }
    }

    private void finish(
            String revision, String profile, String token, String status, String error) {
        transactions.executeWithoutResult(
                tx ->
                        jdbc.update(
                                "update material_semantic_jobs set"
                                        + " status=?,error_code=?,lease_token=null,lease_until=null"
                                        + " where revision_id=? and profile=? and lease_token=? and"
                                        + " status='PROCESSING'",
                                status,
                                error,
                                revision,
                                profile,
                                token));
    }
}
