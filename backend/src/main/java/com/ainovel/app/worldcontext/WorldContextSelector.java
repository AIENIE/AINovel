package com.ainovel.app.worldcontext;

import com.ainovel.app.common.ApiStatusException;
import com.ainovel.app.manuscript.context.SceneDraftContextCompiler;
import com.ainovel.app.world.WorldBindingService;
import com.ainovel.app.world.WorldDraftDtos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.regex.Pattern;
import static com.ainovel.app.worldcontext.WorldContextDtos.*;
import static com.ainovel.app.quality.language.LanguageProjection.hash;

/** Deterministic retrieval from one immutable version. It never summarizes or truncates a source. */
@Component
public class WorldContextSelector {
    public static final String VERSION="world-context-v1";
    private static final Pattern TERMS=Pattern.compile("[\\p{IsHan}]+|[\\p{L}\\p{N}_]+",Pattern.UNICODE_CHARACTER_CLASS);
    private static final Set<String> STOP=Set.of("一个","这个","那个","世界","故事","场景","章节","人物","角色","当前","继续","生成","润色");
    private final ObjectMapper json;
    public WorldContextSelector(ObjectMapper json) { this.json=json; }
    private record Item(String id,String type,String title,String content,List<String> names,List<String> tags,List<String> endpoints) {}
    private record Ranked(Item item,int priority,int score,String reason,boolean required) {}

