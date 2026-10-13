package com.ainovel.app.narrative;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.UUID;
@Service
public class NarrativeGenerationCandidateStore {
    private final NarrativeGenerationCandidateRepository repository;
    private final ObjectMapper json;
    public NarrativeGenerationCandidateStore(NarrativeGenerationCandidateRepository repository,ObjectMapper json){this.repository=repository;this.json=json;}
    public record Source(com.ainovel.app.manuscript.GenerationMode mode,
            com.ainovel.app.manuscript.SceneGenerationService.GenerationMetadata generation,
            com.ainovel.app.quality.language.LanguageStandard.Selection language) {}

    /** Independent commit: a later AI/quality/writeback failure must not lose returned prose. */
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public UUID retainDraft(UUID operationId, com.ainovel.app.manuscript.model.Manuscript manuscript,
            UUID sceneId, String html, int sequence, int minHan, int maxHan, int actualHan, Source source) {
        var existing=repository.findByOperationIdAndGenerationSequence(operationId,sequence);
        if(existing.isPresent()&&existing.get().getActualHan()!=null) return existing.get().getId();
        var c=existing.orElseGet(NarrativeGenerationCandidate::new);
        c.setOperationId(operationId);c.setGenerationSequence(sequence);
        c.setManuscriptId(manuscript.getId());c.setBranchId(manuscript.getCurrentBranchId());
        c.setManuscriptVersion(manuscript.getVersion());c.setSceneId(sceneId);c.setContent(html);
        c.setMinHan(minHan);c.setMaxHan(maxHan);c.setActualHan(actualHan);
        c.setQualityStatus("NOT_PROCESSED");c.setSourceKind("GENERATED");
        c.setStatus(actualHan>=minHan&&actualHan<=maxHan?"AWAITING_WRITEBACK":"LENGTH_REJECTED");
        c.setCreatedAt(Instant.now());
        try {
            c.setMetadataJson(json.writeValueAsString(source));
            var metadata=source.generation();
            var stamp=metadata.contextManifest()==null?null:metadata.contextManifest().isolationStamp();
            var trace=stamp==null?json.createObjectNode():(com.fasterxml.jackson.databind.node.ObjectNode)json.valueToTree(stamp);
            trace.put("promptVersion",metadata.promptVersion());trace.put("promptHash",metadata.promptHash());
            trace.put("contextHash",metadata.contextManifest()==null?null:metadata.contextManifest().contextHash());
            trace.put("modelKey",metadata.modelKey());trace.put("attemptCount",sequence);
            c.setStampJson(json.writeValueAsString(trace));
        } catch(Exception e){throw new IllegalStateException(e);}
        return repository.saveAndFlush(c).getId();
    }

    @Transactional(readOnly=true)
    public NarrativeGenerationCandidate draft(UUID operationId,int sequence) {
        return repository.findByOperationIdAndGenerationSequence(operationId,sequence).orElse(null);
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void freeze(UUID operationId,com.ainovel.app.manuscript.model.Manuscript manuscript,UUID sceneId,Object input) {
        freezeRequest(operationId,manuscript,sceneId,1,input);
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void freezeRequest(UUID operationId,com.ainovel.app.manuscript.model.Manuscript manuscript,UUID sceneId,int sequence,Object input) {
        if(repository.findByOperationIdAndGenerationSequence(operationId,sequence).isPresent()) return;
        var c=new NarrativeGenerationCandidate();
        c.setOperationId(operationId);c.setGenerationSequence(sequence);c.setManuscriptId(manuscript.getId());
        c.setBranchId(manuscript.getCurrentBranchId());c.setManuscriptVersion(manuscript.getVersion());c.setSceneId(sceneId);
        c.setContent("");c.setStampJson("{}");c.setStatus("PREPARING");c.setSourceKind("GENERATED");c.setCreatedAt(Instant.now());
        try {c.setRequestJson(json.writeValueAsString(input));} catch(Exception e){throw new IllegalStateException(e);}
        repository.saveAndFlush(c);
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void qualityResult(UUID id,String html,int actualHan) {
        var c=repository.findById(id).orElseThrow();
        if("APPLIED".equals(c.getStatus())) return;
        c.setContent(html);c.setActualHan(actualHan);c.setQualityStatus("PROCESSED");
        c.setStatus(actualHan>=c.getMinHan()&&actualHan<=c.getMaxHan()?"AWAITING_WRITEBACK":"LENGTH_REJECTED");
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public UUID retain(NarrativeContextDtos.Stamp stamp,UUID sceneId,String html,com.ainovel.app.manuscript.SceneGenerationService.GenerationMetadata metadata){
        return persist(stamp,sceneId,html,"AWAITING_WRITEBACK",metadata);
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public UUID retainLengthRejected(NarrativeContextDtos.Stamp stamp,UUID sceneId,String html,com.ainovel.app.manuscript.SceneGenerationService.GenerationMetadata metadata){
        return persist(stamp,sceneId,html,"LENGTH_REJECTED",metadata);
    }
    private UUID persist(NarrativeContextDtos.Stamp stamp,UUID sceneId,String html,String status,com.ainovel.app.manuscript.SceneGenerationService.GenerationMetadata metadata){
        var c=new NarrativeGenerationCandidate(); c.setManuscriptId(stamp.manuscriptId());c.setBranchId(stamp.branchId());c.setSceneId(sceneId);
        c.setContent(html);c.setStatus(status);c.setCreatedAt(Instant.now());
        try {
            var trace=(com.fasterxml.jackson.databind.node.ObjectNode)json.valueToTree(stamp);
            trace.put("promptVersion",metadata.promptVersion());trace.put("promptHash",metadata.promptHash());
            trace.put("contextHash",metadata.contextManifest().contextHash());trace.put("modelKey",metadata.modelKey());
            trace.put("attemptCount",metadata.attemptCount());c.setStampJson(json.writeValueAsString(trace));
        } catch(Exception e){throw new IllegalStateException(e);}
        return repository.saveAndFlush(c).getId();
    }
    @Transactional public void applied(UUID id){if(id!=null)repository.findById(id).ifPresent(c->c.setStatus("APPLIED"));}
}
