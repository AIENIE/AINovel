package com.ainovel.app.writingagent;

import com.ainovel.app.manuscript.context.SceneDraftContextCompiler;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.world.WorldBindingService;
import com.ainovel.app.world.WorldDraftDtos.*;
import com.ainovel.app.world.WorldTimeline;
import com.ainovel.app.worldcontext.WorldContextDtos.*;
import com.ainovel.app.worldcontext.WorldContextPreferences;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

import static com.ainovel.app.quality.language.LanguageProjection.hash;

/** A server-side frozen projection. Only tool outputs, never FrozenWorld, belong in model messages. */
@Component
public class WorldAgentTools {
    public static final String VERSION="world-agent-tools-v1";
    public static final int TOKEN_BUDGET=3500;
    private static final int BLOCK_CHARS=900;
    private static final Pattern TERMS=Pattern.compile("[\\p{IsHan}]+|[\\p{L}\\p{N}_]+",Pattern.UNICODE_CHARACTER_CLASS);
    private final ObjectMapper json;
    private final WorldBindingService bindings;
    private final WorldContextPreferences preferences;

    public WorldAgentTools(ObjectMapper json,WorldBindingService bindings,WorldContextPreferences preferences) {
        this.json=json;this.bindings=bindings;this.preferences=preferences;
    }
    public record Block(String id,int ordinal,int start,int end,String text,String contentHash) {}
    public record Source(String id,String type,String title,String summary,List<String> names,List<String> tags,
                         List<String> dependencies,List<String> adjacent,String contentHash,Choice choice,List<Block> blocks) {}
    public record FrozenWorld(String compilerVersion,WorldBindingService.Resolved binding,long selectionRevision,
                              boolean isolated,List<Source> sources,List<String> requiredSourceIds,List<String> issues,String fingerprint) {}
    /** start/end are exact UTF-16 offsets in the rendered immutable source, not claims of full-source reading. */
    public record ReadReceipt(String tool,String sourceId,String blockId,int start,int end,String contentHash,String text,boolean fullBlock) {}
    public record ReadState(int tokens,List<ReadReceipt> receipts,Map<String,JsonNode> replay) {
        public ReadState { receipts=receipts==null?List.of():List.copyOf(receipts);replay=replay==null?Map.of():Collections.unmodifiableMap(new TreeMap<>(replay)); }
        public static ReadState empty(){return new ReadState(0,List.of(),Map.of());}
    }
    public record ToolResult(JsonNode output,ReadState state) {}
    private record Match(Source source,int priority,int score,String reason) {}

