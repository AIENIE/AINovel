package com.ainovel.app.material;

import com.ainovel.app.material.dto.*;
import com.ainovel.app.material.model.*;
import com.ainovel.app.material.repo.*;
import com.ainovel.app.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.*;

@Service
public class MaterialRetrievalService {
    @org.springframework.beans.factory.annotation.Value("${app.material-evidence.legacy-semantic-enabled:false}")
    private boolean legacySemanticEnabled;
    @Autowired
    private com.ainovel.app.material.evidence.MaterialEvidenceService materialEvidence;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MaterialRetrievalService.class);
    private final MaterialRepository materials;
    private final MaterialChunker chunker;
    private final TextEmbeddingClient embeddings;
    private final MaterialVectorIndex vectors;
    private final MaterialChunkProjectionRepository chunks;
    private final MaterialIndexJobRepository jobs;
    private final TransactionTemplate transactions;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("materialIndexExecutor")
    private java.util.concurrent.Executor executor;
    private final Set<UUID> dispatched = java.util.concurrent.ConcurrentHashMap.newKeySet();
    @org.springframework.beans.factory.annotation.Value("${app.material.index.max-chunks:512}")
    private int maxChunks = 512;
    @org.springframework.beans.factory.annotation.Value("${app.material.index.max-attempts:5}")
    private int maxAttempts = 5;
    @org.springframework.beans.factory.annotation.Value("${app.material.index.max-duration-seconds:600}")
    private long maxDurationSeconds = 600;

    @Autowired
    public MaterialRetrievalService(MaterialRepository materials, MaterialChunker chunker,
                                    TextEmbeddingClient embeddings, MaterialVectorIndex vectors,
                                    MaterialChunkProjectionRepository chunks, MaterialIndexJobRepository jobs,
                                    TransactionTemplate transactions) {
        this.materials=materials; this.chunker=chunker; this.embeddings=embeddings; this.vectors=vectors;
        this.chunks=chunks; this.jobs=jobs; this.transactions=transactions;
    }

    @Transactional
    public void indexMaterial(User user, Material material) {
        materialEvidence.capture(material);
    }

    public void deleteIndexAfterCommit(UUID materialId) {
        Runnable cleanup = () -> {
            try { vectors.deleteMaterial(materialId); }
            catch (RuntimeException ex) {
                log.warn("event=material_vector_delete_failed materialId={} errorType={}", materialId, ex.getClass().getSimpleName());
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cleanup.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { cleanup.run(); }
        });
    }

    @Scheduled(fixedDelayString="${app.material-index.dispatch-delay-ms:5000}")
    public void dispatchIndexJobs() {
        if(!legacySemanticEnabled)return;
        recoverExpired();
        jobs.findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc("queued", Instant.now(), PageRequest.of(0,20))
                .forEach(job -> dispatch(job.getId()));
    }

    private void dispatch(UUID id) {
        if (!dispatched.add(id)) return;
        try {
            executor.execute(() -> { try { process(id); } finally { dispatched.remove(id); } });
        } catch (java.util.concurrent.RejectedExecutionException ex) { dispatched.remove(id); }
    }

    private void process(UUID jobId) {
        String token = UUID.randomUUID().toString();
        Instant deadline = Instant.now().plusSeconds(Math.max(1, maxDurationSeconds));
        IndexClaim claim = transactions.execute(status -> jobs.findByIdForUpdate(jobId).filter(job -> "queued".equals(job.getStatus())).map(job -> {
            if (job.getAttemptCount() >= Math.max(1, maxAttempts)) { job.setStatus("failed"); job.setErrorCode("INDEX_ATTEMPTS_EXHAUSTED"); return null; }
            job.setStatus("processing"); job.setLeaseOwner(token); job.setLeaseExpiresAt(deadline);
            job.setAttemptCount(job.getAttemptCount()+1); Material material=job.getMaterial();
            if (material.getUser()!=null) material.getUser().getUsername();
            return new IndexClaim(job.getId(),material,job.getAttemptCount(), material.getContentVersion(), token, deadline);
        }).orElse(null));
        if(claim==null)return;
        List<String> generatedIds = new ArrayList<>();
        try {
            if (claim.material().getContent() != null && claim.material().getContent().length() > Math.max(1, maxChunks) * 780L + 120)
                throw new IndexLimitException();
            List<MaterialChunk> projected=chunker.chunks(claim.material()).stream().map(chunk -> {
                String id = UUID.nameUUIDFromBytes((chunk.materialId()+":"+claim.version()+":"+claim.token()+":"+chunk.chunkSeq()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
                return new MaterialChunk(id, chunk.materialId(), chunk.title(), chunk.text(), chunk.chunkSeq(), chunk.tags(), chunk.ownerUserId(), chunk.status());
            }).toList();
            if (projected.size() > Math.max(1, maxChunks)) throw new IndexLimitException();
            if("approved".equalsIgnoreCase(claim.material().getStatus())) {
                for(MaterialChunk chunk:projected){
                    requireClaim(claim);
                    float[] vector=embeddings.embed(claim.material().getUser(),chunk.text());
                    if(vector==null||vector.length==0)throw new IllegalStateException("EMPTY_EMBEDDING");
                    requireClaim(claim);
                    generatedIds.add(chunk.chunkId());
                    vectors.upsert(chunk,vector);
                }
            }
            List<String> oldIds = transactions.execute(status -> {
                Material currentMaterial = materials.findByIdForUpdate(claim.material().getId()).orElseThrow();
                if (currentMaterial.getContentVersion() != claim.version()) throw new IllegalStateException("INDEX_SOURCE_CHANGED");
                MaterialIndexJob job = jobs.findByIdForUpdate(jobId).orElseThrow();
                checkClaim(job, claim);
                List<String> old = chunks.findByMaterialId(claim.material().getId()).stream().map(MaterialChunkProjection::getChunkId).toList();
                chunks.deleteByMaterialId(claim.material().getId());
                chunks.flush();
                chunks.saveAll(projected.stream().map(chunk -> {
                    MaterialChunkProjection value = projection(job.getMaterial(), chunk); value.setContentVersion(claim.version()); return value;
                }).toList());
                job.setStatus("completed"); job.setNextAttemptAt(null); clearLease(job);
                return old;
            });
            try { vectors.deleteChunks(oldIds == null ? List.of() : oldIds); } catch (RuntimeException cleanupFailure) { log.warn("event=material_old_vectors_cleanup_failed jobId={}", jobId); }
        } catch(RuntimeException ex){
            try { vectors.deleteChunks(generatedIds); } catch (RuntimeException ignored) { }
            transactions.executeWithoutResult(status->jobs.findByIdForUpdate(jobId).ifPresent(job->{
                if(!token.equals(job.getLeaseOwner()))return;
                boolean terminal = ex instanceof IndexLimitException || claim.attempt() >= Math.max(1, maxAttempts);
                job.setStatus(terminal ? "failed" : "queued");
                job.setNextAttemptAt(Instant.now().plusSeconds(Math.min(300L,1L<<Math.min(8,claim.attempt()))));
                job.setErrorCode(ex instanceof IndexLimitException ? "INDEX_TOO_LARGE" : terminal ? "INDEX_ATTEMPTS_EXHAUSTED" : "INDEX_RETRY"); clearLease(job);
            }));
        }
    }

    private void requireClaim(IndexClaim claim) {
        transactions.executeWithoutResult(status -> checkClaim(jobs.findByIdForUpdate(claim.jobId()).orElseThrow(), claim));
    }
    private void checkClaim(MaterialIndexJob job, IndexClaim claim) {
        if (!claim.token().equals(job.getLeaseOwner()) || !"processing".equals(job.getStatus())
                || !claim.deadline().isAfter(Instant.now()) || job.getMaterial().getContentVersion() != claim.version()
                || Thread.currentThread().isInterrupted()) throw new IllegalStateException("INDEX_LEASE_LOST");
    }
    private static class IndexLimitException extends RuntimeException { }

    public List<MaterialSearchResultDto> search(User user, MaterialSearchRequest request) {
        String query=request==null||request.query()==null?"":request.query().trim();
        int limit=Math.max(1,Math.min(request!=null&&request.limit()!=null?request.limit():10,30));
        return materialEvidence.legacySearch(user,query,limit);
    }

    private MaterialChunkProjection projection(Material material,MaterialChunk chunk){MaterialChunkProjection p=new MaterialChunkProjection();p.setChunkId(chunk.chunkId());p.setMaterial(material);p.setOwnerUserId(chunk.ownerUserId());p.setStatus(chunk.status());p.setTitle(chunk.title());p.setText(chunk.text());p.setTags(chunk.tags());p.setChunkSeq(chunk.chunkSeq());return p;}
    private void recoverExpired(){Instant now=Instant.now();jobs.findByStatus("processing").stream().map(MaterialIndexJob::getId).forEach(id -> transactions.executeWithoutResult(status -> jobs.findByIdForUpdate(id).ifPresent(job -> { if ("processing".equals(job.getStatus()) && job.getLeaseExpiresAt()!=null&&job.getLeaseExpiresAt().isBefore(now)) { job.setStatus(job.getAttemptCount() >= Math.max(1, maxAttempts) ? "failed" : "queued");job.setNextAttemptAt(now);clearLease(job); } })));}
    private void clearLease(MaterialIndexJob job){job.setLeaseOwner(null);job.setLeaseExpiresAt(null);}
    private record IndexClaim(UUID jobId,Material material,int attempt,long version,String token,Instant deadline){}
}
