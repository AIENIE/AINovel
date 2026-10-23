package com.ainovel.app.world;

import com.ainovel.app.common.ApiStatusException;
import com.ainovel.app.world.model.*;
import com.ainovel.app.world.repo.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static com.ainovel.app.world.WorldDraftDtos.*;

@Component
public class WorldEntryStore {
    public static final Map<String,String> TYPES;
    static {
        var types = new LinkedHashMap<String,String>();
        types.put("GEOGRAPHY","地理环境"); types.put("LOCATION","地点"); types.put("PERSON","世界人物");
        types.put("ORGANIZATION","组织势力"); types.put("SPECIES","族群物种"); types.put("SOCIETY","社会文化");
        types.put("RESOURCE","物品资源"); types.put("SYSTEM","魔法科技"); types.put("RULE","规则限制");
        types.put("HISTORY","历史背景"); types.put("CUSTOM","自由条目"); TYPES=Collections.unmodifiableMap(types);
    }
    private static final Map<String,String> LABELS=Map.ofEntries(
        Map.entry("terrain","地形地貌"),Map.entry("climate","气候特征"),Map.entry("locations","重要地点"),
        Map.entry("politics","政治体制"),Map.entry("economy","经济结构"),Map.entry("culture","文化"),
        Map.entry("system_name","体系名称"),Map.entry("rules","规则"),Map.entry("limitations","限制"));
    private final WorldEntryRepository entries;
    private final WorldRelationRepository relations;
    private final ObjectMapper json;
    public WorldEntryStore(WorldEntryRepository entries,WorldRelationRepository relations,ObjectMapper json) {
        this.entries=entries;this.relations=relations;this.json=json;
    }
    public List<Entry> entries(World world) {
        var stored=entries.findByWorldIdOrderById(world.getId());
        if(stored.isEmpty()&&world.getDraftContentHash()==null) return legacyEntries(world.getId(),modules(world.getModulesJson()));
        return stored.stream().map(e->new Entry(e.getId(),e.getType(),e.getTitle(),e.getSummary(),e.getContent(),
            strings(e.getAliasesJson()),strings(e.getTagsJson()),e.getLegacyModuleKey(),e.getLegacyFieldKey())).toList();
    }
    public List<Relation> relations(UUID worldId) {
        return relations.findByWorldIdOrderById(worldId).stream()
            .map(r->new Relation(r.getId(),r.getSourceId(),r.getTargetId(),r.getType(),r.getDescription())).toList();
    }
    public Content content(World world) {
        var items=entries(world);
        return new Content(world.getName(),world.getTagline(),strings(world.getThemesJson()),world.getCreativeIntent(),
            world.getNotes(),items,relations(world.getId()),projection(items),world.getLegacyModulesJson(),world.getOverview());
    }
    public Map<String,Map<String,String>> projection(List<Entry> items) {
        var result=new TreeMap<String,Map<String,String>>();
        for(var e:items) if(e.legacyModuleKey()!=null&&e.legacyFieldKey()!=null)
            result.computeIfAbsent(e.legacyModuleKey(),k->new TreeMap<>()).put(e.legacyFieldKey(),e.content());
        return result;
    }
    public void replaceLegacy(World world,Map<String,Map<String,String>> modules) {replaceLegacy(world,modules,w->{});}
    public void replaceLegacy(World world,Map<String,Map<String,String>> modules,java.util.function.Consumer<World> metadata) {
        var current=entries(world);var next=new ArrayList<Entry>();
        current.stream().filter(e->e.legacyModuleKey()==null).forEach(next::add);
        for(var e:legacyEntries(world.getId(),modules)) {
            var prior=current.stream().filter(p->Objects.equals(p.legacyModuleKey(),e.legacyModuleKey())&&Objects.equals(p.legacyFieldKey(),e.legacyFieldKey())).findFirst();
            next.add(prior.map(p->new Entry(p.id(),p.type(),p.title(),p.summary(),e.content(),p.aliases(),p.tags(),p.legacyModuleKey(),p.legacyFieldKey())).orElse(e));
        }
        var ids=next.stream().map(Entry::id).collect(java.util.stream.Collectors.toSet());
        save(world,next,relations(world.getId()).stream().filter(r->ids.contains(r.sourceId())&&ids.contains(r.targetId())).toList(),metadata);
    }
    public void save(World world,List<Entry> supplied,List<Relation> links) {save(world,supplied,links,w->{});}
    public void save(World world,List<Entry> supplied,List<Relation> links,java.util.function.Consumer<World> metadata) {
        if(supplied==null||links==null)throw error("WORLD_DRAFT_CONTENT_REQUIRED");
        var prior=entries.findByWorldIdOrderById(world.getId());
        var before=new HashMap<UUID,WorldEntry>();prior.forEach(e->before.put(e.getId(),e));
        var ids=new HashSet<UUID>();var legacyKeys=new HashSet<List<String>>();var normalized=new ArrayList<Entry>();
        for(var e:supplied) {
            if(e.id()==null||!ids.add(e.id())||!TYPES.containsKey(e.type())||e.title()==null||e.title().isBlank()||e.title().length()>255)throw error("WORLD_ENTRY_INVALID");
            if(!before.containsKey(e.id())&&entries.existsById(e.id()))throw error("WORLD_ENTRY_INVALID");
            var old=before.get(e.id());
            if(old!=null&&(!Objects.equals(old.getLegacyModuleKey(),e.legacyModuleKey())||!Objects.equals(old.getLegacyFieldKey(),e.legacyFieldKey())))throw error("WORLD_ENTRY_ORIGIN_CHANGED");
            if((e.legacyModuleKey()==null)!=(e.legacyFieldKey()==null))throw error("WORLD_ENTRY_INVALID");
            if(e.legacyModuleKey()!=null&&!legacyKeys.add(List.of(e.legacyModuleKey(),e.legacyFieldKey())))throw error("WORLD_ENTRY_ORIGIN_DUPLICATE");
            normalized.add(new Entry(e.id(),e.type(),e.title().strip(),e.summary(),e.content(),clean(e.aliases()),clean(e.tags()),e.legacyModuleKey(),e.legacyFieldKey()));
        }
        var linkIds=new HashSet<UUID>();
        var priorLinks=relations.findByWorldIdOrderById(world.getId());
        var oldLinkIds=priorLinks.stream().map(WorldRelation::getId).collect(java.util.stream.Collectors.toSet());
        for(var r:links) {
            if(r.id()==null||!linkIds.add(r.id())||!ids.contains(r.sourceId())||!ids.contains(r.targetId())||r.type()==null||r.type().isBlank()||r.type().length()>255)throw error("WORLD_RELATION_INVALID");
            if(!oldLinkIds.contains(r.id())&&relations.existsById(r.id()))throw error("WORLD_RELATION_INVALID");
        }
        relations.deleteAll(priorLinks.stream().filter(r->!linkIds.contains(r.getId())).toList());relations.flush();
        for(var e:normalized) {
            var row=before.getOrDefault(e.id(),new WorldEntry());row.setId(e.id());row.setWorldId(world.getId());
            row.setType(e.type());row.setTitle(e.title());row.setSummary(e.summary());row.setContent(e.content());
            row.setAliasesJson(write(e.aliases()));row.setTagsJson(write(e.tags()));
            row.setLegacyModuleKey(e.legacyModuleKey());row.setLegacyFieldKey(e.legacyFieldKey());entries.save(row);
        }
        entries.flush();
        for(var r:links) {
            var row=new WorldRelation();row.setId(r.id());row.setWorldId(world.getId());row.setSourceId(r.sourceId());
            row.setTargetId(r.targetId());row.setType(r.type().strip());row.setDescription(r.description());relations.save(row);
        }
        relations.flush();
        entries.deleteAll(prior.stream().filter(e->!ids.contains(e.getId())).toList());entries.flush();
        if(world.getLegacyModulesJson()==null)world.setLegacyModulesJson(world.getModulesJson());
        metadata.accept(world);
        world.setModulesJson(write(projection(normalized)));
        world.setDraftContentHash(hash(new Content(world.getName(),world.getTagline(),strings(world.getThemesJson()),world.getCreativeIntent(),world.getNotes(),normalized,links,projection(normalized),world.getLegacyModulesJson(),world.getOverview())));
    }
    public List<Entry> legacyEntries(UUID worldId,Map<String,Map<String,String>> modules) {
        var result=new ArrayList<Entry>();
        new TreeMap<>(modules).forEach((module,fields)->{
            if(fields==null)return;
            new TreeMap<>(fields).forEach((field,value)->{
                String origin=module+"."+field;
                UUID id=UUID.nameUUIDFromBytes((worldId+":"+origin).getBytes(StandardCharsets.UTF_8));
                String type=legacyType(module,field);
                result.add(new Entry(id,type,LABELS.getOrDefault(field,origin),"",value,List.of(),List.of(),module,field));
            });
        });
        return result;
    }
    public static String legacyType(String module,String field) {
        if("geography".equals(module)&&Set.of("terrain","climate","locations").contains(field))return "locations".equals(field)?"LOCATION":"GEOGRAPHY";
        if("society".equals(module)&&Set.of("politics","economy","culture").contains(field))return "SOCIETY";
        if("magic_tech".equals(module)&&Set.of("system_name","rules","limitations").contains(field))return "system_name".equals(field)?"SYSTEM":"RULE";
        return "CUSTOM";
    }
    public String feature(Entry entry,String action) {
        return entry.legacyModuleKey()!=null&&!"CUSTOM".equals(legacyType(entry.legacyModuleKey(),entry.legacyFieldKey()))
            ?"world."+entry.legacyModuleKey()+"."+entry.legacyFieldKey()+"."+action:"world.entry."+action;
    }
    public Map<String,Map<String,String>> modules(String value) {return read(value,new TypeReference<>(){},new TreeMap<>());}
    public List<String> strings(String value) {return clean(read(value,new TypeReference<>(){},List.of()));}
    private static List<String> clean(List<String> value) {return value==null?List.of():value.stream().filter(Objects::nonNull).distinct().toList();}
    private <T>T read(String value,TypeReference<T> type,T fallback) {
        if(value==null||value.isBlank()||value.equals("null"))return fallback;
        try{return json.readValue(value,type);}catch(Exception e){throw new IllegalStateException("WORLD_DATA_INVALID",e);}
    }
    public Content parse(String value) {try{return json.readValue(value,Content.class);}catch(Exception e){throw new IllegalStateException("WORLD_SNAPSHOT_INVALID",e);}}
    public String write(Object value) {try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    public String hash(Content value) {
        var sorted=new Content(value.name(),value.tagline(),value.themes(),value.creativeIntent(),value.notes(),
            value.entries().stream().sorted(Comparator.comparing(e->e.id().toString())).toList(),
            value.relations().stream().sorted(Comparator.comparing(e->e.id().toString())).toList(),value.modules(),value.legacyModulesJson(),value.overview());
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsBytes(sorted)));}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    public static ApiStatusException error(String message) {return new ApiStatusException(HttpStatus.UNPROCESSABLE_ENTITY,message);}
}
