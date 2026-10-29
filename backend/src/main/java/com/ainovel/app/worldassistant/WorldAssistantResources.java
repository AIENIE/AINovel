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
        var timeline=content.timeline()==null?WorldTimeline.empty():content.timeline();
        for(var era:timeline.eras()) {
            ObjectNode fields=json.valueToTree(era);fields.remove("id");result.add(new Resource("ERA",era.id(),null,era.name(),fields));
        }
        for(var event:timeline.events()) {
            ObjectNode fields=json.valueToTree(event);fields.remove("id");result.add(new Resource("EVENT",event.id(),null,event.title(),fields));
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
            case "ERA"->Set.of("name","description","date","sortOrder");
            case "EVENT"->Set.of("title","summary","content","aliases","tags","date","entryIds","eraIds","sortOrder");
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
        } else if(type.equals("ERA")||type.equals("EVENT")) {
            text(result,type.equals("ERA")?"name":"title",true,255);
            if(type.equals("ERA"))text(result,"description",false,100000);
            else {text(result,"summary",false,100000);text(result,"content",false,100000);strings(result,"aliases");strings(result,"tags");ids(result,"entryIds");ids(result,"eraIds");}
            if(result.hasNonNull("sortOrder")&&(!result.path("sortOrder").isIntegralNumber()||!result.path("sortOrder").canConvertToInt()))throw invalid("WORLD_PROPOSAL_FIELDS_INVALID");
            if(!result.hasNonNull("sortOrder"))result.put("sortOrder",0);
            date(result.path("date"));
            result.set("date",json.valueToTree(WorldTimeline.normalizeDate(result.hasNonNull("date")?json.convertValue(result.path("date"),DateRange.class):null)));
        } else text(result,"content",true,100000);
        return result;
    }
    private void ids(ObjectNode node,String field) {
        if(!node.hasNonNull(field)){node.putArray(field);return;}
        if(!node.path(field).isArray()||node.path(field).size()>1000)throw invalid("WORLD_PROPOSAL_FIELDS_INVALID");
        var values=new LinkedHashSet<UUID>();for(var value:node.path(field))values.add(id(value));node.set(field,json.valueToTree(values));
    }
    private void date(JsonNode value) {
        if(value.isMissingNode()||value.isNull())return;
        if(!value.isObject())throw invalid("WORLD_PROPOSAL_FIELDS_INVALID");
        value.fieldNames().forEachRemaining(key->{if(!Set.of("start","end").contains(key))throw invalid("WORLD_PROPOSAL_FIELDS_INVALID");});
        for(String key:List.of("start","end")) {
            var point=value.path(key);if(point.isMissingNode()||point.isNull())continue;
            if(!point.isObject())throw invalid("WORLD_PROPOSAL_FIELDS_INVALID");
            point.fieldNames().forEachRemaining(field->{if(!Set.of("year","month","day").contains(field))throw invalid("WORLD_PROPOSAL_FIELDS_INVALID");});
            for(String field:List.of("year","month","day"))if(point.hasNonNull(field)&&(!point.path(field).isIntegralNumber()||!point.path(field).canConvertToInt()))throw invalid("WORLD_PROPOSAL_FIELDS_INVALID");
        }
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
        if(data.resourceType.equals("EVENT")) {
            var entryIds=content.entries().stream().map(Entry::id).collect(java.util.stream.Collectors.toSet());
            var eraIds=(content.timeline()==null?WorldTimeline.empty():content.timeline()).eras().stream().map(Era::id).collect(java.util.stream.Collectors.toSet());
            for(var ref:data.candidate.path("entryIds"))if(!entryIds.contains(id(ref)))reason="WORLD_PROPOSAL_DEPENDENCY_MISSING";
            for(var ref:data.candidate.path("eraIds"))if(!eraIds.contains(id(ref)))reason="WORLD_PROPOSAL_DEPENDENCY_MISSING";
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
            if(data.resourceType.equals("ERA")||data.resourceType.equals("EVENT")) {
                var current=content.timeline()==null?WorldTimeline.empty():content.timeline();
                var eras=new ArrayList<>(current.eras());var events=new ArrayList<>(current.events());
                var value=((ObjectNode)fields.deepCopy()).put("id",data.targetId.toString());
                if(data.resourceType.equals("ERA")){eras.removeIf(e->e.id().equals(data.targetId));eras.add(json.convertValue(value,Era.class));}
                else {events.removeIf(e->e.id().equals(data.targetId));events.add(json.convertValue(value,Event.class));}
                var timeline=WorldTimeline.normalize(new Timeline(1,current.calendarName(),eras,events),next.stream().map(Entry::id).collect(java.util.stream.Collectors.toSet()));
                w.setTimelineJson(timeline==null?null:entries.write(timeline));
            }
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