    @Transactional(readOnly=true)
    public FrozenWorld freeze(Manuscript manuscript,UUID sceneId,boolean h2Edit) {
        var binding=bindings.requireUsable(bindings.resolve(manuscript));
        return freeze(binding,preferences.read(WorldContextPreferences.scope(manuscript,sceneId),binding),h2Edit);
    }
    public FrozenWorld freeze(WorldBindingService.Resolved binding,Preferences prefs,boolean h2Edit) {
        if(binding!=null&&binding.unavailableReason()!=null)throw invalid(binding.unavailableReason());
        var reference=binding==null?null:new WorldBindingService.Resolved(binding.worldId(),binding.versionId(),binding.sourceType(),binding.sourceId(),binding.bindingRevision(),binding.name(),binding.sequenceNumber(),binding.origin(),binding.contentHash(),null,null);
        if(prefs==null)prefs=Preferences.defaults(binding==null?null:binding.worldId());
        if(h2Edit||binding==null)return frozen(reference,prefs.revision(),h2Edit,List.of(),List.of(),List.of());
        if(binding.content()==null)throw invalid("WORLD_AGENT_SNAPSHOT_MISSING");
        if(!Objects.equals(prefs.worldId(),binding.worldId()))throw invalid("WORLD_CONTEXT_BINDING_CHANGED");
        var all=project(binding.content(),prefs.choices());var byId=new TreeMap<String,Source>();all.forEach(s->byId.put(s.id(),s));
        var required=new TreeSet<String>();var issues=new TreeSet<String>();
        prefs.choices().forEach((id,choice)->{if(choice==Choice.INCLUDE){if(!byId.containsKey(id))issues.add("MISSING_REQUIRED:"+id);else required.add(id);}});
        for(String id:List.copyOf(required))for(String endpoint:byId.get(id).dependencies()) {
            if(!byId.containsKey(endpoint)||byId.get(endpoint).choice()==Choice.EXCLUDE)issues.add("RELATION_ENDPOINT_CONFLICT:"+id);
            else required.add(endpoint);
        }
        if(!Objects.equals(prefs.reviewedVersionId(),binding.versionId()))for(String id:required) {
            var source=byId.get(id);
            if(prefs.choices().get(id)==Choice.INCLUDE&&prefs.reviewedHashes().containsKey(id)&&!Objects.equals(prefs.reviewedHashes().get(id),source.contentHash()))issues.add("REVIEW_REQUIRED:"+id);
        }
        var permitted=all.stream().filter(s->s.choice()!=Choice.EXCLUDE)
                .filter(s->s.dependencies().stream().allMatch(id->byId.containsKey(id)&&byId.get(id).choice()!=Choice.EXCLUDE)).toList();
        int requiredCost=0;
        for(var source:permitted)if(required.contains(source.id()))for(var block:source.blocks()){var output=readOutput(source,block);output.put("worldVersionId",binding.versionId());requiredCost+=tokens(write(output));}
        if(requiredCost>TOKEN_BUDGET)issues.add("REQUIRED_CONTEXT_TOO_LARGE");
        return frozen(reference,prefs.revision(),false,permitted,List.copyOf(required),List.copyOf(issues));
    }
    private FrozenWorld frozen(WorldBindingService.Resolved binding,long revision,boolean isolated,List<Source> sources,List<String> required,List<String> issues) {
        return new FrozenWorld(VERSION,binding,revision,isolated,List.copyOf(sources),List.copyOf(required),List.copyOf(issues),hash(VERSION+"\n"+write(binding)+"\n"+revision+"\n"+isolated+"\n"+write(sources)+"\n"+write(required)+"\n"+write(issues)));
    }
    public boolean available(FrozenWorld world){return world!=null&&!world.isolated()&&world.binding()!=null;}

