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
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MaterialRetrievalService.class);
    private final MaterialRepository materials;
    private final MaterialChunker chunker;
    private final TextEmbeddingClient embeddings;
    private final MaterialVectorIndex vectors;
    private final MaterialChunkProjectionRepository chunks;
    private final MaterialIndexJobRepository jobs;
    private final TransactionTemplate transactions;
    private final String leaseOwner = UUID.randomUUID().toString();

    public MaterialRetrievalService(MaterialRepository materials, MaterialChunker chunker,
                                    TextEmbeddingClient embeddings, MaterialVectorIndex vectors) {
        this(materials, chunker, embeddings, vectors, null, null, null);
    }

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
        if (jobs == null || material == null || material.getId() == null) return;
        MaterialIndexJob job = jobs.findByMaterialId(material.getId()).orElseGet(MaterialIndexJob::new);
        if (job.getMaterial() == null) job.setMaterial(material);
        job.setStatus("queued"); job.setNextAttemptAt(Instant.now()); job.setErrorCode(null);
        job.setLeaseOwner(null); job.setLeaseExpiresAt(null); jobs.save(job);
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
        if (jobs == null) return;
        recoverExpired();
        jobs.findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc("queued", Instant.now(), PageRequest.of(0,20))
                .forEach(job -> process(job.getId()));
    }

    private void process(UUID jobId) {
        IndexClaim claim = transactions.execute(status -> jobs.findById(jobId).filter(job -> "queued".equals(job.getStatus())).map(job -> {
            job.setStatus("processing"); job.setLeaseOwner(leaseOwner); job.setLeaseExpiresAt(Instant.now().plusSeconds(120));
            job.setAttemptCount(job.getAttemptCount()+1); Material material=job.getMaterial();
            if (material.getUser()!=null) material.getUser().getUsername();
            return new IndexClaim(job.getId(),material,job.getAttemptCount());
        }).orElse(null));
        if(claim==null)return;
        try {
            List<MaterialChunk> projected=chunker.chunks(claim.material());
            transactions.executeWithoutResult(status->{
                chunks.deleteByMaterialId(claim.material().getId());
                chunks.saveAll(projected.stream().map(chunk->projection(claim.material(),chunk)).toList());
            });
            vectors.deleteMaterial(claim.material().getId());
            if("approved".equalsIgnoreCase(claim.material().getStatus())) {
                for(MaterialChunk chunk:projected){
                    float[] vector=embeddings.embed(claim.material().getUser(),chunk.text());
                    if(vector==null||vector.length==0)throw new IllegalStateException("EMPTY_EMBEDDING");
                    vectors.upsert(chunk,vector);
                }
            }
            transactions.executeWithoutResult(status->jobs.findById(jobId).ifPresent(job->{
                if(!leaseOwner.equals(job.getLeaseOwner()))return; job.setStatus("completed"); job.setNextAttemptAt(null); clearLease(job);
            }));
        } catch(RuntimeException ex){
            transactions.executeWithoutResult(status->jobs.findById(jobId).ifPresent(job->{
                if(!leaseOwner.equals(job.getLeaseOwner()))return; job.setStatus("queued");
                job.setNextAttemptAt(Instant.now().plusSeconds(Math.min(300L,1L<<Math.min(8,claim.attempt()))));
                job.setErrorCode("INDEX_RETRY"); clearLease(job);
            }));
        }
    }

    public List<MaterialSearchResultDto> search(User user, MaterialSearchRequest request) {
        String query=request==null||request.query()==null?"":request.query().trim();
        int limit=Math.max(1,Math.min(request!=null&&request.limit()!=null?request.limit():10,30));
        if(chunks==null)return legacySearch(user,query,limit);
        Map<String,MaterialSearchResultDto> merged=new LinkedHashMap<>();
        UUID owner=user==null?null:user.getId();
        for(MaterialChunkProjection chunk:chunks.searchVisible(owner,query,limit*2)){
            double score=keywordScore(chunk,query); if(score<=0&&!query.isBlank())continue;
            merged.put(chunk.getChunkId(),new MaterialSearchResultDto(chunk.getMaterial().getId(),chunk.getChunkId(),chunk.getTitle(),snippet(chunk.getText()),score,chunk.getChunkSeq(),"keyword",List.of("database")));
        }
        if(!query.isBlank())try{
            float[] vector=embeddings.embed(user,query);
            for(VectorMatch match:vectors.search(vector,limit*2,owner)){
                MaterialChunkProjection current = chunks.findById(match.chunkId()).orElse(null);
                if (current == null || !"approved".equalsIgnoreCase(current.getStatus())
                        || (current.getOwnerUserId() != null && !current.getOwnerUserId().equals(owner))) continue;
                MaterialSearchResultDto value=new MaterialSearchResultDto(match.materialId(),match.chunkId(),match.title(),snippet(match.text()),match.score(),match.chunkSeq(),"vector",List.of("semantic"));
                merged.merge(match.chunkId(),value,(a,b)->a.score()>=b.score()?a:b);
            }
        }catch(RuntimeException ignored){}
        return merged.values().stream().sorted(Comparator.comparingDouble(MaterialSearchResultDto::score).reversed()).limit(limit).toList();
    }

    private List<MaterialSearchResultDto> legacySearch(User user,String query,int limit){
        List<MaterialSearchResultDto> result=new ArrayList<>();
        for(Material material:materials.findAll()){
            if(!"approved".equalsIgnoreCase(material.getStatus()))continue;
            if(user!=null&&material.getUser()!=null&&!user.getId().equals(material.getUser().getId()))continue;
            for(MaterialChunk chunk:chunker.chunks(material)){
                String haystack=(chunk.title()+" "+chunk.tags()+" "+chunk.text()).toLowerCase(Locale.ROOT);
                boolean matches=Arrays.stream(query.toLowerCase(Locale.ROOT).split("\\s+"))
                        .filter(term->!term.isBlank()).allMatch(haystack::contains);
                double score=matches?1:0;
                if(score>0)result.add(new MaterialSearchResultDto(chunk.materialId(),chunk.chunkId(),chunk.title(),snippet(chunk.text()),score,chunk.chunkSeq(),"keyword",List.of("content")));
            }
        }
        return result.stream().limit(limit).toList();
    }

    private MaterialChunkProjection projection(Material material,MaterialChunk chunk){MaterialChunkProjection p=new MaterialChunkProjection();p.setChunkId(chunk.chunkId());p.setMaterial(material);p.setOwnerUserId(chunk.ownerUserId());p.setStatus(chunk.status());p.setTitle(chunk.title());p.setText(chunk.text());p.setTags(chunk.tags());p.setChunkSeq(chunk.chunkSeq());return p;}
    private double keywordScore(MaterialChunkProjection chunk,String query){if(query.isBlank())return .1;String q=query.toLowerCase(Locale.ROOT);double score=0;if(safe(chunk.getTitle()).toLowerCase(Locale.ROOT).contains(q))score+=3;if(safe(chunk.getTags()).toLowerCase(Locale.ROOT).contains(q))score+=2;if(safe(chunk.getText()).toLowerCase(Locale.ROOT).contains(q))score+=1;return score;}
    private void recoverExpired(){Instant now=Instant.now();transactions.executeWithoutResult(status->jobs.findByStatus("processing").stream().filter(job->job.getLeaseExpiresAt()!=null&&job.getLeaseExpiresAt().isBefore(now)).forEach(job->{job.setStatus("queued");job.setNextAttemptAt(now);clearLease(job);}));}
    private void clearLease(MaterialIndexJob job){job.setLeaseOwner(null);job.setLeaseExpiresAt(null);}
    private String snippet(String text){return text==null?"":text.length()<=180?text:text.substring(0,180)+"...";}
    private String safe(String value){return value==null?"":value;}
    private record IndexClaim(UUID jobId,Material material,int attempt){}
}
