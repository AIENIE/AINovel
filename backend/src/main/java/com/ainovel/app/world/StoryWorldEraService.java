package com.ainovel.app.world;

import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.world.model.StoryWorldEras;
import com.ainovel.app.world.repo.WorldVersionRepository;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.ainovel.app.world.WorldDraftDtos.*;

/** Author browsing labels. Deliberately separate from all generation/binding revisions. */
@Service
public class StoryWorldEraService {
    public record View(UUID worldId,UUID worldVersionId,long bindingRevision,long revision,UUID reviewedVersionId,
                       List<UUID> eraIds,List<Era> eras,List<Era> reviewedEras,List<UUID> changedEraIds,List<UUID> missingEraIds) {}
    public record Save(UUID worldId,UUID worldVersionId,Long expectedBindingRevision,Long expectedRevision,List<UUID> eraIds) {}
    private final EntityManager em;
    private final ResourceAccessGuard access;
    private final WorldBindingService bindings;
    private final WorldVersionRepository versions;
    private final WorldEntryStore store;
    public StoryWorldEraService(EntityManager em,ResourceAccessGuard access,WorldBindingService bindings,WorldVersionRepository versions,WorldEntryStore store) {
        this.em=em;this.access=access;this.bindings=bindings;this.versions=versions;this.store=store;
    }
    @Transactional(readOnly=true)
    public View view(UUID storyId){return view(owned(storyId,false));}
    @Transactional
    public View save(UUID storyId,Save request) {
        var story=owned(storyId,true);
        var binding=bindings.requireUsable(bindings.resolve(story,null,null));
        if(binding==null||request==null||!Objects.equals(binding.worldId(),request.worldId())
                ||!Objects.equals(binding.versionId(),request.worldVersionId())||request.expectedBindingRevision()==null
                ||request.expectedBindingRevision()!=story.getWorldBindingRevision())throw WorldVersionService.conflict("WORLD_BINDING_CONFLICT");
        var row=find(storyId,binding.worldId(),true);
        if(request.expectedRevision()==null||request.expectedRevision()!=(row==null?0:row.getRevision()))
            throw WorldVersionService.conflict("WORLD_ERAS_CONFLICT");
        if(request.eraIds()==null||request.eraIds().stream().anyMatch(Objects::isNull))throw WorldEntryStore.error("WORLD_ERAS_INVALID");
        var ids=request.eraIds().stream().distinct().sorted().toList();
        var available=eras(binding.content()).stream().map(Era::id).toList();
        if(!available.containsAll(ids))throw WorldEntryStore.error("WORLD_ERA_UNAVAILABLE");
        if(row==null){row=new StoryWorldEras(storyId,binding.worldId());em.persist(row);}
        row.review(binding.versionId(),store.write(ids));em.flush();
        return view(story);
    }
    private View view(Story story) {
        var binding=bindings.requireUsable(bindings.resolve(story,null,null));
        if(binding==null)return new View(null,null,story.getWorldBindingRevision(),0,null,List.of(),List.of(),List.of(),List.of(),List.of());
        var row=find(story.getId(),binding.worldId(),false);
        var current=eras(binding.content());
        if(row==null)return new View(binding.worldId(),binding.versionId(),story.getWorldBindingRevision(),0,null,List.of(),current,List.of(),List.of(),List.of());
        var ids=store.strings(row.getEraIdsJson()).stream().map(UUID::fromString).toList();
        var reviewed=row.getReviewedVersionId()==null?List.<Era>of():versions.findById(row.getReviewedVersionId())
            .filter(v->v.getWorldId().equals(binding.worldId())).map(v->eras(store.parse(v.getSnapshotJson()))).orElse(List.of());
        var old=new HashMap<UUID,Era>();reviewed.forEach(e->old.put(e.id(),e));
        var now=new HashMap<UUID,Era>();current.forEach(e->now.put(e.id(),e));
        var missing=ids.stream().filter(id->!now.containsKey(id)).toList();
        var changed=ids.stream().filter(now::containsKey).filter(id->!Objects.equals(old.get(id),now.get(id))).toList();
        return new View(binding.worldId(),binding.versionId(),story.getWorldBindingRevision(),row.getRevision(),row.getReviewedVersionId(),ids,current,reviewed,changed,missing);
    }
    private Story owned(UUID id,boolean lock) {
        var story=em.find(Story.class,id,lock?LockModeType.PESSIMISTIC_WRITE:LockModeType.NONE);
        if(story==null)throw WorldEntryStore.error("STORY_NOT_FOUND");access.assertOwner(story.getUser());return story;
    }
    private StoryWorldEras find(UUID storyId,UUID worldId,boolean lock) {
        var query=em.createQuery("select s from StoryWorldEras s where s.storyId=:story and s.worldId=:world",StoryWorldEras.class)
            .setParameter("story",storyId).setParameter("world",worldId);
        // Revision checks need a current read, even if an outer MySQL transaction already has a read view.
        if(lock)query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        return query.getResultStream().findFirst().orElse(null);
    }
    private static List<Era> eras(Content c){return c==null||c.timeline()==null?List.of():c.timeline().eras();}
}
