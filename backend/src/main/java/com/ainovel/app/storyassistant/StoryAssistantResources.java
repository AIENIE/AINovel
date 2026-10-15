package com.ainovel.app.storyassistant;

import com.ainovel.app.narrative.NarrativeSourceChanged;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.story.model.*;
import com.ainovel.app.story.repo.*;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import jakarta.persistence.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import static com.ainovel.app.material.MaterialFingerprintService.bytes;
import static com.ainovel.app.storyassistant.StoryAssistantErrors.*;

/** Applies only resource fields. It never writes manuscript content, knowledge grants or the canon ledger. */
@Service
public class StoryAssistantResources {
    private static final List<String> CHARACTER_FIELDS=List.of("name","synopsis","details","relationships","role","archetype");
    private static final Set<String> CHAPTER_PLANNING=Set.of("purpose","informationRelease","twistRole","selectedTwistId","revealFocus","tensionShift");
    private static final Set<String> SCENE_PLANNING=Set.of("minHan","maxHan","sceneType","goal","conflict","infoRelease","foreshadowId","revealFor","foreshadowHint","misdirectionAction","revealTrigger","payoffPlan","memeUsage");
    private final ResourceAccessGuard access;
    private final CharacterCardRepository characters;
    private final OutlineRepository outlines;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;
    @PersistenceContext private EntityManager em;
    public StoryAssistantResources(ResourceAccessGuard access,CharacterCardRepository characters,OutlineRepository outlines,
            ObjectMapper json,JdbcTemplate jdbc,ApplicationEventPublisher events){
        this.access=access;this.characters=characters;this.outlines=outlines;this.json=json;this.jdbc=jdbc;this.events=events;
    }
    public record Resource(String type,UUID id,UUID outlineId,UUID parentId,long revision,JsonNode fields) {}
    public record State(JsonNode current,Long revision,String reason) {}
    public JsonNode outlineSummary(UUID storyId,UUID outlineId){
        Outline outline=outline(storyId,outlineId,false);ObjectNode result=json.createObjectNode();
        result.put("id",outlineId.toString());result.put("title",outline.getTitle());result.put("revision",outline.getRevision());
        result.set("planning",tree(outline).path("planning"));return result;
    }
    public Story story(User user,UUID id,boolean lock){
        Story s=access.requireOwnedStory(id,user);
        if(lock)em.refresh(s,LockModeType.PESSIMISTIC_WRITE);
        return s;
    }
    public List<Resource> snapshot(User user,UUID storyId,UUID outlineId){
        Story story=story(user,storyId,false);var result=new ArrayList<Resource>();
        characters.findByStory(story).stream().sorted(Comparator.comparing(c->c.getId().toString())).forEach(c->
                result.add(new Resource("CHARACTER",c.getId(),null,null,c.getRevision(),character(c))));
        if(outlineId!=null){
            Outline outline=outline(storyId,outlineId,false);ObjectNode root=tree(outline);
            for(JsonNode c:root.path("chapters")){
                UUID chapter=id(c.path("id"));result.add(new Resource("CHAPTER",chapter,outlineId,null,outline.getRevision(),project("CHAPTER",c)));
                for(JsonNode s:c.path("scenes"))result.add(new Resource("SCENE",id(s.path("id")),outlineId,chapter,outline.getRevision(),project("SCENE",s)));
            }
        }
        return List.copyOf(result);
    }
    public State state(User user,UUID storyId,StoryAssistantService.ProposalData d,boolean lock){
        story(user,storyId,false);
        if(d.resourceType.equals("CHARACTER")){
            CharacterCard c=characters.findByIdWithStoryUser(d.targetId).orElse(null);
            if(c==null)return new State(null,null,d.action.equals("CREATE")?null:"STORY_RESOURCE_MISSING");
            if(!c.getStory().getId().equals(storyId))throw missing();
            if(lock)em.refresh(c,LockModeType.PESSIMISTIC_WRITE);
            return new State(character(c),c.getRevision(),d.action.equals("CREATE")?"STORY_RESOURCE_ALREADY_EXISTS":null);
        }
        Outline outline;
        try{outline=outline(storyId,d.outlineId,lock);}catch(com.ainovel.app.common.ApiStatusException ex){return new State(null,null,"STORY_RESOURCE_MISSING");}
        ObjectNode root=tree(outline);
        if(d.resourceType.equals("SCENE")&&find(root,"CHAPTER",d.parentId,null)==null)return new State(null,outline.getRevision(),"STORY_PARENT_CHANGED");
        JsonNode node=find(root,d.resourceType,d.targetId,d.parentId);
        if(d.action.equals("CREATE"))return new State(node==null?null:project(d.resourceType,node),outline.getRevision(),node==null?null:"STORY_RESOURCE_ALREADY_EXISTS");
        return new State(node==null?null:project(d.resourceType,node),outline.getRevision(),node==null?"STORY_RESOURCE_MISSING":null);
    }
    public Long apply(User user,UUID storyId,StoryAssistantService.ProposalData d,JsonNode value,boolean undo,Long expectedRevision){
        State current=state(user,storyId,d,true);
        if(current.reason()!=null)throw conflict(current.reason());
        if(!Objects.equals(current.revision(),expectedRevision))throw conflict("STORY_RESOURCE_CONFLICT");
        if(d.action.equals("UPDATE")&&!Objects.equals(current.current(),undo?d.acceptedCandidate:d.before))throw conflict("STORY_PROPOSAL_STALE");
        if(d.resourceType.equals("CHARACTER")){
            if(d.action.equals("CREATE")){
                try{jdbc.update("insert into character_cards(id,story_id,name,synopsis,details,relationships,role,archetype,revision,created_at,updated_at) values(?,?,?,?,?,?,?,?,0,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))",
                        bytes(d.targetId),bytes(storyId),text(value,"name"),text(value,"synopsis"),text(value,"details"),text(value,"relationships"),text(value,"role"),text(value,"archetype"));
                }catch(org.springframework.dao.DuplicateKeyException ex){throw conflict("STORY_RESOURCE_ALREADY_EXISTS");}
                return 0L;
            }
            CharacterCard c=characters.findByIdWithStoryUser(d.targetId).orElseThrow(StoryAssistantErrors::missing);
            c.setName(text(value,"name"));c.setSynopsis(text(value,"synopsis"));c.setDetails(text(value,"details"));
            c.setRelationships(text(value,"relationships"));c.setRole(text(value,"role"));c.setArchetype(text(value,"archetype"));
            characters.saveAndFlush(c);return c.getRevision();
        }
        Outline outline=outline(storyId,d.outlineId,false);ObjectNode root=tree(outline);
        if(d.action.equals("UPDATE")){
            ObjectNode node=(ObjectNode)find(root,d.resourceType,d.targetId,d.parentId);
            node.set("title",value.path("title"));node.set("summary",value.path("summary"));node.set("planning",value.path("planning").deepCopy());
        }else{
            ArrayNode siblings=d.resourceType.equals("CHAPTER")?array(root,"chapters"):array((ObjectNode)find(root,"CHAPTER",d.parentId,null),"scenes");
            ObjectNode added=value.deepCopy();added.put("id",d.targetId.toString());added.put("order",nextOrder(siblings));
            if(d.resourceType.equals("SCENE"))added.put("content","");
            else {int order=1;for(JsonNode scene:added.path("scenes")){((ObjectNode)scene).put("order",order++);((ObjectNode)scene).put("content","");}}
            siblings.add(added);
        }
        outline.setContentJson(write(root));outlines.saveAndFlush(outline);
        events.publishEvent(new NarrativeSourceChanged(null,outline.getId()));return outline.getRevision();
    }
    public JsonNode normalize(String type,String action,JsonNode raw,JsonNode before,JsonNode stableCandidate){
        if(raw==null||!raw.isObject())throw invalid("STORY_CANDIDATE_INVALID");
        Set<String> allowed=new HashSet<>(type.equals("CHARACTER")?CHARACTER_FIELDS:List.of("title","summary","planning"));
        if(type.equals("CHAPTER")&&action.equals("CREATE"))allowed.add("scenes");
        raw.fieldNames().forEachRemaining(k->{if(!allowed.contains(k))throw invalid("STORY_CANDIDATE_FIELD_INVALID");});
        ObjectNode result=before!=null&&before.isObject()?before.deepCopy():json.createObjectNode();
        for(String field:type.equals("CHARACTER")?CHARACTER_FIELDS:List.of("title","summary")){
            JsonNode supplied=raw.get(field);if(supplied!=null){if(!supplied.isNull()&&!supplied.isTextual())throw invalid("STORY_CANDIDATE_INVALID");result.set(field,supplied);}
            else if(!result.has(field))result.putNull(field);
            JsonNode v=result.get(field);if(v!=null&&v.isTextual()&&v.textValue().length()>(Set.of("name","title","role","archetype").contains(field)?255:30000))throw invalid("STORY_CANDIDATE_TOO_LARGE");
        }
        String required=type.equals("CHARACTER")?"name":"title";
        if(result.path(required).asText("").isBlank())throw invalid("STORY_CANDIDATE_NAME_REQUIRED");
        if(!type.equals("CHARACTER")){
            ObjectNode planning=result.path("planning").isObject()?result.path("planning").deepCopy():json.createObjectNode();
            if(raw.has("planning")){
                if(!raw.get("planning").isObject())throw invalid("STORY_CANDIDATE_INVALID");
                var fields=raw.get("planning").fields();while(fields.hasNext()){
                    var field=fields.next();if(!(type.equals("CHAPTER")?CHAPTER_PLANNING:SCENE_PLANNING).contains(field.getKey())){
                        if(Objects.equals(planning.get(field.getKey()),field.getValue()))continue;
                        throw invalid("STORY_CANDIDATE_FIELD_INVALID");
                    }
                    JsonNode v=field.getValue();if(!v.isNull()&&!v.isTextual()&&!Set.of("minHan","maxHan").contains(field.getKey()))throw invalid("STORY_CANDIDATE_INVALID");
                    planning.set(field.getKey(),v);
                }
            }
            if(type.equals("SCENE")){
                SceneLengthRange.from(json.convertValue(planning,Map.class));
                if(planning.hasNonNull("sceneType")&&!planning.path("sceneType").asText().isBlank()&&!SceneType.isExplicitlySupported(planning.path("sceneType").asText()))throw invalid("STORY_CANDIDATE_INVALID");
            }
            result.set("planning",planning);
        }
        if(type.equals("CHAPTER")&&action.equals("CREATE")){
            JsonNode scenes=raw.has("scenes")?raw.get("scenes"):stableCandidate==null?json.createArrayNode():stableCandidate.path("scenes");
            if(!scenes.isArray()||scenes.size()>20)throw invalid("STORY_CANDIDATE_INVALID");
            if(stableCandidate!=null&&scenes.size()!=stableCandidate.path("scenes").size())throw invalid("STORY_CANDIDATE_STRUCTURE_CHANGED");
            ArrayNode children=json.createArrayNode();int index=0;
            for(JsonNode child:scenes){
                if(!child.isObject())throw invalid("STORY_CANDIDATE_INVALID");ObjectNode fields=child.deepCopy();JsonNode givenId=fields.remove("id");
                UUID childId=stableCandidate==null?UUID.randomUUID():id(stableCandidate.path("scenes").get(index).path("id"));
                if(stableCandidate!=null&&givenId!=null&&!childId.toString().equals(givenId.asText()))throw invalid("STORY_CANDIDATE_STRUCTURE_CHANGED");
                JsonNode previousChild=stableCandidate==null?null:stableCandidate.path("scenes").get(index);
                ObjectNode normalized=(ObjectNode)normalize("SCENE","CREATE",fields,previousChild,null);normalized.put("id",childId.toString());children.add(normalized);index++;
            }
            result.set("scenes",children);
        }
        return result;
    }
    private Outline outline(UUID story,UUID id,boolean lock){
        if(id==null)throw missing();Outline o=(lock?outlines.findByIdForUpdate(id):outlines.findByIdWithStoryUser(id)).orElseThrow(StoryAssistantErrors::missing);
        if(!o.getStory().getId().equals(story))throw missing();if(lock)em.refresh(o,LockModeType.PESSIMISTIC_WRITE);return o;
    }
    private ObjectNode character(CharacterCard c){ObjectNode n=json.createObjectNode();n.put("name",c.getName());n.put("synopsis",c.getSynopsis());n.put("details",c.getDetails());n.put("relationships",c.getRelationships());n.put("role",c.getRole());n.put("archetype",c.getArchetype());return n;}
    private ObjectNode project(String type,JsonNode node){ObjectNode out=json.createObjectNode();out.set("title",node.path("title").isMissingNode()?NullNode.instance:node.get("title"));out.set("summary",node.path("summary").isMissingNode()?NullNode.instance:node.get("summary"));out.set("planning",node.path("planning").isObject()?node.path("planning").deepCopy():json.createObjectNode());return out;}
    private ObjectNode tree(Outline o){try{JsonNode n=json.readTree(o.getContentJson());if(!n.isObject()||!n.path("chapters").isArray())throw new IllegalArgumentException();return (ObjectNode)n;}catch(Exception e){throw new IllegalStateException("STORY_OUTLINE_INVALID");}}
    private static JsonNode find(ObjectNode root,String type,UUID id,UUID parent){
        for(JsonNode c:root.path("chapters")){
            if(type.equals("CHAPTER")&&c.path("id").asText().equals(id.toString()))return c;
            if(type.equals("SCENE")&&(parent==null||c.path("id").asText().equals(parent.toString())))for(JsonNode s:c.path("scenes"))if(s.path("id").asText().equals(id.toString()))return s;
        }return null;
    }
    private static ArrayNode array(ObjectNode parent,String field){if(!parent.path(field).isArray())parent.set(field,JsonNodeFactory.instance.arrayNode());return (ArrayNode)parent.get(field);}
    private static int nextOrder(ArrayNode nodes){int max=0;for(JsonNode n:nodes)max=Math.max(max,n.path("order").asInt(0));return Math.addExact(max,1);}
    static UUID id(JsonNode n){try{return UUID.fromString(n.asText());}catch(Exception e){throw invalid("STORY_RESOURCE_ID_INVALID");}}
    private static String text(JsonNode n,String field){return n.path(field).isNull()?null:n.path(field).asText();}
    private String write(Object n){try{return json.writeValueAsString(n);}catch(Exception e){throw new IllegalStateException(e);}}
}