    /** This small directory contains explicit must-read obligations, not source bodies or all world titles. */
    public JsonNode requiredReads(FrozenWorld world,ReadState state) {
        var result=new ArrayList<Map<String,Object>>();
        if(available(world))for(var source:world.sources())if(world.requiredSourceIds().contains(source.id())) {
            var pending=source.blocks().stream().filter(b->!readBlock(state,source.id(),b.id())).map(b->Map.of("blockId",b.id(),"ordinal",b.ordinal())).toList();
            if(!pending.isEmpty())result.add(Map.of("sourceId",source.id(),"nextBlockId",pending.getFirst().get("blockId"),"remainingBlocks",pending.size()));
        }
        return json.valueToTree(result);
    }
    public boolean requiredComplete(FrozenWorld world,ReadState state) {
        return world!=null&&world.issues().isEmpty()&&requiredReads(world,state).isEmpty();
    }
    public ToolResult invoke(FrozenWorld world,String name,JsonNode args,ReadState previous) {
        var state=previous==null?ReadState.empty():previous;
        if(!available(world))return failure(world!=null&&world.isolated()?"H2_WORLD_TOOL_UNAVAILABLE":"WORLD_NOT_BOUND",state);
        if(!VERSION.equals(world.compilerVersion()))return failure("WORLD_AGENT_COMPILER_UNSUPPORTED",state);
        if(!world.issues().isEmpty())return new ToolResult(json.valueToTree(Map.of("error","WORLD_AGENT_BASELINE_INVALID","issues",world.issues())),state);
        if(args==null||!args.isObject())return failure("WORLD_AGENT_ARGUMENTS_INVALID",state);
        String tool=Objects.toString(name,"").replace('_','.');
        String key=hash(world.fingerprint()+"\n"+tool+"\n"+write(args));
        if(state.replay().containsKey(key))return new ToolResult(state.replay().get(key),state);
        try {
            return switch(tool) {
                case "world.search"->search(world,args,state,key);
                case "world.read"->read(world,args,state,key);
                case "world.related"->related(world,args,state,key);
                default->failure("WORLD_AGENT_TOOL_UNKNOWN",state);
            };
        }catch(IllegalArgumentException invalid){return failure("WORLD_AGENT_ARGUMENTS_INVALID",state);}
    }
    private ToolResult search(FrozenWorld world,JsonNode args,ReadState state,String key) {
        String query=required(args,"query");if(query.length()>500)throw new IllegalArgumentException();
        String q=normalize(query);var terms=terms(q);var ranked=new ArrayList<Match>();
        for(var source:world.sources()) {
            var direct=source.names().stream().map(WorldAgentTools::normalize).filter(n->!n.isBlank()&&q.contains(n)).sorted().findFirst();
            var tags=source.tags().stream().filter(t->!t.isBlank()&&q.contains(normalize(t))).sorted().toList();
            var sourceTerms=terms(source.title()+" "+source.summary()+" "+source.blocks().stream().map(Block::text).reduce("",String::concat));
            int score=(int)terms.stream().filter(sourceTerms::contains).count();
            int priority=direct.isPresent()?300:!tags.isEmpty()?200:score>0?100:0;
            if(priority>0)ranked.add(new Match(source,priority,score,direct.isPresent()?"NAME_MATCH:"+direct.get():!tags.isEmpty()?"TAG_MATCH:"+String.join("、",tags):"CONTENT_MATCH"));
        }
        var direct=new HashSet<String>();ranked.stream().filter(m->m.priority()>=200).forEach(m->direct.add(m.source().id()));
        var neighbors=new HashSet<String>();
        for(var source:world.sources())if(direct.contains(source.id())||source.adjacent().stream().anyMatch(direct::contains)){neighbors.add(source.id());neighbors.addAll(source.adjacent());}
        var existing=new HashSet<String>();ranked.forEach(m->existing.add(m.source().id()));
        for(var source:world.sources())if(neighbors.contains(source.id())&&!existing.contains(source.id()))ranked.add(new Match(source,50,0,"RELATED_ONE_HOP"));
        ranked.sort(Comparator.comparingInt(Match::priority).reversed().thenComparing(m->!m.source().type().equals("RULE"))
                .thenComparing(Comparator.comparingInt(Match::score).reversed()).thenComparing(m->m.source().id()));
        return directory(world,ranked,args,state,key,"world.search",query);
    }
    private ToolResult related(FrozenWorld world,JsonNode args,ReadState state,String key) {
        String id=required(args,"sourceId");var source=find(world,id);if(source==null)return failure("WORLD_SOURCE_UNAVAILABLE",state);
        var neighbors=new TreeSet<String>(source.adjacent());
        for(var other:world.sources())if(other.adjacent().contains(id)){neighbors.add(other.id());neighbors.addAll(other.adjacent());}
        neighbors.remove(id);
        var matches=world.sources().stream().filter(s->neighbors.contains(s.id())).map(s->new Match(s,0,0,"RELATED_ONE_HOP")).sorted(Comparator.comparing(m->m.source().id())).toList();
        return directory(world,matches,args,state,key,"world.related","");
    }
    private ToolResult directory(FrozenWorld world,List<Match> ranked,JsonNode args,ReadState state,String key,String tool,String query) {
        int limit=integer(args,"limit",5,1,8),offset=integer(args,"cursor",0,0,Integer.MAX_VALUE);
        var results=new ArrayList<Map<String,Object>>();var receipts=new ArrayList<ReadReceipt>();
        for(var match:ranked.stream().skip(offset).limit(limit).toList()) {
            var source=match.source();var snippet=snippet(source,query);
            if(!source.dependencies().stream().allMatch(id->completeSource(world,state,id))){results.add(Map.of("sourceId",source.id(),"type",source.type(),"dependencies",source.dependencies(),"reason","RELATION_ENDPOINTS_NOT_READ"));continue;}
            var item=new LinkedHashMap<String,Object>();item.put("sourceId",source.id());item.put("type",source.type());item.put("title",source.title());item.put("reason",match.reason());
            item.put("snippet",snippet.text());item.put("snippetBlockId",snippet.blockId());item.put("start",snippet.start());item.put("end",snippet.end());item.put("contentHash",source.contentHash());
            item.put("blockCount",source.blocks().size());results.add(item);
            receipts.add(new ReadReceipt(tool,source.id(),snippet.blockId(),snippet.start(),snippet.end(),source.contentHash(),snippet.text(),false));
        }
        var output=new LinkedHashMap<String,Object>();output.put("worldVersionId",world.binding().versionId());output.put("results",results);
        output.put("nextCursor",offset+results.size()<ranked.size()?offset+results.size():null);output.put("scope","bound-version; not found does not establish absence");
        return admit(world,json.valueToTree(output),receipts,state,key);
    }
    private ToolResult read(FrozenWorld world,JsonNode args,ReadState state,String key) {
        var source=find(world,required(args,"sourceId"));if(source==null)return failure("WORLD_SOURCE_UNAVAILABLE",state);
        if(!source.dependencies().stream().allMatch(id->completeSource(world,state,id)))return new ToolResult(json.valueToTree(Map.of("error","RELATION_ENDPOINTS_NOT_READ","requiredSourceIds",source.dependencies())),state);
        String blockId=args.path("blockId").asText("");
        var block=blockId.isEmpty()?source.blocks().getFirst():source.blocks().stream().filter(b->b.id().equals(blockId)).findFirst().orElse(null);
        if(block==null)return failure("WORLD_BLOCK_UNAVAILABLE",state);
        var output=readOutput(source,block);output.put("worldVersionId",world.binding().versionId());
        return admit(world,json.valueToTree(output),List.of(new ReadReceipt("world.read",source.id(),block.id(),block.start(),block.end(),source.contentHash(),block.text(),true)),state,key);
    }
    private Map<String,Object> readOutput(Source source,Block block) {
        var result=new LinkedHashMap<String,Object>();
        result.put("sourceId",source.id());result.put("type",source.type());result.put("title",source.title());result.put("blockId",block.id());
        result.put("start",block.start());result.put("end",block.end());result.put("text",block.text());result.put("contentHash",source.contentHash());result.put("blockHash",block.contentHash());
        result.put("blockCount",source.blocks().size());result.put("nextBlockId",block.ordinal()+1<source.blocks().size()?source.blocks().get(block.ordinal()+1).id():null);
        return result;
    }
    private ToolResult admit(FrozenWorld world,JsonNode output,List<ReadReceipt> receipts,ReadState state,String key) {
        int cost=tokens(write(output));if(state.tokens()+cost>TOKEN_BUDGET)return failure("WORLD_AGENT_READ_BUDGET_EXCEEDED",state);
        var retained=new ArrayList<>(state.receipts());retained.addAll(receipts);var replay=new TreeMap<>(state.replay());replay.put(key,output);
        int pending=0;for(var source:world.sources())if(world.requiredSourceIds().contains(source.id()))for(var block:source.blocks()){
            if(retained.stream().noneMatch(r->r.fullBlock()&&r.sourceId().equals(source.id())&&r.blockId().equals(block.id()))){var remaining=readOutput(source,block);remaining.put("worldVersionId",world.binding().versionId());pending+=tokens(write(remaining));}
        }
        if(state.tokens()+cost+pending>TOKEN_BUDGET)return failure("WORLD_AGENT_REQUIRED_READ_CAPACITY",state);
        return new ToolResult(output,new ReadState(state.tokens()+cost,retained,replay));
    }
    private ToolResult failure(String error,ReadState state){return new ToolResult(json.valueToTree(Map.of("error",error)),state);}
    private static Source find(FrozenWorld world,String id){return world.sources().stream().filter(s->s.id().equals(id)).findFirst().orElse(null);}
    private static boolean readBlock(ReadState state,String source,String block){return state!=null&&state.receipts().stream().anyMatch(r->r.fullBlock()&&r.sourceId().equals(source)&&r.blockId().equals(block));}
    private static boolean completeSource(FrozenWorld world,ReadState state,String id){var source=find(world,id);return source!=null&&source.blocks().stream().allMatch(b->readBlock(state,id,b.id()));}
    private record Snippet(String blockId,int start,int end,String text) {}
    private static Snippet snippet(Source source,String query) {
        var block=source.blocks().getFirst();int start=0;String q=normalize(query);
        for(var candidate:source.blocks()) {
            String value=normalize(candidate.text());int match=q.isBlank()?-1:value.indexOf(q);
            if(match<0)for(var term:terms(q)){match=value.indexOf(term);if(match>=0)break;}
            if(match>=0){block=candidate;start=Math.max(0,match-40);break;}
        }
        start=Math.min(start,Math.max(0,block.text().length()-1));
        if(start>0&&Character.isLowSurrogate(block.text().charAt(start)))start--;
        int end=Math.min(block.text().length(),start+160);if(end<block.text().length()&&Character.isHighSurrogate(block.text().charAt(end-1)))end--;
        return new Snippet(block.id(),block.start()+start,block.start()+end,block.text().substring(start,end));
    }
    private List<Source> project(Content content,Map<String,Choice> choices) {
        var result=new ArrayList<Source>();
        add(result,"metadata","METADATA",content.name(),content.tagline(),"名称："+text(content.name())+"\n简介："+text(content.tagline())+"\n主题："+write(content.themes())+"\n创作意图："+text(content.creativeIntent()),List.of(),List.of(),List.of(),List.of(),choices);
        if(!text(content.overview()).isBlank())add(result,"overview","OVERVIEW","世界概览","",content.overview(),List.of(),List.of(),List.of(),List.of(),choices);
        if(!text(content.notes()).isBlank())add(result,"notes","AUTHOR_NOTES","作者说明","",content.notes(),List.of(),List.of(),List.of(),List.of(),choices);
        var names=new HashMap<UUID,String>();
        for(var e:list(content.entries())) {
            names.put(e.id(),e.title());var aliases=new ArrayList<String>();aliases.add(e.title());aliases.addAll(list(e.aliases()));
            add(result,"entry:"+e.id(),e.type(),e.title(),e.summary(),"标题："+e.title()+"\n类型："+e.type()+"\n别名："+write(e.aliases())+"\n标签："+write(e.tags())+"\n摘要："+text(e.summary())+"\n正文："+text(e.content()),aliases,list(e.tags()),List.of(),List.of(),choices);
        }
        for(var r:list(content.relations())) {
            var endpoints=List.of("entry:"+r.sourceId(),"entry:"+r.targetId());
            add(result,"relation:"+r.id(),"RELATION",names.getOrDefault(r.sourceId(),r.sourceId().toString())+" → "+names.getOrDefault(r.targetId(),r.targetId().toString()),r.description(),"起点："+r.sourceId()+"\n终点："+r.targetId()+"\n关系："+text(r.type())+"\n说明："+text(r.description()),List.of(),List.of(),endpoints,endpoints,choices);
        }
        if(content.timeline()!=null) {
            var eras=new HashMap<UUID,Era>();content.timeline().eras().forEach(e->eras.put(e.id(),e));
            for(var event:content.timeline().events()) {
                var aliases=new ArrayList<String>();aliases.add(event.title());aliases.addAll(event.aliases());
                String eraText=event.eraIds().stream().map(id->{var e=eras.get(id);return e==null?id.toString():e.name()+"（"+WorldTimeline.renderDate(e.date())+"；"+text(e.description())+"）";}).collect(java.util.stream.Collectors.joining("、"));
                String entries=event.entryIds().stream().map(id->names.getOrDefault(id,id.toString())+" ["+id+"]").collect(java.util.stream.Collectors.joining("、"));
                add(result,"event:"+event.id(),"EVENT",event.title(),event.summary(),"标题："+event.title()+"\n别名："+write(event.aliases())+"\n标签："+write(event.tags())+"\n历法："+text(content.timeline().calendarName())+"\n日期："+WorldTimeline.renderDate(event.date())+"\n时代："+eraText+"\n关联条目："+entries+"\n摘要："+text(event.summary())+"\n正文："+text(event.content()),aliases,event.tags(),List.of(),event.entryIds().stream().map(id->"entry:"+id).toList(),choices);
            }
        }
        result.sort(Comparator.comparing(Source::id));return List.copyOf(result);
    }
    private void add(List<Source> sources,String id,String type,String title,String summary,String content,List<String> names,List<String> tags,List<String> dependencies,List<String> adjacent,Map<String,Choice> choices) {
        var blocks=new ArrayList<Block>();int offset=0;
        while(offset<content.length()) {
            int end=Math.min(content.length(),offset+BLOCK_CHARS);
            if(end<content.length()) {
                int paragraph=content.lastIndexOf('\n',end-1);if(paragraph>offset+BLOCK_CHARS/2)end=paragraph+1;
                if(Character.isHighSurrogate(content.charAt(end-1)))end--;
            }
            String part=content.substring(offset,end);int ordinal=blocks.size();
            blocks.add(new Block(id+":block:"+ordinal,ordinal,offset,end,part,hash(part)));offset=end;
        }
        if(blocks.isEmpty())blocks.add(new Block(id+":block:0",0,0,0,"",hash("")));
        sources.add(new Source(id,text(type),text(title),text(summary),List.copyOf(names),List.copyOf(tags),List.copyOf(dependencies),List.copyOf(adjacent),hash(content),choices.getOrDefault(id,Choice.AUTO),List.copyOf(blocks)));
    }
    private static String required(JsonNode args,String field){var value=args.get(field);if(value==null||!value.isTextual()||value.asText().isBlank())throw new IllegalArgumentException();return value.asText().strip();}
    private static int integer(JsonNode args,String field,int fallback,int min,int max){if(!args.has(field)||args.path(field).isNull())return fallback;var value=args.path(field);if(!value.isIntegralNumber()||!value.canConvertToInt()||value.intValue()<min||value.intValue()>max)throw new IllegalArgumentException();return value.intValue();}
    private static Set<String> terms(String value){var result=new TreeSet<String>();var matcher=TERMS.matcher(normalize(value));while(matcher.find()){String term=matcher.group();if(term.length()<2)continue;if(term.codePoints().allMatch(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN)){for(int i=0;i+1<term.length();i++)result.add(term.substring(i,i+2));}else result.add(term);}return result;}
    private static String normalize(String value){return Normalizer.normalize(text(value),Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private static int tokens(String value){return SceneDraftContextCompiler.estimateTokens(value);}
    private static String text(String value){return Objects.toString(value,"");}
    private static <T>List<T> list(List<T> value){return value==null?List.of():value;}
    private static com.ainovel.app.common.ApiStatusException invalid(String code){return com.ainovel.app.worldcontext.WorldContextSelector.error(code);}
}
