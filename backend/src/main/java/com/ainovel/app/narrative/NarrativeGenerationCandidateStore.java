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