    public Selection select(WorldBindingService.Resolved binding,Preferences preferences,String query,int budget,boolean isolated) {
        int limit=Math.max(0,Math.min(3500,budget));
        if(binding==null)return result(null,preferences,query,limit,List.of(),List.of(),List.of());
        if(binding.unavailableReason()!=null)throw error(binding.unavailableReason());
        var items=items(binding.content());var byId=new TreeMap<String,Item>();items.forEach(i->byId.put(i.id(),i));
        var choices=preferences.choices();var mandatory=new HashSet<String>();var issues=new ArrayList<String>();
        for(var choice:choices.entrySet())if(choice.getValue()==Choice.INCLUDE) {
            if(!byId.containsKey(choice.getKey()))issues.add("MISSING_REQUIRED:"+choice.getKey());
            else mandatory.add(choice.getKey());
        }
        if(!text(binding.content().overview()).isBlank()&&choices.getOrDefault("overview",Choice.AUTO)!=Choice.EXCLUDE)mandatory.add("overview");
        for(var id:List.copyOf(mandatory)) {
            var item=byId.get(id);if(item==null)continue;
            for(var endpoint:item.endpoints()) {
                if(choices.get(endpoint)==Choice.EXCLUDE||!byId.containsKey(endpoint))issues.add("RELATION_ENDPOINT_CONFLICT:"+id);
                else mandatory.add(endpoint);
            }
        }
        if(!Objects.equals(preferences.reviewedVersionId(),binding.versionId()))for(var id:mandatory) {
            if(choices.get(id)==Choice.INCLUDE&&preferences.reviewedHashes().containsKey(id)&&
                    !Objects.equals(preferences.reviewedHashes().get(id),hash(byId.get(id).content())))issues.add("REVIEW_REQUIRED:"+id);
        }
        String normalized=normalize(query);var terms=terms(normalized);var direct=new HashSet<String>();List<Ranked> ranked=new ArrayList<>();
        for(var item:items) {
            var match=item.names().stream().map(WorldContextSelector::normalize).filter(n->!n.isBlank()&&normalized.contains(n)).findFirst();
            int matches=overlap(terms,terms(item.title()+" "+String.join(" ",item.tags())+" "+item.content()));
            boolean tag=item.tags().stream().anyMatch(t->!t.isBlank()&&normalized.contains(normalize(t)));
            int priority=match.isPresent()?300:tag||matches>0?200:item.id().equals("metadata")?100:0;
            if(priority>=200&&item.endpoints().isEmpty())direct.add(item.id());
            String reason=match.isPresent()?"NAME_MATCH:"+match.get():tag?"TAG_MATCH":matches>0?"CONTENT_MATCH":item.id().equals("metadata")?"WORLD_BACKGROUND":"NOT_RELEVANT";
            ranked.add(new Ranked(item,priority,matches,reason,mandatory.contains(item.id())));
        }
        var neighbors=new HashSet<String>();
        for(var item:items)if(!item.endpoints().isEmpty()&&item.endpoints().stream().anyMatch(direct::contains)) {
            neighbors.add(item.id());neighbors.addAll(item.endpoints());
        }
        ranked=ranked.stream().map(r->r.priority()==0&&neighbors.contains(r.item().id())?new Ranked(r.item(),50,0,"RELATED_ONE_HOP",r.required()):r)
                .sorted(Comparator.<Ranked,Boolean>comparing(Ranked::required).reversed()
                        .thenComparing(Comparator.comparingInt(Ranked::priority).reversed())
                        .thenComparing(r->!r.item().type().equals("RULE"))
                        .thenComparing(Comparator.comparingInt(Ranked::score).reversed()).thenComparing(r->r.item().id())).toList();
        var included=new ArrayList<Source>();var excluded=new ArrayList<Source>();var selected=new HashSet<String>();int used=0;
        // Entries precede relationships so no admitted relation can refer to omitted endpoint content.
        var ordered=new ArrayList<Ranked>();
        for(boolean required:List.of(true,false)) {
            ordered.addAll(ranked.stream().filter(r->r.required()==required&&r.item().endpoints().isEmpty()).toList());
            ordered.addAll(ranked.stream().filter(r->r.required()==required&&!r.item().endpoints().isEmpty()).toList());
        }
        for(var r:ordered) {
            var item=r.item();Choice choice=choices.getOrDefault(item.id(),Choice.AUTO);
            String reason=isolated?"H2_UNCLASSIFIED":choice==Choice.EXCLUDE?"AUTHOR_EXCLUDED":choice==Choice.INCLUDE?"AUTHOR_REQUIRED":
                    r.required()?item.id().equals("overview")?"OVERVIEW_DEFAULT":"REQUIRED_RELATION_ENDPOINT":r.reason();
            int cost=tokens(render(item.title(),item.content()));
            boolean allowed=!isolated&&choice!=Choice.EXCLUDE&&(r.required()||r.priority()>0);
            if(allowed&&!selected.containsAll(item.endpoints())) {allowed=false;reason="ENDPOINT_NOT_INCLUDED";}
            if(allowed&&used+cost>limit) {allowed=false;reason="CAPACITY_EXCLUDED";if(r.required())issues.add("REQUIRED_CONTEXT_TOO_LARGE:"+item.id());}
            var source=new Source(item.id(),item.type(),item.title(),item.content(),hash(item.content()),choice,reason,cost,r.required());
            if(allowed){included.add(source);selected.add(item.id());used+=cost;}else excluded.add(source);
        }
        if(isolated)issues.clear();
        return result(binding,preferences,query,limit,included,excluded,issues);
    }
    public Map<String,String> sourceHashes(WorldBindingService.Resolved binding) {
        var result=new TreeMap<String,String>();if(binding!=null&&binding.content()!=null)items(binding.content()).forEach(i->result.put(i.id(),hash(i.content())));return result;
    }
    public static void requireUsable(Selection value) { if(value!=null&&!value.issues().isEmpty())throw error("WORLD_CONTEXT_"+value.issues().getFirst().split(":",2)[0]); }
    public static ApiStatusException error(String code){return new ApiStatusException(HttpStatus.UNPROCESSABLE_ENTITY,code);}
    private Selection result(WorldBindingService.Resolved binding,Preferences prefs,String query,int limit,List<Source> included,List<Source> excluded,List<String> issues) {
        String content=included.stream().map(s->render(s.title(),s.content())).reduce("",String::concat);
        var reference=binding==null?null:new WorldBindingService.Resolved(binding.worldId(),binding.versionId(),binding.sourceType(),binding.sourceId(),binding.bindingRevision(),binding.name(),binding.sequenceNumber(),binding.origin(),binding.contentHash(),binding.unavailableReason(),null);
        String queryHash=hash(text(query));String fingerprint=hash(VERSION+"\n"+write(reference)+"\n"+prefs.revision()+"\n"+queryHash+"\n"+limit+"\n"+write(included)+"\n"+write(excluded)+"\n"+write(issues));
        return new Selection(VERSION,reference,prefs.revision(),queryHash,fingerprint,limit,tokens(content),content,List.copyOf(included),List.copyOf(excluded),List.copyOf(issues));
    }
    private List<Item> items(Content c) {
        var list=new ArrayList<Item>();
        list.add(new Item("metadata","METADATA",c.name(),"名称："+text(c.name())+"\n简介："+text(c.tagline())+"\n主题："+write(c.themes())+"\n创作意图："+text(c.creativeIntent()),List.of(),List.of(),List.of()));
        if(!text(c.overview()).isBlank())list.add(new Item("overview","OVERVIEW","世界概览",c.overview(),List.of(),List.of(),List.of()));
        if(!text(c.notes()).isBlank())list.add(new Item("notes","AUTHOR_NOTES","作者说明",c.notes(),List.of(),List.of(),List.of()));
        for(var e:c.entries()) {
            var names=new ArrayList<String>();names.add(e.title());if(e.aliases()!=null)names.addAll(e.aliases());
            list.add(new Item("entry:"+e.id(),e.type(),e.title(),"类型："+e.type()+"\n别名："+write(e.aliases())+"\n标签："+write(e.tags())+"\n摘要："+text(e.summary())+"\n正文："+text(e.content()),names,e.tags()==null?List.of():e.tags(),List.of()));
        }
        var names=new HashMap<UUID,String>();c.entries().forEach(e->names.put(e.id(),e.title()));
        for(var r:c.relations())list.add(new Item("relation:"+r.id(),"RELATION",names.getOrDefault(r.sourceId(),r.sourceId().toString())+" → "+names.getOrDefault(r.targetId(),r.targetId().toString()),
                "起点："+r.sourceId()+"\n终点："+r.targetId()+"\n关系："+text(r.type())+"\n说明："+text(r.description()),List.of(),List.of(),List.of("entry:"+r.sourceId(),"entry:"+r.targetId())));
        return list;
    }
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private static String render(String title,String content){return "["+title+"]\n"+content+"\n\n";}
    private static int tokens(String s){return SceneDraftContextCompiler.estimateTokens(s);}
    private static String text(String s){return Objects.toString(s,"");}
    private static String normalize(String s){return java.text.Normalizer.normalize(text(s),java.text.Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);}
    private static Set<String> terms(String value) {
        var result=new HashSet<String>();var matcher=TERMS.matcher(normalize(value));
        while(matcher.find()) {String term=matcher.group();if(term.length()<2)continue;
            if(term.codePoints().allMatch(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN)) {
                for(int i=0;i+1<term.length();i++){String pair=term.substring(i,i+2);if(!STOP.contains(pair))result.add(pair);}
            }else if(!STOP.contains(term))result.add(term);
        }return result;
    }
    private static int overlap(Set<String> a,Set<String> b){return (int)a.stream().filter(b::contains).count();}
}
