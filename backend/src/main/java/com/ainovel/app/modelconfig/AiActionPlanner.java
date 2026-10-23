package com.ainovel.app.modelconfig;

import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;

/** The HTTP action, never a client-supplied feature key, determines all possible priced steps. */
@Component
public class AiActionPlanner {
    public record Step(String featureKey,boolean conditional,String calls,int estimatedInput,int estimatedOutput) {}
    public record Plan(String target,List<Step> steps,String businessHash,String businessScope,com.ainovel.app.world.WorldBindingService.Frozen worldInput,com.ainovel.app.worldassistant.WorldAssistantService.Frozen worldAssistantInput) {
        public Plan(String target,List<Step> steps,String hash,String scope){this(target,steps,hash,scope,null,null);}
    }
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.ainovel.app.world.WorldBindingService worldBindings;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.ainovel.app.quality.language.LanguageFeature language;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.ainovel.app.manuscript.context.SceneDraftContextCompiler compiler;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.ainovel.app.manuscript.repo.ManuscriptRepository manuscripts;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.ainovel.app.manuscript.ManuscriptContentService contents;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    @org.springframework.context.annotation.Lazy
    private com.ainovel.app.worldassistant.WorldAssistantService worldAssistant;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public AiActionPlanner(JdbcTemplate jdbc,ObjectMapper json) {this.jdbc=jdbc;this.json=json;}
    public List<String> features(String target,JsonNode body) {
        String path=target.split("\\?",2)[0]; if(path.startsWith("/api/"))path=path.substring(4);
        var keys=new LinkedHashSet<String>();
        if(path.equals("/v1/creation-workflows")&&body.path("autoRun").asBoolean()) {
            keys.addAll(List.of("guided.premise","guided.world","guided.json_repair"));
        } else if(path.equals("/v1/conception")||path.equals("/v1/conception/operations"))keys.add("story.conception");
        else if(path.matches("/v1/story-cards/[^/]+/refine"))keys.add("story.refine");
        else if(path.matches("/v1/character-cards/[^/]+/refine"))keys.add("character.refine");
        else if(path.matches("/v1/outlines/scenes/[^/]+/refine"))keys.add("outline.scene_refine");
        else if(path.matches("/v1/outlines/[^/]+/chapters(/operations)?"))keys.add("outline.chapter");
        else if(path.matches("/v1/worlds/[^/]+/modules/[^/]+/fields/[^/]+/refine")) {
            String[] p=path.split("/"); keys.add("world."+p[5]+"."+p[7]+".refine");
        } else if(path.matches("/v1/worlds/[^/]+/entries/[^/]+/(generate/operations|refine(/operations)?)")) {
            keys.add("world.entry."+(path.contains("/refine")?"refine":"generate"));
        } else if(path.matches("/v1/worlds/[^/]+/(publish/operations|generation/[^/]+(/operations|/retry)?)")) {
            var p=path.split("/");var module=p[4].equals("generation")?p[5]:null;
            for(var f:AiFeatures.all())if(f.key().startsWith("world.")&&!f.key().startsWith("world.entry.")&&f.key().endsWith(".generate")&&(module==null||f.key().startsWith("world."+module+".")))keys.add(f.key());
        } else if(path.matches("/v1/worlds/[^/]+/overview/integrate/operations"))keys.add("world.overview.integrate");
        else if(path.matches("/v1/worlds/[^/]+/assistant/turns")) {
            String intent=body.path("intent").asText("DISCUSS").toLowerCase(Locale.ROOT);
            if(!Set.of("discuss","propose","refine").contains(intent))throw new com.ainovel.app.common.ApiStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"AI_ACTION_INVALID");
            keys.add("world_assistant."+intent+".plan");keys.add("world_assistant."+intent+".final");
        } else if(path.matches("/v1/creation-workflows/[^/]+/(steps/[^/]+/generate|steps/outline/directions/[^/]+/(develop|expand)|auto-run)")) {
            if(path.endsWith("/auto-run")){keys.addAll(automaticGuidedSteps());}
            else if(path.endsWith("/develop"))keys.add("rewrite".equalsIgnoreCase(body.path("action").asText())?"guided.outline_rewrite":"guided.outline_develop");
            else if(path.endsWith("/expand"))keys.add("guided.outline_expand");
            else {String[] p=path.split("/");keys.add("guided."+p[5].toLowerCase(Locale.ROOT));}
            keys.add("guided.json_repair");
        } else if(path.matches("/v1/manuscripts/[^/]+/scenes/[^/]+/generate(/operations)?")||path.matches("/v1/manuscript/scenes/[^/]+/generate")) {
            String mode="crafted".equalsIgnoreCase(queryParameter(target,"mode"))?"crafted":"fast";
            keys.add("scene."+mode+".draft");keys.add("scene."+mode+".length");generationQuality(keys);
        } else if(path.matches("/v1/g2-evaluations/[^/]+/samples")) {
            for(String mode:List.of("fast","crafted")){keys.add("scene."+mode+".draft");keys.add("scene."+mode+".length");} keys.add("slop.judge");
        } else if(path.matches("/v1/manuscripts/[^/]+/scenes/[^/]+/assistant/turns")) {
            String intent=body.path("intent").asText("AUTO").toLowerCase(Locale.ROOT);
            keys.add("assistant."+intent+".plan");keys.add("assistant."+intent+".final");
        } else if(path.matches("/v1/story-cards/[^/]+/assistant/turns")) {
            String intent=body.path("intent").asText().toLowerCase(Locale.ROOT);
            keys.add("story_assistant."+intent+".plan");keys.add("story_assistant."+intent+".final");
        } else if(path.matches("/v2/manuscripts/[^/]+/scenes/[^/]+/quality-runs/language/operations"))keys.add("language.diagnose");
        else if(path.matches("/v2/manuscripts/[^/]+/quality-runs/language/[^/]+/issues/[^/]+/suggestions/operations")){keys.add("language.revise");keys.add("language.review");}
        else if(path.matches("/v2/manuscripts/[^/]+/quality-runs/[^/]+/issues/[^/]+/suggestions/operations")){keys.add("language.revise");keys.add("language.review");}
        else if(path.matches("/v2/manuscripts/[^/]+/branches/[^/]+/narrative/scene-approvals"))keys.add("narrative.extract");
        else if(path.matches("/v2/manuscripts/[^/]+/scenes/[^/]+/quality-runs(/operations)?"))keys.add("slop.diagnose");
        else if(path.matches("/v2/manuscripts/[^/]+/slop-drift-runs(/operations)?"))keys.add("slop.drift");
        else if(path.matches("/v2/manuscripts/[^/]+/scenes/[^/]+/plot-quality-runs(/operations)?"))keys.add("plot.diagnose");
        else if(path.matches("/v2/manuscripts/[^/]+/plot-quality-runs/[^/]+/revision-candidate(/operations)?"))keys.add("plot.revise");
        else if(path.equals("/v1/ai/chat"))keys.add("generic.chat");
        else if(path.equals("/v1/ai/refine"))keys.add("generic.refine");
        else if(path.equals("/v1/ai/version-summary"))keys.add("versions.summary");
        else if(path.equals("/v1/ai/outline-refine"))keys.add(body.path("kind").asText().equals("scene")?"outline.scene_refine":"outline.chapter_refine");
        try{keys.forEach(AiFeatures::require);}catch(IllegalArgumentException e){throw new com.ainovel.app.common.ApiStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"AI_ACTION_INVALID");}
        return List.copyOf(keys);
    }
    private static void generationQuality(Set<String> keys) {keys.addAll(List.of("slop.judge","plot.diagnose","language.diagnose"));}
    public boolean candidate(String method,String target) {
        if(!"POST".equals(method)||target.contains("/admin"))return false;
        if(target.equals("/v1/creation-workflows")||target.matches(".*/assistant/turns"))return true;
        try{return !features(target,json.createObjectNode()).isEmpty();}
        catch(com.ainovel.app.common.ApiStatusException e){return true;}
    }
    @org.springframework.transaction.annotation.Transactional(readOnly=true)
    public Plan plan(User user,String target,JsonNode body) {
        var keys=new ArrayList<>(features(target,body));
        if(keys.isEmpty())throw AiModelConfigurationService.error("AI_ACTION_INVALID");
        String state=businessState(user,target,body);
        if(target.matches("/v1/worlds/[^/]+/entries/[^/]+/(generate/operations|refine(/operations)?)")) {
            var parts=target.split("/");String action=target.contains("/refine")?"refine":"generate";
            var origins=jdbc.query("select legacy_module_key,legacy_field_key,content from world_entries where world_id=? and id=?",
                (rs,i)->{var row=new HashMap<String,String>();row.put("legacy_module_key",rs.getString(1));row.put("legacy_field_key",rs.getString(2));row.put("content",rs.getString(3));return row;},
                AiModelConfigurationService.bytes(UUID.fromString(parts[3])),AiModelConfigurationService.bytes(UUID.fromString(parts[5])));
            if(origins.isEmpty())throw AiModelConfigurationService.error("WORLD_ENTRY_NOT_FOUND");
            var entry=origins.getFirst();String module=(String)entry.get("legacy_module_key"),field=(String)entry.get("legacy_field_key");
            String legacy="world."+module+"."+field+"."+action;
            if(AiFeatures.all().stream().anyMatch(f->f.key().equals(legacy))){keys.clear();keys.add(legacy);}
            if(action.equals("generate")&&!Objects.toString(entry.get("content"),"").isBlank())throw AiModelConfigurationService.error("WORLD_ENTRY_NOT_EMPTY");
        }
        String scope=null;
        try {
            if(target.endsWith("/auto-run")) {
                var row=json.readTree(state).get(0);String current=row.path("current_step").asText();
                var stages=List.of("PREMISE","WORLD","CHARACTERS","OUTLINE");int index=stages.indexOf(current);
                if(index<0)keys.clear();
                else {
                    for(int i=0;i<index;i++)keys.remove("guided."+stages.get(i).toLowerCase(Locale.ROOT));
                    var stepData=json.readTree(row.path("steps_json").asText("{}")).path(current);
                    if(index<=1)keys.removeAll(List.of("guided.characters","guided.outline","guided.outline_expand"));
                    if(stepData.path("awaitingPublication").asBoolean())keys.clear();
                    if(!stepData.path("candidates").isEmpty())keys.remove("guided."+current.toLowerCase(Locale.ROOT));
                    if(current.equals("OUTLINE")&&stepData.path("outlinePhase").asText().equals("OUTLINE_PREVIEW"))keys.remove("guided.outline_expand");
                    if(keys.stream().allMatch(key->key.equals("guided.json_repair")))keys.clear();
                }
            }
            if(target.matches("/v1/manuscript/scenes/[^/]+/generate")) {
                var rows=json.readTree(state);scope=uuidFromJson(rows.get(0).path("id"));
            }
            if(target.contains("/worlds/")&&!target.contains("/entries/")&&!keys.isEmpty()&&keys.getFirst().startsWith("world.")&&keys.getFirst().endsWith(".generate")) {
                var row=json.readTree(state).get(0);var modules=json.readTree(row.path("modules_json").asText("{}"));
                keys.removeIf(key->{var parts=key.split("\\.");var module=modules.path(parts[1]);
                    if(target.contains("/publish/")&&module.isObject())for(var field:module)if(!field.asText("").isBlank())return true;
                    return !module.path(parts[2]).asText("").isBlank();});
            }
            if(keys.contains("language.diagnose")&&(target.contains("/generate")||target.contains("/g2-evaluations/"))) {
                // The G2 path never schedules the ordinary post-generation language job.
                var match=Pattern.compile("/manuscripts/([0-9a-fA-F-]{36})/").matcher(target);
                UUID manuscript=match.find()?UUID.fromString(match.group(1)):scope==null?null:UUID.fromString(scope);
                UUID story=null;
                if(manuscript!=null) {
                    var found=jdbc.query("select o.story_id from manuscripts m join outlines o on m.outline_id=o.id where m.id=?",(rs,i)->uuid(rs.getBytes(1)),AiModelConfigurationService.bytes(manuscript));
                    if(!found.isEmpty())story=found.getFirst();
                }
                if(language==null||story==null||!language.checkAfterGeneration(story)||target.contains("/g2-evaluations/"))keys.remove("language.diagnose");
                if(language!=null&&story!=null&&language.replacesLegacy(story)&&!target.contains("/g2-evaluations/")){keys.remove("slop.judge");}
            }
        } catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}
        List<Step> steps=new ArrayList<>();
        int input=Math.max(1,com.ainovel.app.manuscript.context.SceneDraftContextCompiler.estimateTokens(state+body));
        for(int i=0;i<keys.size();i++) {
            var k=keys.get(i);
            String calls=k.equals("language.diagnose")?"CHUNKS":target.contains("/g2-evaluations/")&&k.startsWith("slop.")?"QUALITY":k.startsWith("world.")?"ONE":k.equals("guided.json_repair")?"PER_STEP":
                    target.contains("/g2-evaluations/")&&k.endsWith(".length")?"UP_TO_TWO":"ONE";
            boolean conditional=k.startsWith("world_assistant.")&&k.endsWith(".final")||k.endsWith(".length")||k.equals("guided.json_repair")||(target.endsWith("/auto-run")||target.equals("/v1/creation-workflows"))&&i>0
                    ||(target.contains("/generate")||target.contains("/g2-evaluations/"))&&(k.startsWith("slop.")||k.startsWith("plot.")||k.startsWith("language."));
            int output=k.startsWith("scene.")?sceneOutputTokens(target,body,state):4096;
            steps.add(new Step(k,conditional,calls,input,output));
        }
        com.ainovel.app.worldassistant.WorldAssistantService.Frozen assistantInput=null;
        if(worldAssistant!=null&&target.matches("/v1/worlds/[^/]+/(assistant/turns|entries/[^/]+/refine/operations|overview/integrate/operations)")) {
            String[] parts=target.split("/");boolean single=!target.endsWith("/assistant/turns");
            assistantInput=worldAssistant.validateQuote(user,UUID.fromString(parts[3]),target.contains("/entries/")?UUID.fromString(parts[5]):null,body,single);
        }
        String compiledHash="";
        com.ainovel.app.world.WorldBindingService.Frozen worldInput=null;
        if(compiler!=null&&manuscripts!=null&&contents!=null) {
            var scene=Pattern.compile("/manuscripts/([0-9a-fA-F-]{36})/scenes/([0-9a-fA-F-]{36})/(generate|assistant)").matcher(target);
            var legacyScene=Pattern.compile("/manuscript/scenes/([0-9a-fA-F-]{36})/generate").matcher(target);
            UUID manuscriptId=null,sceneId=null;
            if(scene.find()){manuscriptId=UUID.fromString(scene.group(1));sceneId=UUID.fromString(scene.group(2));}
            else if(scope!=null&&legacyScene.find()){manuscriptId=UUID.fromString(scope);sceneId=UUID.fromString(legacyScene.group(1));}
            if(manuscriptId!=null) {
                var manuscript=manuscripts.findById(manuscriptId).orElseThrow();
                if(worldBindings!=null)worldInput=worldBindings.freeze(manuscript);
                var compiled=compiler.compile(manuscript,sceneId,contents.readAll(manuscript),
                    com.ainovel.app.manuscript.context.SceneDraftContextCompiler.DEFAULT_TOKEN_BUDGET,worldInput);
                compiledHash=compiled.manifest().contextHash();
            }
        }
        return new Plan(target,List.copyOf(steps),hash(state+keys+compiledHash+json.valueToTree(worldInput).toString()),scope,worldInput,assistantInput);
    }
    private static List<String> automaticGuidedSteps() {
        return List.of("guided.premise","guided.world","guided.characters","guided.outline","guided.outline_expand","guided.json_repair");
    }
    private String businessState(User user,String target,JsonNode body) {
        // Related rows participate in freshness checks without exposing their content in the quote view.
        var match=Pattern.compile("/(manuscripts|story-cards|character-cards|outlines|worlds|creation-workflows|scenes)/([0-9a-fA-F-]{36})(?:/|$)").matcher(target.split("\\?",2)[0]);
        String kind=null,id=null;
        if(match.find()){kind=match.group(1);id=match.group(2);}
        else if(body.hasNonNull("manuscriptId")){kind="manuscripts";id=body.path("manuscriptId").asText();}
        if(target.matches("/v1/manuscript/scenes/[^/]+/generate")) {
            var found=jdbc.query("select m.id from manuscripts m join outlines o on m.outline_id=o.id join stories s on o.story_id=s.id where s.user_id=? order by m.id limit 1",(rs,i)->uuid(rs.getBytes(1)),AiModelConfigurationService.bytes(user.getId()));
            if(found.isEmpty())throw AiModelConfigurationService.error("AI_RESOURCE_NOT_FOUND");
            kind="manuscripts";id=found.getFirst().toString();
        }
        // Outline scenes live in outline JSON; this legacy text-refine route receives its full input.
        if("scenes".equals(kind))kind=null;
        List<Map<String,Object>> templates=new ArrayList<>(jdbc.queryForList("select * from prompt_templates where user_id=?",AiModelConfigurationService.bytes(user.getId())));
        if(target.contains("/worlds/"))templates.addAll(jdbc.queryForList("select * from world_prompt_templates where user_id=?",AiModelConfigurationService.bytes(user.getId())));
        if(kind==null)return writeState(templates);
        String sql=switch(kind) {
            case "manuscripts"->"select m.* from manuscripts m join outlines o on m.outline_id=o.id join stories s on o.story_id=s.id where m.id=? and s.user_id=?";
            case "outlines"->"select o.* from outlines o join stories s on o.story_id=s.id where o.id=? and s.user_id=?";
            case "character-cards"->"select c.* from character_cards c join stories s on c.story_id=s.id where c.id=? and s.user_id=?";
            case "story-cards"->"select s.* from stories s where s.id=? and s.user_id=?";
            case "worlds"->"select w.* from worlds w where w.id=? and w.user_id=?";
            default->"select r.* from creation_workflow_runs r where r.id=? and r.user_id=?";
        };
        UUID resource;
        try{resource=UUID.fromString(id);}catch(RuntimeException e){throw AiModelConfigurationService.error("AI_ACTION_INVALID");}
        var rows=jdbc.queryForList(sql,AiModelConfigurationService.bytes(resource),AiModelConfigurationService.bytes(user.getId()));
        if(rows.isEmpty())throw new com.ainovel.app.common.ApiStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"AI_RESOURCE_NOT_FOUND");
        if("worlds".equals(kind)) {
            rows.addAll(jdbc.queryForList("select * from world_entries where world_id=? order by id",AiModelConfigurationService.bytes(resource)));
            rows.addAll(jdbc.queryForList("select * from world_relations where world_id=? order by id",AiModelConfigurationService.bytes(resource)));
        }
        byte[] storyId=switch(kind) {
            case "story-cards"->AiModelConfigurationService.bytes(resource);
            case "character-cards","outlines"->(byte[])rows.getFirst().get("story_id");
            case "manuscripts"->jdbc.query("select story_id from outlines where id=?",(rs,i)->rs.getBytes(1),rows.getFirst().get("outline_id")).getFirst();
            default->null;
        };
        if(storyId!=null) {
            rows.addAll(jdbc.queryForList("select * from stories where id=?",storyId));
            rows.addAll(jdbc.queryForList("select * from character_cards where story_id=? order by id",storyId));
            rows.addAll(jdbc.queryForList("select * from outlines where story_id=? order by id",storyId));
            rows.addAll(jdbc.queryForList("select * from language_quality_settings where story_id=?",storyId));
        }
        if("manuscripts".equals(kind)) {
            var report=Pattern.compile("/plot-quality-runs/([0-9a-fA-F-]{36})").matcher(target);
            if(report.find())rows.addAll(jdbc.queryForList("select * from plot_quality_runs where id=? and manuscript_id=?",AiModelConfigurationService.bytes(UUID.fromString(report.group(1))),AiModelConfigurationService.bytes(resource)));
            var languageReport=Pattern.compile("/quality-runs/language/([0-9a-fA-F-]{36})").matcher(target);
            if(languageReport.find())rows.addAll(jdbc.queryForList("select * from language_quality_reports where id=? and manuscript_id=?",AiModelConfigurationService.bytes(UUID.fromString(languageReport.group(1))),AiModelConfigurationService.bytes(resource)));
            if(target.contains("/assistant/"))rows.addAll(jdbc.queryForList("select t.* from writing_assistant_turns t join writing_assistant_sessions s on t.session_id=s.id where s.user_id=? and s.manuscript_id=? order by t.created_at,t.id",AiModelConfigurationService.bytes(user.getId()),AiModelConfigurationService.bytes(resource)));
        }
        if("story-cards".equals(kind)&&target.contains("/assistant/")&&body.hasNonNull("sessionId")) {
            try {rows.addAll(jdbc.queryForList("select t.* from story_assistant_turns t join story_assistant_sessions s on t.session_id=s.id where s.user_id=? and s.story_id=? and s.id=? order by t.created_at,t.id",AiModelConfigurationService.bytes(user.getId()),AiModelConfigurationService.bytes(resource),AiModelConfigurationService.bytes(UUID.fromString(body.path("sessionId").asText()))));}
            catch(IllegalArgumentException e){throw AiModelConfigurationService.error("AI_ACTION_INVALID");}
        }
        if("worlds".equals(kind)&&target.contains("/assistant/")) {
            rows.addAll(jdbc.queryForList("select t.* from world_assistant_turns t join world_assistant_sessions s on t.session_id=s.id where s.world_id=? and s.user_id=? order by t.created_at,t.id",AiModelConfigurationService.bytes(resource),AiModelConfigurationService.bytes(user.getId())));
            rows.addAll(jdbc.queryForList("select p.* from world_assistant_proposals p join world_assistant_turns t on p.turn_id=t.id join world_assistant_sessions s on t.session_id=s.id where s.world_id=? and s.user_id=? order by p.id",AiModelConfigurationService.bytes(resource),AiModelConfigurationService.bytes(user.getId())));
        }
        rows.addAll(templates);return writeState(rows);
    }
    private String writeState(List<Map<String,Object>> rows) {
        for(var row:rows)row.replaceAll((column,value)->{
            if(value instanceof java.sql.Clob clob)try{return clob.getSubString(1,(int)clob.length());}catch(java.sql.SQLException e){throw new IllegalStateException(e);}
            return value;
        });
        try{return json.writeValueAsString(rows);}catch(Exception e){throw new IllegalStateException(e);}
    }
    private static UUID uuid(byte[] bytes){var b=java.nio.ByteBuffer.wrap(bytes);return new UUID(b.getLong(),b.getLong());}
    private static String uuidFromJson(JsonNode value) {try{return uuid(value.binaryValue()).toString();}catch(java.io.IOException e){throw new IllegalStateException(e);}}
    private static String queryParameter(String target,String key) {
        if(!target.contains("?"))return "";
        for(String part:target.substring(target.indexOf('?')+1).split("&")) {
            String[] pair=part.split("=",2);
            if(java.net.URLDecoder.decode(pair[0],StandardCharsets.UTF_8).equals(key))return pair.length==1?"":java.net.URLDecoder.decode(pair[1],StandardCharsets.UTF_8);
        }
        return "";
    }
    private int sceneOutputTokens(String target,JsonNode body,String state) {
        String sceneId=body.path("sceneId").asText(null);
        var scene=Pattern.compile("/scenes/([0-9a-fA-F-]{36})/").matcher(target);
        if(scene.find())sceneId=scene.group(1);
        if(sceneId==null)return 4096;
        try {
            for(var row:json.readTree(state)) {
                if(!row.hasNonNull("content_json"))continue;
                var outline=json.readTree(row.path("content_json").asText());
                for(var chapter:outline.path("chapters"))for(var item:chapter.path("scenes"))if(sceneId.equals(item.path("id").asText())) {
                    Map<String,Object> planning=item.hasNonNull("planning")?json.convertValue(item.path("planning"),new com.fasterxml.jackson.core.type.TypeReference<>(){}):Map.of();
                    int max=com.ainovel.app.story.model.SceneLengthRange.from(planning).maxHan();
                    return com.ainovel.app.manuscript.context.SceneDraftContextCompiler.estimateTokens("汉".repeat(max));
                }
            }
        }catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}
        return 4096;
    }
    public static String hash(String value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException(e);}
    }
}
