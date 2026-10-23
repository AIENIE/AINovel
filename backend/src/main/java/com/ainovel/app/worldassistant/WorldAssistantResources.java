package com.ainovel.app.worldassistant;

import com.ainovel.app.user.User;
import com.ainovel.app.world.*;
import com.ainovel.app.world.model.World;
import com.ainovel.app.world.repo.WorldRepository;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import java.util.*;
import static com.ainovel.app.world.WorldDraftDtos.*;
import static com.ainovel.app.worldassistant.WorldAssistantErrors.*;

@Component
public class WorldAssistantResources {
    public static final Set<String> METADATA=Set.of("name","tagline","themes","creativeIntent","notes");
    private final WorldRepository worlds;
    private final WorldEntryStore entries;
    private final ObjectMapper json;
    public WorldAssistantResources(WorldRepository worlds,WorldEntryStore entries,ObjectMapper json) {this.worlds=worlds;this.entries=entries;this.json=json;}
    public World world(User user,UUID id,boolean lock) {
        var world=(lock?worlds.findByIdForUpdate(id):worlds.findById(id)).orElseThrow(WorldAssistantErrors::missing);
        if(!world.getUser().getId().equals(user.getId()))throw missing();return world;
    }
    public Content content(World world) {return entries.content(world);}
    public String hash(Content content) {return entries.hash(content);}
    public String feature(Entry entry,String action) {return entries.feature(entry,action);}
    public record Resource(String type,UUID id,String fieldKey,String title,JsonNode fields) {}
    public List<Resource> resources(UUID world,Content content) {
        var result=new ArrayList<Resource>();var metadata=json.valueToTree(content);
        for(String key:new TreeSet<>(METADATA))result.add(new Resource("METADATA",world,key,key,json.createObjectNode().set("value",metadata.path(key))));
        result.add(new Resource("OVERVIEW",world,null,"世界概览",json.createObjectNode().put("content",Objects.toString(content.overview(),""))));
        for(var e:content.entries()) {
            ObjectNode fields=json.valueToTree(e);fields.remove(List.of("id","legacyModuleKey","legacyFieldKey"));
            result.add(new Resource("ENTRY",e.id(),null,e.title(),fields));
        }
        for(var r:content.relations()) {
            ObjectNode fields=json.valueToTree(r);fields.remove("id");result.add(new Resource("RELATION",r.id(),null,r.type(),fields));
        }
        return List.copyOf(result);
    }
    public static String sourceId(Resource resource) {return resource.type().toLowerCase(Locale.ROOT)+":"+resource.id()+(resource.fieldKey()==null?"":":"+resource.fieldKey());}
    public Resource target(UUID world,Content content,String type,UUID id,String field) {
        return resources(world,content).stream().filter(r->r.type().equals(type)&&r.id().equals(id)&&Objects.equals(r.fieldKey(),field)).findFirst().orElse(null);
    }
    public JsonNode normalize(String type,String field,String action,JsonNode supplied,JsonNode before) {
        if(supplied==null||!supplied.isObject())throw invalid("WORLD_PROPOSAL_FIELDS_INVALID");
        Set<String> allowed=switch(type) {
            case "ENTRY"->Set.of("type","title","summary","content","aliases","tags");
            case "RELATION"->Set.of("sourceId","targetId","type","description");
            case "METADATA"->Set.of("value");
            case "OVERVIEW"->Set.of("content");
            default->throw invalid("WORLD_PROPOSAL_TARGET_INVALID");
        };
        if((type.equals("METADATA")||type.equals("OVERVIEW"))&&!action.equals("UPDATE"))throw invalid("WORLD_PROPOSAL_TARGET_INVALID");
        if(type.equals("METADATA")&&!METADATA.contains(field))throw invalid("WORLD_PROPOSAL_TARGET_INVALID");
        supplied.fieldNames().forEachRemaining(k->{if(!allowed.contains(k))throw invalid("WORLD_PROPOSAL_FIELDS_INVALID");});
        ObjectNode result=before==null?json.createObjectNode():(ObjectNode)before.deepCopy();
        result.setAll((ObjectNode)supplied);
        if(type.equals("ENTRY")) {
            text(result,"title",true,255);text(result,"type",true,32);
            if(!WorldEntryStore.TYPES.containsKey(result.path("type").asText()))throw invalid("WORLD_ENTRY_INVALID");
            text(result,"summary",false,100000);text(result,"content",false,100000);strings(result,"aliases");strings(result,"tags");
        } else if(type.equals("RELATION")) {
            id(result.path("sourceId"));id(result.path("targetId"));text(result,"type",true,255);text(result,"description",false,100000);
        } else if(type.equals("METADATA")) {
            if(field.equals("themes"))strings(result,"value");else text(result,"value",field.equals("name"),field.equals("name")?255:100000);
        } else text(result,"content",true,100000);
        return result;
    }
    private void text(ObjectNode node,String field,boolean required,int max) {
        JsonNode value=node.get(field);
        if(value!=null&&!value.isNull()&&!value.isTextual())throw invalid("WORLD_PROPOSAL_FIELDS_INVALID");
        String text=value==null||value.isNull()?"":value.asText();
        if(text.length()>max||required&&text.isBlank())throw invalid("WORLD_PROPOSAL_FIELDS_INVALID");node.put(field,text);
    }
    private void strings(ObjectNode node,String field) {
        var value=node.get(field);if(value==null||value.isNull()){node.putArray(field);return;}
        if(!value.isArray()||value.size()>100)throw invalid("WORLD_PROPOSAL_FIELDS_INVALID");
        var result=new LinkedHashSet<String>();for(var item:value){if(!item.isTextual()||item.asText().length()>255)throw invalid("WORLD_PROPOSAL_FIELDS_INVALID");result.add(item.asText());}
        node.set(field,json.valueToTree(result));
    }
    public record State(long revision,JsonNode current,String reason,boolean contextChanged) {}
    public State state(World world,WorldAssistantService.ProposalData data) {
        var content=content(world);var target=target(world.getId(),content,data.resourceType,data.targetId,data.fieldKey);
        JsonNode current=target==null?null:target.fields();String reason=null;
        if(data.action.equals("CREATE")&&target!=null)reason="WORLD_PROPOSAL_TARGET_EXISTS";
        if(data.action.equals("UPDATE")&&target==null)reason="WORLD_PROPOSAL_TARGET_MISSING";
        if(data.action.equals("UPDATE")&&target!=null&&!Objects.equals(data.before,current))reason="WORLD_PROPOSAL_STALE";
        if(data.resourceType.equals("RELATION")) {
            var ids=content.entries().stream().map(Entry::id).collect(java.util.stream.Collectors.toSet());
            if(!ids.contains(id(data.candidate.path("sourceId")))||!ids.contains(id(data.candidate.path("targetId"))))reason="WORLD_PROPOSAL_ENDPOINT_MISSING";
        }
        boolean changed=!Objects.equals(data.worldHash,hash(content));
        if(data.resourceType.equals("OVERVIEW")&&changed)reason="WORLD_OVERVIEW_STALE";
        return new State(world.getRevision(),current,reason,changed);
    }
    public void apply(World world,WorldAssistantService.ProposalData data) {
        var content=content(world);var next=new ArrayList<>(content.entries());var links=new ArrayList<>(content.relations());var fields=data.candidate;
        if(data.resourceType.equals("ENTRY")) {
            var prior=next.stream().filter(e->e.id().equals(data.targetId)).findFirst().orElse(null);
            next.removeIf(e->e.id().equals(data.targetId));
            next.add(new Entry(data.targetId,fields.path("type").asText(),fields.path("title").asText(),fields.path("summary").asText(),fields.path("content").asText(),
                strings(fields.path("aliases")),strings(fields.path("tags")),prior==null?null:prior.legacyModuleKey(),prior==null?null:prior.legacyFieldKey()));
        } else if(data.resourceType.equals("RELATION")) {
            links.removeIf(r->r.id().equals(data.targetId));links.add(new Relation(data.targetId,id(fields.path("sourceId")),id(fields.path("targetId")),fields.path("type").asText(),fields.path("description").asText()));
        }
        entries.save(world,next,links,w->{
            if(data.resourceType.equals("OVERVIEW"))w.setOverview(fields.path("content").asText());
            if(data.resourceType.equals("METADATA"))switch(data.fieldKey) {
                case "name"->w.setName(fields.path("value").asText());case "tagline"->w.setTagline(fields.path("value").asText());
                case "themes"->w.setThemesJson(entries.write(strings(fields.path("value"))));
                case "creativeIntent"->w.setCreativeIntent(fields.path("value").asText());case "notes"->w.setNotes(fields.path("value").asText());
                default->throw invalid("WORLD_PROPOSAL_TARGET_INVALID");
            }
        });
        worlds.saveAndFlush(world);
    }
    private List<String> strings(JsonNode array) {var result=new ArrayList<String>();array.forEach(n->result.add(n.asText()));return result;}
    public static UUID id(JsonNode value) {try{return UUID.fromString(value.asText());}catch(Exception ex){throw invalid("WORLD_PROPOSAL_TARGET_INVALID");}}
}
