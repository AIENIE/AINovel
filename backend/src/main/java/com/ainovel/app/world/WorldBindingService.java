package com.ainovel.app.world;

import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.story.model.*;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.world.repo.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.ainovel.app.world.WorldDraftDtos.*;

@Service
public class WorldBindingService {
    public record Resolved(UUID worldId,UUID versionId,String sourceType,UUID sourceId,long bindingRevision,
                           String name,int sequenceNumber,String origin,String contentHash,String unavailableReason,Content content) {}
    public record Override(String resourceType,UUID resourceId,String title,Resolved effective) {}
    public record View(String resourceType,UUID resourceId,long bindingRevision,UUID explicitVersionId,
                       boolean explicit,Resolved effective,List<Override> overrides) {}
    public record Change(UUID fromVersionId,UUID toVersionId,Long expectedBindingRevision) {}
    private final EntityManager em;
    private final ResourceAccessGuard access;
    private final WorldRepository worlds;
    private final WorldVersionRepository versions;
    private final WorldVersionService versionService;
    private final WorldEntryStore store;
    public WorldBindingService(EntityManager em,ResourceAccessGuard access,WorldRepository worlds,WorldVersionRepository versions,
                               WorldVersionService versionService,WorldEntryStore store) {
        this.em=em;this.access=access;this.worlds=worlds;this.versions=versions;this.versionService=versionService;this.store=store;
    }
    @Transactional(readOnly=true)
    public Resolved resolve(Manuscript manuscript) {return resolve(manuscript.getOutline().getStory(),manuscript.getOutline(),manuscript);}
    public Resolved resolve(Story story,Outline outline,Manuscript manuscript) {
        if(manuscript!=null&&explicit(manuscript.getWorldId(),manuscript.getWorldVersionId()))
            return resolved(story,manuscript.getWorldId(),manuscript.getWorldVersionId(),"MANUSCRIPT",manuscript.getId(),manuscript.getWorldBindingRevision());
        if(outline!=null&&explicit(outline.getWorldId(),outline.getWorldVersionId()))
            return resolved(story,outline.getWorldId(),outline.getWorldVersionId(),"OUTLINE",outline.getId(),outline.getWorldBindingRevision());
        if(explicit(story.getWorldId(),story.getWorldVersionId()))
            return resolved(story,story.getWorldId(),story.getWorldVersionId(),"STORY",story.getId(),story.getWorldBindingRevision());
        return null;
    }
    public Resolved requireUsable(Resolved value) {
        if(value!=null&&value.unavailableReason()!=null)throw WorldEntryStore.error(value.unavailableReason());
        return value;
    }
    private Resolved resolved(Story story,String rawWorld,UUID versionId,String source,UUID sourceId,long revision) {
        UUID worldId=null;try{worldId=UUID.fromString(rawWorld);}catch(Exception ignored){/* Show the broken explicit binding; do not inherit. */}
        var world=worldId==null?Optional.<com.ainovel.app.world.model.World>empty():worlds.findById(worldId);
        var version=versionId==null?Optional.<com.ainovel.app.world.model.WorldVersion>empty():versions.findById(versionId);
        if(world.isEmpty()||world.get().getUser()==null||!Objects.equals(world.get().getUser().getId(),story.getUser().getId())
                ||version.isEmpty()||!Objects.equals(version.get().getWorldId(),worldId))
            return new Resolved(worldId,versionId,source,sourceId,revision,"不可用的世界绑定",0,null,null,"WORLD_BINDING_UNRESOLVED",null);
        var v=version.get();var content=store.parse(v.getSnapshotJson());
        return new Resolved(worldId,versionId,source,sourceId,revision,content.name(),v.getSequenceNumber(),v.getOrigin(),v.getContentHash(),null,content);
    }
    @Transactional(readOnly=true)
    public View view(String type,UUID id) {return view(resource(type,id,false),true);}
    private View view(Resource r,boolean includeOverrides) {
        var effective=resolve(r.story(),r.outline(),r.manuscript());
        var overrides=new ArrayList<Override>();
        if(includeOverrides&&r.type().equals("STORY")) {
            for(var o:em.createQuery("select o from Outline o where o.story.id=:id",Outline.class).setParameter("id",r.id()).getResultList())
                if(explicit(o.getWorldId(),o.getWorldVersionId()))overrides.add(new Override("OUTLINE",o.getId(),o.getTitle(),reference(resolve(r.story(),o,null))));
            for(var m:em.createQuery("select m from Manuscript m where m.outline.story.id=:id",Manuscript.class).setParameter("id",r.id()).getResultList())
                if(explicit(m.getWorldId(),m.getWorldVersionId()))overrides.add(new Override("MANUSCRIPT",m.getId(),m.getTitle(),reference(resolve(r.story(),m.getOutline(),m))));
        }
        return new View(r.type(),r.id(),r.revision(),r.versionId(),explicit(r.worldId(),r.versionId()),reference(effective),overrides);
    }
    @Transactional
    public View change(String type,UUID id,Change request) {
        var r=resource(type,id,true);
        if(request.expectedBindingRevision()==null||request.expectedBindingRevision()!=r.revision()||!Objects.equals(request.fromVersionId(),r.versionId()))
            throw WorldVersionService.conflict("WORLD_BINDING_CONFLICT");
        UUID target=request.toVersionId();String worldId=null;
        if(target!=null) {
            var v=versions.findById(target).orElseThrow(()->WorldEntryStore.error("WORLD_VERSION_UNAVAILABLE"));
            var w=worlds.findById(v.getWorldId()).orElseThrow(()->WorldEntryStore.error("WORLD_VERSION_UNAVAILABLE"));
            if(w.getUser()==null||!w.getUser().getId().equals(r.story().getUser().getId()))throw WorldEntryStore.error("WORLD_VERSION_UNAVAILABLE");
            if(!"PUBLISHED".equals(v.getOrigin()))throw WorldEntryStore.error("WORLD_PUBLISHED_VERSION_REQUIRED");
            worldId=v.getWorldId().toString();
            if(!type.equals("STORY")&&(!explicit(r.worldId(),r.versionId())||!worldId.equalsIgnoreCase(Objects.toString(r.worldId(),""))))
                throw WorldEntryStore.error("WORLD_BIND_AT_STORY");
        }
        if(Objects.equals(target,r.versionId())&&Objects.equals(worldId,r.worldId()))return view(r,true);
        if(r.manuscript()!=null){r.manuscript().setWorldId(worldId);r.manuscript().setWorldVersionId(target);r.manuscript().setWorldBindingRevision(r.revision()+1);}
        else if(r.outline()!=null){r.outline().setWorldId(worldId);r.outline().setWorldVersionId(target);r.outline().setWorldBindingRevision(r.revision()+1);}
        else {r.story().setWorldId(worldId);r.story().setWorldVersionId(target);r.story().setWorldBindingRevision(r.revision()+1);}
        em.flush();return view(resource(type,id,false),true);
    }
    @Transactional(readOnly=true)
    public Diff preview(String type,UUID id,UUID toVersionId) {
        var r=resource(type,id,false);var current=requireUsable(resolve(r.story(),r.outline(),r.manuscript()));
        var target=versions.findById(toVersionId).orElseThrow(()->WorldEntryStore.error("WORLD_VERSION_UNAVAILABLE"));
        var next=versionService.get(target.getWorldId(),toVersionId);
        if(!"PUBLISHED".equals(next.origin()))throw WorldEntryStore.error("WORLD_PUBLISHED_VERSION_REQUIRED");
        if(!type.equals("STORY")&&(current==null||!target.getWorldId().equals(current.worldId())))throw WorldEntryStore.error("WORLD_BIND_AT_STORY");
        return versionService.compare(current==null?null:current.versionId(),toVersionId,null,current==null?null:current.content(),next.content());
    }
    public boolean same(Resolved left,Resolved right) {
        if(left==null||right==null)return left==right;
        return Objects.equals(left.versionId(),right.versionId())&&Objects.equals(left.sourceType(),right.sourceType())
            &&Objects.equals(left.sourceId(),right.sourceId())&&Objects.equals(left.contentHash(),right.contentHash())
            &&left.bindingRevision()==right.bindingRevision()&&right.unavailableReason()==null;
    }
    public static boolean explicit(String worldId,UUID versionId){return versionId!=null||worldId!=null&&!worldId.isBlank();}
    public static void preserveLegacy(String current,String requested) {
        if(requested!=null&&!Objects.equals(Objects.toString(current,""),requested))
            throw WorldEntryStore.error("WORLD_USE_BINDING_PANEL");
    }
    private Resolved reference(Resolved v) {
        return v==null?null:new Resolved(v.worldId(),v.versionId(),v.sourceType(),v.sourceId(),v.bindingRevision(),v.name(),
            v.sequenceNumber(),v.origin(),v.contentHash(),v.unavailableReason(),null);
    }
    private record Resource(String type,UUID id,Story story,Outline outline,Manuscript manuscript) {
        String worldId(){return manuscript!=null?manuscript.getWorldId():outline!=null?outline.getWorldId():story.getWorldId();}
        UUID versionId(){return manuscript!=null?manuscript.getWorldVersionId():outline!=null?outline.getWorldVersionId():story.getWorldVersionId();}
        long revision(){return manuscript!=null?manuscript.getWorldBindingRevision():outline!=null?outline.getWorldBindingRevision():story.getWorldBindingRevision();}
    }
    private Resource resource(String type,UUID id,boolean lock) {
        Resource r;
        var mode=lock?LockModeType.PESSIMISTIC_WRITE:LockModeType.NONE;
        switch(type) {
            case "STORY"->{var s=em.find(Story.class,id,mode);if(s==null)throw WorldEntryStore.error("STORY_NOT_FOUND");r=new Resource(type,id,s,null,null);}
            case "OUTLINE"->{var o=em.find(Outline.class,id,mode);if(o==null)throw WorldEntryStore.error("OUTLINE_NOT_FOUND");r=new Resource(type,id,o.getStory(),o,null);}
            case "MANUSCRIPT"->{var m=em.find(Manuscript.class,id,mode);if(m==null)throw WorldEntryStore.error("MANUSCRIPT_NOT_FOUND");r=new Resource(type,id,m.getOutline().getStory(),m.getOutline(),m);}
            default->throw WorldEntryStore.error("WORLD_BINDING_SCOPE_INVALID");
        }
        access.assertOwner(r.story().getUser());return r;
    }
}
