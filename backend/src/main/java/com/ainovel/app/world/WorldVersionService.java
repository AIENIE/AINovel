package com.ainovel.app.world;

import com.ainovel.app.common.ApiStatusException;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.world.model.*;
import com.ainovel.app.world.repo.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import static com.ainovel.app.world.WorldDraftDtos.*;

@Service
public class WorldVersionService {
    private final WorldRepository worlds;
    private final WorldPublishReceiptRepository receipts;
    private final WorldVersionRepository versions;
    private final WorldEntryStore store;
    private final WorldService legacy;
    private final ResourceAccessGuard access;
    public WorldVersionService(WorldRepository worlds,WorldVersionRepository versions,WorldEntryStore store,WorldService legacy,ResourceAccessGuard access,WorldPublishReceiptRepository receipts) {
        this.receipts=receipts;this.worlds=worlds;this.versions=versions;this.store=store;this.legacy=legacy;this.access=access;
    }
    @Transactional(readOnly=true)
    public Draft draft(UUID id) {
        var world=owned(id,false);var content=store.content(world);
        var latest=versions.findFirstByWorldIdAndOriginOrderBySequenceNumberDesc(id,"PUBLISHED");
        return new Draft(legacy.get(id),content.entries(),content.relations(),latest.map(v->dto(v,false)).orElse(null),
            latest.isEmpty()||!Objects.equals(latest.get().getContentHash(),store.hash(content)),content.timeline());
    }
    @Transactional
    public Draft save(UUID id,Save request) {
        var world=owned(id,true);checkRevision(world,request.expectedRevision());
        var m=request.metadata();
        store.save(world,request.entries(),request.relations(),request.timeline(), current -> {
        if(m!=null) {
            if(m.name()!=null){if(m.name().isBlank())throw WorldEntryStore.error("WORLD_NAME_REQUIRED");world.setName(m.name());}
            if(m.tagline()!=null)world.setTagline(m.tagline());
            if(m.themes()!=null)world.setThemesJson(store.write(m.themes()));
            if(m.creativeIntent()!=null)world.setCreativeIntent(m.creativeIntent());
            if(m.notes()!=null)world.setNotes(m.notes());
            if(m.overview()!=null)world.setOverview(m.overview());
        }
        });
        worlds.saveAndFlush(world);
        return draft(id);
    }
    @Transactional
    public Version publish(UUID id,Publish request) {
        if(request==null||request.requestKey()==null||request.requestKey().isBlank()||request.requestKey().length()>100)throw WorldEntryStore.error("WORLD_PUBLISH_KEY_REQUIRED");
        var world=owned(id,true);
        String requestHash=com.ainovel.app.quality.language.LanguageProjection.hash(store.write(request));
        var receipt=receipts.findByWorldIdAndRequestKey(id,request.requestKey());
        if(receipt.isPresent()){
            if(!requestHash.equals(receipt.get().getRequestHash()))throw conflict("WORLD_PUBLISH_REQUEST_CONFLICT");
            return dto(requireVersion(id,receipt.get().getVersionId()),true);
        }
        checkRevision(world,request.expectedRevision());
        // Import old callers' module records once, before making a complete immutable copy.
        if(world.getDraftContentHash()==null)store.replaceLegacy(world,store.modules(world.getModulesJson()));
        var content=store.content(world);String hash=store.hash(content);
        var latest=versions.findFirstByWorldIdAndOriginOrderBySequenceNumberDesc(id,"PUBLISHED");
        if(latest.isPresent()&&hash.equals(latest.get().getContentHash())) {
            receipts.saveAndFlush(new WorldPublishReceipt(id,latest.get().getId(),request.requestKey(),requestHash));
            return dto(latest.get(),true);
        }
        var version=new WorldVersion();version.setId(UUID.randomUUID());version.setWorldId(id);
        version.setSequenceNumber(latest.map(v->v.getSequenceNumber()+1).orElse(1));version.setOrigin("PUBLISHED");
        version.setDraftRevision(request.expectedRevision());version.setContentHash(hash);version.setSnapshotJson(store.write(content));
        version.setReleaseNote(Objects.toString(request.releaseNote(),""));version.setRequestKey(request.requestKey());version.setCreatedAt(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        versions.saveAndFlush(version);
        receipts.saveAndFlush(new WorldPublishReceipt(id,version.getId(),request.requestKey(),requestHash));
        world.setStatus("active");world.setVersion("v"+version.getSequenceNumber());world.setDraftContentHash(hash);worlds.saveAndFlush(world);
        return dto(version,true);
    }
    @Transactional(readOnly=true)
    public List<Version> list(UUID worldId) {owned(worldId,false);return versions.findByWorldIdOrderBySequenceNumberDesc(worldId).stream().map(v->dto(v,false)).toList();}
    @Transactional(readOnly=true)
    public Version get(UUID worldId,UUID versionId) {owned(worldId,false);return dto(requireVersion(worldId,versionId),true);}
    public WorldVersion requireVersion(UUID worldId,UUID versionId) {
        return versions.findById(versionId).filter(v->v.getWorldId().equals(worldId)).orElseThrow(()->WorldEntryStore.error("WORLD_VERSION_UNAVAILABLE"));
    }
    @Transactional(readOnly=true)
    public Diff diff(UUID worldId,UUID from,UUID to,Long revision) {
        var world=owned(worldId,false);
        Content before=from==null?null:store.parse(requireVersion(worldId,from).getSnapshotJson()),after;
        if(to!=null)after=store.parse(requireVersion(worldId,to).getSnapshotJson());
        else {checkRevision(world,revision);after=store.content(world);}
        return compare(from,to,to==null?world.getRevision():null,before,after);
    }
    public static Diff compare(UUID from,UUID to,Long revision,Content before,Content after) {
        var changes=new ArrayList<Change>();
        var previous=before==null?Map.<String,Object>of():metadata(before);var next=metadata(after);
        next.forEach((key,value)->{if(!Objects.equals(previous.get(key),value))changes.add(new Change("METADATA",key,key,previous.get(key),value));});
        compareItems(changes,"ENTRY",before==null?List.of():before.entries(),after.entries(),Entry::id,Entry::title);
        compareItems(changes,"RELATION",before==null?List.of():before.relations(),after.relations(),Relation::id,Relation::type);
        var previousTimeline=before==null||before.timeline()==null?WorldTimeline.empty():before.timeline();
        var nextTimeline=after.timeline()==null?WorldTimeline.empty():after.timeline();
        if(!Objects.equals(previousTimeline.calendarName(),nextTimeline.calendarName()))
            changes.add(new Change("TIMELINE","calendarName","纪年名称",previousTimeline.calendarName(),nextTimeline.calendarName()));
        compareItems(changes,"ERA",previousTimeline.eras(),nextTimeline.eras(),Era::id,Era::name);
        compareItems(changes,"EVENT",previousTimeline.events(),nextTimeline.events(),Event::id,Event::title);
        return new Diff(from,to,revision,changes);
    }
    private static <T>void compareItems(List<Change> out,String kind,List<T> before,List<T> after,java.util.function.Function<T,UUID> id,java.util.function.Function<T,String> label) {
        var old=new TreeMap<String,T>();before.forEach(v->old.put(id.apply(v).toString(),v));
        var current=new TreeMap<String,T>();after.forEach(v->current.put(id.apply(v).toString(),v));
        var keys=new TreeSet<>(old.keySet());keys.addAll(current.keySet());
        for(String key:keys)if(!Objects.equals(old.get(key),current.get(key))) {
            T value=current.containsKey(key)?current.get(key):old.get(key);
            out.add(new Change(kind,key,label.apply(value),old.get(key),current.get(key)));
        }
    }
    private static Map<String,Object> metadata(Content c) {
        var result=new LinkedHashMap<String,Object>();result.put("name",c.name());result.put("tagline",c.tagline());
        result.put("themes",c.themes());result.put("creativeIntent",c.creativeIntent());result.put("notes",c.notes());result.put("overview",Objects.toString(c.overview(),""));return result;
    }
    public Version dto(WorldVersion v,boolean content) {
        return new Version(v.getId(),v.getWorldId(),v.getSequenceNumber(),v.getOrigin(),v.getLegacyVersion(),v.getDraftRevision(),
            v.getContentHash(),v.getReleaseNote(),v.getCreatedAt(),content?store.parse(v.getSnapshotJson()):null);
    }
    private World owned(UUID id,boolean lock) {
        var world=(lock?worlds.findByIdForUpdate(id):worlds.findById(id)).orElseThrow(()->WorldEntryStore.error("WORLD_NOT_FOUND"));
        access.assertOwner(world.getUser());return world;
    }
    public static void checkRevision(World world,Long revision) {
        if(revision==null)throw WorldEntryStore.error("WORLD_REVISION_REQUIRED");
        if(revision!=world.getRevision())throw conflict("WORLD_VERSION_CONFLICT");
    }
    public static ApiStatusException conflict(String code) {return new ApiStatusException(HttpStatus.CONFLICT,code);}
}
