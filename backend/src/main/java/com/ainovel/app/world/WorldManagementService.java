package com.ainovel.app.world;

import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.user.User;
import com.ainovel.app.world.dto.WorldDetailDto;
import com.ainovel.app.world.model.*;
import com.ainovel.app.world.repo.WorldRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.ainovel.app.world.WorldDraftDtos.*;

@Service
public class WorldManagementService {
    public record Copy(String source,Long expectedRevision,UUID versionId,String name,String requestKey) {}
    public record Source(UUID worldId,String name,String source,Long draftRevision,UUID versionId,Integer sequenceNumber,String contentHash) {}
    public record Download(String filename,String contentType,byte[] bytes) {}
    private record Selected(Source source,Content content,String rawJson) {}
    private final EntityManager em;
    private final WorldRepository worlds;
    private final WorldVersionService versions;
    private final WorldEntryStore store;
    private final WorldService legacy;
    private final ResourceAccessGuard access;
    private final ObjectMapper json;
    public WorldManagementService(EntityManager em,WorldRepository worlds,WorldVersionService versions,WorldEntryStore store,
                                  WorldService legacy,ResourceAccessGuard access,ObjectMapper json) {
        this.em=em;this.worlds=worlds;this.versions=versions;this.store=store;this.legacy=legacy;this.access=access;this.json=json;
    }
    @Transactional
    public WorldDetailDto copy(User user,UUID worldId,Copy request) {
        access.assertCurrentUserEquals(user.getUsername());
        if(request==null||request.requestKey()==null||request.requestKey().isBlank()||request.requestKey().length()>100)
            throw WorldEntryStore.error("WORLD_COPY_KEY_REQUIRED");
        // Serialize first-use receipts too; retrying after a lost response must not create another world.
        em.find(User.class,user.getId(),LockModeType.PESSIMISTIC_WRITE);
        String hash=com.ainovel.app.quality.language.LanguageProjection.hash(worldId+":"+store.write(request));
        var receipt=em.createQuery("select r from WorldCopyReceipt r where r.userId=:user and r.requestKey=:key",WorldCopyReceipt.class)
            .setParameter("user",user.getId()).setParameter("key",request.requestKey()).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultStream().findFirst();
        if(receipt.isPresent()) {
            if(!hash.equals(receipt.get().getRequestHash()))throw WorldVersionService.conflict("WORLD_COPY_REQUEST_CONFLICT");
            if(!worlds.existsById(receipt.get().getCopiedWorldId()))throw WorldVersionService.conflict("WORLD_COPY_RESULT_REMOVED");
            return legacy.get(receipt.get().getCopiedWorldId());
        }
        var source=select(worldId,request.source(),request.expectedRevision(),request.versionId(),true);
        String name=request.name()==null?source.content().name()+"（副本）":request.name().strip();
        if(name.isBlank()||name.length()>255)throw WorldEntryStore.error("WORLD_NAME_REQUIRED");
        var content=remap(source.content(),name);
        var world=new World();world.setUser(user);world.setName(content.name());world.setTagline(content.tagline());
        world.setThemesJson(store.write(content.themes()));world.setCreativeIntent(content.creativeIntent());world.setNotes(content.notes());
        world.setOverview(content.overview());world.setStatus("draft");world.setVersion("0.1.0");world.setModuleProgressJson("{}");
        world.setModulesJson(store.write(content.modules()));world.setLegacyModulesJson(content.legacyModulesJson());
        world.setTimelineJson(content.timeline()==null?null:store.write(content.timeline()));
        worlds.saveAndFlush(world);
        store.save(world,content.entries(),content.relations());worlds.saveAndFlush(world);
        em.persist(new WorldCopyReceipt(user.getId(),request.requestKey(),hash,worldId,world.getId()));em.flush();
        return legacy.get(world.getId());
    }
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Download export(UUID worldId,String source,Long revision,UUID versionId,String format) {
        if(!Set.of("MARKDOWN","JSON").contains(Objects.toString(format,"")))throw WorldEntryStore.error("WORLD_EXPORT_FORMAT_INVALID");
        var selected=select(worldId,source,revision,versionId,false);
        String text;
        if("JSON".equals(format)) {
            try {
                var document=new LinkedHashMap<String,Object>();document.put("formatVersion",1);document.put("source",selected.source());
                // Preserve the original snapshot's content, including fields unknown to a newer DTO.
                document.put("content",json.readTree(selected.rawJson()));text=json.writerWithDefaultPrettyPrinter().writeValueAsString(document)+"\n";
            } catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("WORLD_EXPORT_INVALID",e);}
        } else text=markdown(selected.source(),selected.content());
        String suffix=selected.source().versionId()==null?"draft-r"+selected.source().draftRevision():"v"+selected.source().sequenceNumber();
        String filename=selected.content().name().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_")+"-"+suffix+("JSON".equals(format)?".json":".md");
        return new Download(filename,"JSON".equals(format)?"application/json;charset=UTF-8":"text/markdown;charset=UTF-8",text.getBytes(StandardCharsets.UTF_8));
    }
    private Selected select(UUID id,String source,Long revision,UUID versionId,boolean lock) {
        var world=(lock?worlds.findByIdForUpdate(id):worlds.findById(id)).orElseThrow(()->WorldEntryStore.error("WORLD_NOT_FOUND"));
        access.assertOwner(world.getUser());
        if("DRAFT".equals(source)&&versionId==null) {
            WorldVersionService.checkRevision(world,revision);var content=store.content(world);
            return new Selected(new Source(id,content.name(),source,world.getRevision(),null,null,store.hash(content)),content,store.write(content));
        }
        if("VERSION".equals(source)&&versionId!=null&&revision==null) {
            var version=versions.requireVersion(id,versionId);
            if(!"PUBLISHED".equals(version.getOrigin()))throw WorldEntryStore.error("WORLD_PUBLISHED_VERSION_REQUIRED");
            var content=store.parse(version.getSnapshotJson());
            return new Selected(new Source(id,content.name(),source,version.getDraftRevision(),version.getId(),version.getSequenceNumber(),version.getContentHash()),content,version.getSnapshotJson());
        }
        throw WorldEntryStore.error("WORLD_SOURCE_REQUIRED");
    }
    static Content remap(Content source,String name) {
        var entryIds=new HashMap<UUID,UUID>();source.entries().forEach(e->entryIds.put(e.id(),UUID.randomUUID()));
        var entries=source.entries().stream().map(e->new Entry(entryIds.get(e.id()),e.type(),e.title(),e.summary(),e.content(),e.aliases(),e.tags(),e.legacyModuleKey(),e.legacyFieldKey())).toList();
        var relations=source.relations().stream().map(r->new Relation(UUID.randomUUID(),mapped(entryIds,r.sourceId()),mapped(entryIds,r.targetId()),r.type(),r.description())).toList();
        Timeline timeline=null;
        if(source.timeline()!=null) {
            var original=source.timeline();var eraIds=new HashMap<UUID,UUID>();original.eras().forEach(e->eraIds.put(e.id(),UUID.randomUUID()));
            var eras=original.eras().stream().map(e->new Era(eraIds.get(e.id()),e.name(),e.description(),e.date(),e.sortOrder())).toList();
            var events=original.events().stream().map(e->new Event(UUID.randomUUID(),e.title(),e.summary(),e.content(),e.aliases(),e.tags(),e.date(),
                e.entryIds().stream().map(id->mapped(entryIds,id)).toList(),e.eraIds().stream().map(id->mapped(eraIds,id)).toList(),e.sortOrder())).toList();
            timeline=WorldTimeline.normalize(new Timeline(1,original.calendarName(),eras,events),new HashSet<>(entryIds.values()));
        }
        return new Content(name,source.tagline(),source.themes(),source.creativeIntent(),source.notes(),entries,relations,source.modules(),source.legacyModulesJson(),source.overview(),timeline);
    }
    private static UUID mapped(Map<UUID,UUID> ids,UUID id) {
        UUID mapped=ids.get(id);if(mapped==null)throw WorldEntryStore.error("WORLD_SOURCE_REFERENCE_INVALID");return mapped;
    }
    static String markdown(Source source,Content content) {
        var out=new StringBuilder("# ").append(content.name()).append("\n\n");
        out.append("导出格式：1\n\n来源世界：").append(source.worldId()).append("\n\n");
        out.append(source.versionId()==null?"草稿 revision："+source.draftRevision():"发布版本：v"+source.sequenceNumber()+"（"+source.versionId()+"），来源 revision："+source.draftRevision()).append("\n\n");
        out.append("内容摘要：").append(source.contentHash()).append("\n\n");
        section(out,"简介",content.tagline());section(out,"主题",String.join("、",content.themes()));section(out,"创作意图",content.creativeIntent());
        section(out,"作者备注（创作说明）",content.notes());section(out,"世界概览",content.overview());
        out.append("## 世界条目\n\n");
        for(var entry:content.entries()) {
            out.append("### ").append(entry.title()).append("\n\nID：").append(entry.id()).append("；类型：").append(entry.type()).append("\n\n");
            out.append("别名：").append(String.join("、",entry.aliases())).append("\n\n标签：").append(String.join("、",entry.tags())).append("\n\n");
            text(out,entry.summary());text(out,entry.content());
            if(entry.legacyModuleKey()!=null)out.append("原始字段：").append(entry.legacyModuleKey()).append('.').append(entry.legacyFieldKey()).append("\n\n");
        }
        var names=new HashMap<UUID,String>();content.entries().forEach(e->names.put(e.id(),e.title()));
        out.append("## 条目关系\n\n");
        for(var relation:content.relations()) {
            out.append("### ").append(relation.type()).append("\n\nID：").append(relation.id()).append("\n\n")
                .append(reference(names,relation.sourceId())).append(" → ").append(reference(names,relation.targetId())).append("\n\n");text(out,relation.description());
        }
        var timeline=content.timeline();
        out.append("## 世界时间线\n\n");
        if(timeline==null)out.append("尚未设置时间线。\n\n");
        else {
            out.append("纪年：").append(timeline.calendarName()).append("\n\n");var eraNames=new HashMap<UUID,String>();
            timeline.eras().forEach(e->eraNames.put(e.id(),e.name()));
            for(var era:timeline.eras().stream().sorted(Comparator.comparingInt(Era::sortOrder).thenComparing(Era::id)).toList()) {
                out.append("### 时代：").append(era.name()).append("\n\nID：").append(era.id()).append("；顺序：").append(era.sortOrder()).append("\n\n")
                    .append(WorldTimeline.renderDate(era.date())).append("\n\n");text(out,era.description());
            }
            for(var event:timeline.events().stream().sorted(Comparator.comparingInt(Event::sortOrder).thenComparing(Event::id)).toList()) {
                out.append("### 事件：").append(event.title()).append("\n\nID：").append(event.id()).append("；顺序：").append(event.sortOrder()).append("\n\n")
                    .append(WorldTimeline.renderDate(event.date())).append("\n\n别名：").append(String.join("、",event.aliases())).append("\n\n标签：").append(String.join("、",event.tags())).append("\n\n");
                out.append("时代：").append(String.join("、",event.eraIds().stream().map(id->reference(eraNames,id)).toList())).append("\n\n");
                out.append("关联条目：").append(String.join("、",event.entryIds().stream().map(id->reference(names,id)).toList())).append("\n\n");text(out,event.summary());text(out,event.content());
            }
        }
        section(out,"原始兼容数据",content.legacyModulesJson()==null?"":content.legacyModulesJson().lines().map(line->"    "+line).collect(java.util.stream.Collectors.joining("\n")));
        return out.toString();
    }
    private static String reference(Map<UUID,String> names,UUID id){return names.getOrDefault(id,"缺失关联")+"（"+id+"）";}
    private static void section(StringBuilder out,String title,String value){out.append("## ").append(title).append("\n\n");text(out,value);}
    private static void text(StringBuilder out,String value){out.append(Objects.toString(value,"")).append("\n\n");}
}
