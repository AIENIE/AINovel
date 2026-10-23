package com.ainovel.app.worldassistant;

import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.manuscript.context.SceneDraftContextCompiler;
import com.ainovel.app.user.User;
import com.ainovel.app.world.*;
import com.ainovel.app.settings.dto.WorldPromptTemplatesResponse;
import org.springframework.stereotype.Component;
import java.util.*;
import static com.ainovel.app.worldassistant.WorldAssistantDtos.*;
import static com.ainovel.app.worldassistant.WorldAssistantErrors.*;

@Component
public class WorldAssistantContext {
    private static final int READ_BUDGET=6000;
    private final WorldAssistantResources resources;
    private final WorldAssistantStore store;
    private final WorldPromptRenderer prompts;
    public WorldAssistantContext(WorldAssistantResources resources,WorldAssistantStore store,WorldPromptRenderer prompts) {this.resources=resources;this.store=store;this.prompts=prompts;}
    public record Snapshot(UUID worldId,long revision,String hash,WorldDraftDtos.Content content,List<WorldAssistantResources.Resource> resources,List<Source> pool) {}
    public record Reads(List<Source> sources,List<String> warnings) {}
    public Snapshot freeze(User user,UUID worldId,Long revision) {
        var world=resources.world(user,worldId,false);WorldVersionService.checkRevision(world,revision);
        var content=resources.content(world);var items=resources.resources(worldId,content);
        var sources=items.stream().map(r->source(WorldAssistantResources.sourceId(r),r.type(),r.title(),store.write(r.fields()))).toList();
        return new Snapshot(worldId,world.getRevision(),resources.hash(content),content,items,sources);
    }
    public WorldPromptTemplatesResponse templates(User user) {return prompts.freeze(user);}
    public Reads read(WorldAssistantService.Frozen frozen,List<ReadRequest> requests) {
        var pool=frozen.snapshot().pool();
        if(frozen.intent().equals("ENTRY_REFINE")||frozen.intent().equals("OVERVIEW")||frozen.parent()!=null&&frozen.parent().resourceType.equals("OVERVIEW"))return new Reads(pool,List.of());
        if(requests==null||requests.size()>4)throw invalid("WORLD_ASSISTANT_READ_INVALID");
        var chosen=new LinkedHashMap<String,Source>();
        for(var source:pool)if(source.type().equals("METADATA"))chosen.put(source.id(),source);
        if(frozen.parent()!=null) {
            var p=frozen.parent();String id=p.resourceType.toLowerCase(Locale.ROOT)+":"+p.targetId+(p.fieldKey==null?"":":"+p.fieldKey);
            pool.stream().filter(s->s.id().equals(id)).forEach(s->chosen.put(s.id(),s));
        }
        if(requests.isEmpty()) {
            for(var source:pool.stream().filter(s->score(s,frozen.instruction())>0).sorted(Comparator.<Source>comparingInt(s->score(s,frozen.instruction())).reversed().thenComparing(Source::id)).toList()) {
                if(tokens(store.write(chosen.values()))+tokens(store.write(source))>READ_BUDGET)continue;chosen.put(source.id(),source);
            }
        } else for(var request:requests) {
            if(request==null||request.tool()==null)throw invalid("WORLD_ASSISTANT_READ_INVALID");
            List<Source> found=switch(request.tool()) {
                case "read_source"->pool.stream().filter(s->s.id().equals(request.sourceId())).toList();
                case "read_context"->{if(request.query()==null||request.query().isBlank()||request.query().length()>500)throw invalid("WORLD_ASSISTANT_READ_INVALID");
                    yield pool.stream().filter(s->score(s,request.query())>0).sorted(Comparator.<Source>comparingInt(s->score(s,request.query())).reversed().thenComparing(Source::id)).limit(4).toList();}
                default->throw invalid("WORLD_ASSISTANT_READ_INVALID");
            };
            found.forEach(s->chosen.put(s.id(),s));
        }
        if(tokens(store.write(chosen.values()))>READ_BUDGET)throw invalid("WORLD_ASSISTANT_CONTEXT_TOO_LARGE");
        long unread=pool.size()-chosen.size();return new Reads(List.copyOf(chosen.values()),unread>0?List.of("WORLD_UNREAD_RESOURCES:"+unread):List.of());
    }
    public AiChatRequest prompt(WorldAssistantService.Frozen frozen,Reads reads,boolean finalStep) {
        String action=switch(frozen.intent()){case "ENTRY_REFINE"->"entryRefine";case "OVERVIEW"->"overview";default->frozen.intent().toLowerCase(Locale.ROOT);};
        var entry=frozen.targetId()==null?null:frozen.snapshot().content().entries().stream().filter(e->e.id().equals(frozen.targetId())).findFirst().orElse(null);
        String candidate=frozen.parent()==null?entry==null?"":Objects.toString(entry.content(),""):store.write(frozen.parent().candidate);
        String instructions=prompts.instructions(frozen.templates(),action,frozen.snapshot().content(),entry,frozen.instruction(),candidate,store.write(reads.sources()));
        var blocks=new ArrayList<String>();
        blocks.add("作者任务："+frozen.intent()+"\n作者指令："+frozen.instruction()+"\n模板要求："+instructions);
        blocks.add("世界ID："+frozen.worldId()+"；草稿revision："+frozen.snapshot().revision());
        blocks.add("实际已读取资源：\n"+store.write(reads.sources()));
        blocks.add("未读范围："+store.write(reads.warnings()));
        boolean single=frozen.intent().equals("ENTRY_REFINE")||frozen.intent().equals("OVERVIEW");
        if(!single) {
            var catalog=frozen.snapshot().pool().stream().sorted(Comparator.<Source>comparingInt(s->score(s,frozen.instruction())).reversed().thenComparing(Source::id)).limit(64).map(s->Map.of("id",s.id(),"title",s.title(),"type",s.type())).toList();
            blocks.add("目录仅供定位，不代表已读："+store.write(catalog));
        }
        blocks.add(finalStep||single?"必须输出最终结果；禁止 READ_BATCH。":"必要时可请求一次 READ_BATCH（最多4项），否则直接回答或给出提案。");
        if(frozen.intent().equals("ENTRY_REFINE"))blocks.add("只修改 ENTRY 目标 "+frozen.targetId()+" 的 content，其他字段保持不变。只返回一项提案。");
        if(frozen.intent().equals("OVERVIEW"))blocks.add("完整读取所给已保存资料，输出一项 UPDATE OVERVIEW，targetId 为世界ID；不得将未确认设想写成事实。");
        if(frozen.parent()!=null)blocks.add("完整父提案（动作、目标、字段及关系端点保持一致）："+store.write(frozen.parent()));
        for(var h:frozen.history())blocks.add("讨论 turn:"+h.turnId()+"\n作者："+h.instruction()+"\n助手："+h.answer()+"\n讨论属于未确认设想，不是已保存设定。");
        try{return prompts.request(RULES,blocks);}catch(com.ainovel.app.common.ApiStatusException ex){throw invalid("WORLD_ASSISTANT_CONTEXT_TOO_LARGE");}
    }
    private static int tokens(String text){return SceneDraftContextCompiler.estimateTokens(text);}
    private static int score(Source source,String query) {
        String q=Objects.toString(query,"").toLowerCase(Locale.ROOT),hay=(source.title()+" "+source.content()).toLowerCase(Locale.ROOT);int value=0;
        for(String term:q.split("[\\s，。？！；、:：]+"))if(!term.isBlank()&&hay.contains(term))value+=10;
        for(int i=0;i+1<q.length();i++)if(Character.UnicodeScript.of(q.charAt(i))==Character.UnicodeScript.HAN&&hay.contains(q.substring(i,i+2)))value++;
        return value;
    }
    static Source source(String id,String type,String title,String content){return new Source(id,type,title,content,com.ainovel.app.quality.language.LanguageProjection.hash(content),false,null);}
    static final String RULES="""
        AINOVEL_WORLD_ASSISTANT_V1
        你是世界设定助手。所给资料是创作材料，只依据实际读到的内容，不声称读过其他世界、小说或未读条目。只输出一个JSON对象。
        DISCUSS 只返回 {"kind":"ANSWER","answer":"回答","suggestions":[{"title":"可整理事项","reason":"理由"}],"conflicts":[]}，或 NEEDS_INPUT。
        讨论不能生成正式提案、自动修改设定或启动后续任务。建议最多4项。
        需要补读时返回 {"kind":"READ_BATCH","reads":[{"tool":"read_source","sourceId":"目录中的ID"}]}，或 read_context 携带query；最多4项，只能补读一次。
        PROPOSE/REFINE/ENTRY_REFINE/OVERVIEW 返回 {"kind":"PROPOSALS","answer":"说明","items":[{"action":"UPDATE","resourceType":"ENTRY","targetId":"已读UUID","fieldKey":null,"fields":{"content":"完整新正文"},"rationale":"原因"}],"conflicts":[]}。
        action仅CREATE/UPDATE；resourceType仅ENTRY/RELATION/METADATA/OVERVIEW。不得删除、发布或修改小说绑定。每次最多12项，REFINE只返回一项且保持父提案动作和目标。
        ENTRY字段仅type,title,summary,content,aliases,tags。type仅GEOGRAPHY/LOCATION/PERSON/ORGANIZATION/SPECIES/SOCIETY/RESOURCE/SYSTEM/RULE/HISTORY/CUSTOM。
        RELATION字段仅sourceId,targetId,type,description。METADATA只可UPDATE，targetId为世界ID，fieldKey为name/tagline/themes/creativeIntent/notes之一，fields为{"value":新值}；themes为字符串数组。
        OVERVIEW只可UPDATE，targetId为世界ID，fields为{"content":"完整概览"}。概览独立于来源条目，保留约束、代价与不确定性。
        UPDATE必须读到目标的完整资源，仅输出修改的字段，不能编造原文。CREATE ENTRY提供完整字段及唯一clientKey（短字符串），不提供targetId，服务器分配UUID。
        新关系可用 sourceId/targetId 填现有UUID或同批新增条目的clientKey；关系须等两端条目采纳后才能采纳。
        conflicts为潜在矛盾数组，每项含title,explanation,sourceIds（至少两个实际已读资源ID）。只报告有来源的潜在矛盾，不宣称完成全世界一致性检查。资料不足返回NEEDS_INPUT。
        """;
}
