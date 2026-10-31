package com.ainovel.app.assistant;

import com.ainovel.app.common.ApiStatusException;
import com.ainovel.app.manuscript.ManuscriptContentService;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.context.SceneDraftContextCompiler;
import com.ainovel.app.narrative.*;
import com.ainovel.app.story.repo.CharacterCardRepository;
import com.ainovel.app.story.model.SceneLengthRange;
import com.ainovel.app.world.WorldBindingService;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.*;
import static com.ainovel.app.assistant.AssistantDtos.*;
import static com.ainovel.app.quality.language.LanguageProjection.hash;

/** All retrieval operates on a frozen, owned pool; no paid retrieval or unscoped lookup. */
@Service
public class AssistantContext {
    public static final int BUDGET=3500;
    private static final int READ_METADATA_BUDGET=128;
    private final ObjectMapper json;
    private final ManuscriptContentService contents;
    private final NarrativeContextService narrative;
    private final CharacterCardRepository characters;
    private final WorldBindingService worlds;
    @org.springframework.beans.factory.annotation.Autowired private com.ainovel.app.worldcontext.WorldContextSelector worldSelector;
    @org.springframework.beans.factory.annotation.Autowired private com.ainovel.app.worldcontext.WorldContextPreferences worldPreferences;
    public AssistantContext(ObjectMapper json,ManuscriptContentService contents,NarrativeContextService narrative,
            CharacterCardRepository characters,WorldBindingService worlds){this.json=json;this.contents=contents;this.narrative=narrative;this.characters=characters;this.worlds=worlds;}
    public record Snapshot(boolean h2,NarrativeContextDtos.Stamp stamp,List<Source> base,List<Source> editPool,List<Source> authorPool,
            int minHan,int maxHan,com.ainovel.app.worldcontext.WorldContextDtos.Selection worldContext) {
        public Snapshot(boolean h2,NarrativeContextDtos.Stamp stamp,List<Source> base,List<Source> editPool,List<Source> authorPool,int minHan,int maxHan){this(h2,stamp,base,editPool,authorPool,minHan,maxHan,null);}
    }
    public record Reads(String intent,List<Source> sources,List<String> warnings) {}
    public Snapshot freeze(User user,Manuscript m,UUID sceneId){
        return freeze(user,m,sceneId,worlds.input(m),"","");
    }
    public Snapshot freeze(User user,Manuscript m,UUID sceneId,String instruction,String text){return freeze(user,m,sceneId,worlds.input(m),instruction,text);}
    private Snapshot freeze(User user,Manuscript m,UUID sceneId,WorldBindingService.Frozen input,String instruction,String selectedText){
        var root=tree(m.getOutline().getContentJson());JsonNode selected=null,selectedChapter=null;var prior=new ArrayList<Source>();
        boolean found=false;
        for(var chapter:ordered(root.path("chapters")))for(var scene:ordered(chapter.path("scenes"))){
            UUID id=id(scene.path("id").asText());
            if(sceneId.equals(id)){selected=scene;selectedChapter=chapter;found=true;break;}
            if(!found&&id!=null){String text=plain(contents.readScene(m,id));if(!text.isBlank())prior.add(source("scene:"+id,"PREVIOUS_SCENE",chapter.path("title").asText()+" / "+scene.path("title").asText(),text,id));}
        }
        if(selected==null)throw error("ASSISTANT_SCENE_MISSING");
        var range=SceneLengthRange.from(selected.path("planning").isObject()?json.convertValue(selected.path("planning"),Map.class):Map.of());
        var base=new ArrayList<Source>();var editPool=new ArrayList<Source>();var authorPool=new ArrayList<>(prior);
        var story=m.getOutline().getStory();
        authorPool.add(source("story:"+story.getId(),"STORY","故事资料",Objects.toString(story.getTitle(),"")+"\n"+Objects.toString(story.getSynopsis(),""),null));
        authorPool.add(source("outline:"+m.getOutline().getId(),"OUTLINE","当前场景大纲",selected.toString(),null));
        for(var c:characters.findByStory(story))authorPool.add(source("character:"+c.getId(),"CHARACTER",c.getName(),Objects.toString(c.getSynopsis(),"")+"\n"+Objects.toString(c.getDetails(),"")+"\n"+Objects.toString(c.getRelationships(),""),null));
        var world=worlds.requireUsable(input.binding());
        boolean h2=narrative.enabled(m);NarrativeContextDtos.Stamp stamp=null;
        if(h2){
            var p=narrative.preview(user,m.getId(),m.getCurrentBranchId(),sceneId,NarrativeContextDtos.View.SCENE,null,BUDGET-350);
            // excluded contains forbidden original text: never serialize that DTO into prompts.
            stamp=p.stamp();base.add(source("h2:scene","H2_SCENE","本场受控叙事上下文",p.content(),sceneId));
            for(var f:p.included())editPool.add(source("h2:"+f.id(),f.category(),f.reason(),f.content(),null));
        }else{
            base.add(source("outline:"+m.getOutline().getId(),"OUTLINE","当前场景大纲",selected.toString(),null));
            editPool.addAll(authorPool);
        }
        com.ainovel.app.worldcontext.WorldContextDtos.Selection selection=null;
        {
            String recent=prior.stream().skip(Math.max(0,prior.size()-2)).map(Source::content).reduce("",(a,b)->a+"\n"+b);
            String query=selected+"\n"+selectedChapter.path("planning")+"\n"+selectedChapter.path("summary").asText()+"\n"+instruction+"\n"+selectedText+"\n"+recent;
            int baseTokens=base.stream().mapToInt(s->tokens(json.valueToTree(s).toString())).sum();
            selection=worldSelector.select(world,worldPreferences.read(com.ainovel.app.worldcontext.WorldContextPreferences.scope(m,sceneId),world),query,Math.max(0,BUDGET-baseTokens-READ_METADATA_BUDGET-16),false,
                    item->tokens(json.valueToTree(worldSource(world,item)).toString()));
            for(var item:selection.included()) {
                var source=worldSource(world,item);
                authorPool.add(source);if(!h2)editPool.add(source);
            }
        }
        return new Snapshot(h2,stamp,List.copyOf(base),List.copyOf(editPool),List.copyOf(authorPool),range.minHan(),range.maxHan(),selection);
    }
    public Reads read(Snapshot snapshot,String intent,List<ReadRequest> requests){
        if(requests==null||requests.size()>4)throw error("ASSISTANT_TOOL_REQUEST_INVALID");
        var pool="AUTHOR_QA".equals(intent)?snapshot.authorPool():snapshot.editPool();
        var selected=new LinkedHashMap<String,Source>();snapshot.base().forEach(s->selected.put(s.id(),s));
        if(!snapshot.h2()||"AUTHOR_QA".equals(intent)) {
            com.ainovel.app.worldcontext.WorldContextSelector.requireUsable(snapshot.worldContext());
            pool.stream().filter(s->s.type().startsWith("WORLD_")).forEach(s->selected.putIfAbsent(s.id(),s));
        }
        var warnings=new ArrayList<String>();
        if(snapshot.h2()&&!"AUTHOR_QA".equals(intent))warnings.add("H2_EDIT_CONTEXT_ONLY");
        for(var request:requests){
            if(request==null||request.tool()==null)throw error("ASSISTANT_TOOL_REQUEST_INVALID");
            List<Source> hits;
            if("read_source".equals(request.tool()))hits=pool.stream().filter(s->s.id().equals(request.sourceId())).toList();
            else if(Set.of("search_previous","read_context").contains(request.tool())){
                String query=Objects.toString(request.query(),"").strip();if(query.isBlank()||query.length()>500)throw error("ASSISTANT_TOOL_REQUEST_INVALID");
                hits=pool.stream().filter(s->!"search_previous".equals(request.tool())||s.type().equals("PREVIOUS_SCENE")||snapshot.h2()&&!"AUTHOR_QA".equals(intent))
                        .filter(s->score(s,query)>0).sorted(Comparator.<Source>comparingInt(s->score(s,query)).reversed().thenComparing(Source::id)).limit(4).toList();
            }else throw error("ASSISTANT_TOOL_REQUEST_INVALID");
            if(hits.isEmpty())warnings.add("ASSISTANT_SOURCE_NOT_FOUND:"+shortText(request.tool().equals("read_source")?request.sourceId():request.query(),40));
            for(var hit:hits)selected.putIfAbsent(hit.id(),hit.type().startsWith("WORLD_")?hit:excerpt(hit,request.query(),1000));
        }
        var result=new ArrayList<Source>();int used=0;
        for(var s:selected.values()){
            int cost=tokens(json.valueToTree(s).toString());
            if(used+cost>BUDGET-READ_METADATA_BUDGET){if(s.type().startsWith("WORLD_")||snapshot.base().stream().anyMatch(b->b.id().equals(s.id())))throw error("ASSISTANT_CONTEXT_TOO_LARGE");warnings.add("ASSISTANT_CONTEXT_OMITTED:"+shortText(s.title()==null?s.id():s.title(),40));continue;}
            result.add(s);used+=cost;
        }
        if(snapshot.h2()&&result.stream().noneMatch(s->s.id().equals("h2:scene")))throw error("ASSISTANT_CONTEXT_TOO_LARGE");
        return new Reads(intent,List.copyOf(result),warnings.stream().distinct().toList());
    }
    /** Bounded model-facing metadata; the complete visible titles remain in the UI warnings. */
    public static Map<String,Object> readMetadata(Reads reads){
        var missing=reads.warnings().stream().filter(w->w.startsWith("ASSISTANT_SOURCE_NOT_FOUND:"))
                .map(w->shortText(w.substring(w.indexOf(':')+1),12)).limit(4).toList();
        long omitted=reads.warnings().stream().filter(w->w.startsWith("ASSISTANT_CONTEXT_OMITTED:")).count();
        return Map.of("unmatchedQueries",missing,"omittedSourceCount",omitted);
    }
    private static String shortText(String value,int maxPoints){
        String text=Objects.toString(value,"").replaceAll("[\\r\\n\\t]"," ");int count=text.codePointCount(0,text.length());
        return count<=maxPoints?text:text.substring(0,text.offsetByCodePoints(0,maxPoints))+"…";
    }
    public boolean sourcesCurrent(User user,Manuscript m,UUID sceneId,Snapshot frozen,List<Source> used){
        if(frozen.h2())return narrative.stampCurrent(user,frozen.stamp());
        Snapshot current;
        try{current=freeze(user,m,sceneId,worlds.freeze(m),"","");}catch(ApiStatusException e){
            if(Set.of("ASSISTANT_SCENE_MISSING","H2_CONFIGURATION_REQUIRED","H2_VIEWPOINT_REQUIRED","H2_REQUIRED_CONTEXT_TOO_LARGE","WORLD_BINDING_UNRESOLVED").contains(e.getMessage()))return false;
            throw e;
        }
        var hashes=new HashMap<String,String>();
        current.base().forEach(s->hashes.put(s.id(),s.hash()));current.editPool().forEach(s->hashes.put(s.id(),s.hash()));
        if(frozen.worldContext()!=null) {
            var binding=worlds.resolve(m);
            if(!worlds.same(frozen.worldContext().binding(),binding)||worldPreferences.read(com.ainovel.app.worldcontext.WorldContextPreferences.scope(m,sceneId),binding).revision()!=frozen.worldContext().selectionRevision())return false;
            if(binding!=null)worldSelector.sourceHashes(binding).forEach((id,value)->hashes.put("world:"+binding.versionId()+":"+id,value));
        }
        return used.stream().allMatch(s->Objects.equals(hashes.get(s.id()),s.hash()));
    }
    private static Source source(String id,String type,String title,String text,UUID scene){return new Source(id,type,title,text,scene,hash(text),false,null);}
    private static Source worldSource(WorldBindingService.Resolved world,com.ainovel.app.worldcontext.WorldContextDtos.Source item) {
        return new Source("world:"+world.versionId()+":"+item.id(),"WORLD_"+item.type(),item.title(),item.content(),null,item.contentHash(),false,item.reason());
    }
    private static Source excerpt(Source source,String query,int max){
        if(source.content().length()<=max)return source;
        int hit=query==null?-1:source.content().indexOf(query);int from=Math.max(0,hit-200),end=Math.min(source.content().length(),from+max);
        if(from>0&&Character.isLowSurrogate(source.content().charAt(from)))from--;
        if(end<source.content().length()&&Character.isHighSurrogate(source.content().charAt(end-1)))end--;
        return new Source(source.id(),source.type(),source.title(),source.content().substring(from,end),source.sceneId(),source.hash(),true,"EXCERPT:"+from+":"+end);
    }
    private static int score(Source source,String query){
        String hay=(source.title()+" "+source.content()).toLowerCase(Locale.ROOT),q=query.toLowerCase(Locale.ROOT);int score=hay.contains(q)?100:0;
        for(String term:q.split("[\\s，。？！；、:：]+"))if(!term.isBlank()&&hay.contains(term))score+=10;
        for(int i=0;i+1<q.length();i++)if(Character.UnicodeScript.of(q.charAt(i))==Character.UnicodeScript.HAN&&hay.contains(q.substring(i,i+2)))score++;
        return score;
    }
    public static int tokens(String value){return SceneDraftContextCompiler.estimateTokens(value);}
    public static String plain(String value){return org.jsoup.Jsoup.parseBodyFragment(Objects.toString(value,"")).body().wholeText();}
    private JsonNode tree(String value){try{return json.readTree(value);}catch(Exception e){throw new IllegalStateException("ASSISTANT_OUTLINE_INVALID",e);}}
    private static List<JsonNode> ordered(JsonNode array){var list=new ArrayList<JsonNode>();array.forEach(list::add);list.sort(Comparator.comparingInt(n->n.path("order").asInt(0)));return list;}
    private static UUID id(String value){try{return UUID.fromString(value);}catch(Exception e){return null;}}
    static ApiStatusException error(String code){return new ApiStatusException(HttpStatus.UNPROCESSABLE_ENTITY,code);}
}
