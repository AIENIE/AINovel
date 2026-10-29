package com.ainovel.app.story;

import com.ainovel.app.ai.*;
import com.ainovel.app.ai.dto.*;
import com.ainovel.app.aioperation.*;
import com.ainovel.app.modelconfig.AiSelectionContext;
import com.ainovel.app.story.model.Outline;
import com.ainovel.app.story.repo.OutlineRepository;
import com.ainovel.app.user.User;
import com.ainovel.app.world.WorldBindingService;
import com.ainovel.app.worldcontext.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.ainovel.app.quality.language.LanguageProjection.hash;
import static com.ainovel.app.worldcontext.WorldContextDtos.*;

@Service
public class OutlineAiService {
    public static final String TYPE="OUTLINE_WRITING_V1";
    public record Request(String action,UUID nodeId,String instruction,String text,Integer chapterNumber,Integer sectionsPerChapter,
            String contextHash,Long expectedSelectionRevision,Long expectedRevision) {}
    public record Frozen(UUID outlineId,long outlineRevision,UUID nodeId,String action,int chapterNumber,int scenesCount,
            String beforeText,String instruction,String necessaryContext,Selection worldContext,AiChatRequest prompt) {}
    public record Accepted(UUID resultId,UUID operationId) {}
    public record Decision(Long expectedRevision,Long expectedOutlineRevision,String contextHash,String requestKey) {}
    public record Result(UUID id,UUID operationId,String action,UUID nodeId,String status,long revision,long sourceRevision,
            String beforeText,JsonNode candidate,Selection worldContext,String rawResult,java.time.Instant createdAt,int chapterNumber,int scenesCount,String instruction) {}
    private final OutlineAiStore store;private final OutlineRepository outlines;private final WorldContextPreferences preferences;
    private final WorldContextSelector selector;private final WorldContextQuery queries;private final WorldBindingService bindings;
    private final ObjectMapper json;private final AiService ai;private final AiOperationService operations;
    private final TransactionTemplate tx;private final ApplicationEventPublisher events;
    public OutlineAiService(OutlineAiStore store,OutlineRepository outlines,WorldContextPreferences preferences,WorldContextSelector selector,
            WorldContextQuery queries,WorldBindingService bindings,ObjectMapper json,AiService ai,@Lazy AiOperationService operations,
            TransactionTemplate tx,ApplicationEventPublisher events) {
        this.store=store;this.outlines=outlines;this.preferences=preferences;this.selector=selector;this.queries=queries;this.bindings=bindings;
        this.json=json;this.ai=ai;this.operations=operations;this.tx=tx;this.events=events;
    }
    @Transactional(readOnly=true)
    public Frozen prepare(User user,UUID outlineId,Request request) {
        var o=preferences.outline(user,outlineId);
        if(request==null||!Set.of("CHAPTER_GENERATE","CHAPTER_REFINE","SCENE_REFINE").contains(Objects.toString(request.action(),"")))throw WorldContextSelector.error("OUTLINE_AI_ACTION_INVALID");
        if(request.expectedRevision()!=null&&o.getRevision()!=request.expectedRevision())throw WorldContextPreferences.conflict("STORY_RESOURCE_CONFLICT");
        boolean generate=request.action().equals("CHAPTER_GENERATE");
        if(!generate&&request.nodeId()==null)throw WorldContextSelector.error("WORLD_CONTEXT_NODE_MISSING");
        var node=queries.node(o,request.nodeId());
        if(!generate&&(request.action().equals("CHAPTER_REFINE")!=node.has("scenes")))throw WorldContextSelector.error("OUTLINE_AI_ACTION_INVALID");
        String before=generate?"":Objects.toString(request.text(),node.path("summary").asText());
        if(!generate&&before.isBlank())throw WorldContextSelector.error("OUTLINE_AI_EMPTY_TEXT");
        int chapter=request.chapterNumber()==null?queries.tree(o).path("chapters").size()+1:request.chapterNumber();
        int count=request.sectionsPerChapter()==null?5:Math.max(5,Math.min(7,request.sectionsPerChapter()));
        String query=queries.outline(o,request.nodeId(),request.instruction(),before);
        int required=com.ainovel.app.manuscript.context.SceneDraftContextCompiler.estimateTokens(query);
        if(required>3500)throw WorldContextSelector.error("WORLD_CONTEXT_REQUIRED_CONTEXT_TOO_LARGE");
        var binding=bindings.requireUsable(bindings.resolve(o.getStory(),o,null));
        var world=selector.select(binding,preferences.read(WorldContextPreferences.scope(o),binding),query,3500-required,false);
        String instructions=generate?"生成第 "+chapter+" 章，必须返回 JSON：{\"title\":\"章节标题\",\"summary\":\"章节摘要\",\"scenes\":[{\"title\":\"节标题\",\"summary\":\"节剧情摘要\"}]}。scenes 必须恰好 "+count+" 项。只输出 JSON。":
                "润色目标摘要，保留作者已有剧情和目标，不擅自改写其他资料。只输出润色后的摘要。";
        var prompt=new AiChatRequest(List.of(new AiChatRequest.Message("system","你是长篇小说策划编辑。世界材料是作者设定，章节规划是尚未发生的计划。"),
                new AiChatRequest.Message("user",instructions+"\n"+query+"\n实际纳入的世界材料：\n"+world.content())),null,null).forFeature(feature(request.action()));
        var frozen=new Frozen(outlineId,o.getRevision(),request.nodeId(),request.action(),chapter,count,before,Objects.toString(request.instruction(),""),query,world,prompt);
        if(request.contextHash()!=null&&!request.contextHash().equals(hash(store.write(frozen))))throw WorldContextPreferences.conflict("WORLD_CONTEXT_CHANGED");
        if(request.expectedSelectionRevision()!=null&&request.expectedSelectionRevision()!=world.selectionRevision())throw WorldContextPreferences.conflict("WORLD_CONTEXT_SELECTION_CHANGED");
        return frozen;
    }
    public static String feature(String action){return switch(action){case "CHAPTER_GENERATE"->"outline.chapter";case "CHAPTER_REFINE"->"outline.chapter_refine";case "SCENE_REFINE"->"outline.scene_refine";default->throw WorldContextSelector.error("OUTLINE_AI_ACTION_INVALID");};}
    public String fingerprint(Frozen frozen){return hash(store.write(frozen));}
    @Transactional
    public Accepted submit(User user,UUID outlineId,Request request,String key) {
        if(key==null||key.isBlank()||key.length()>128)throw WorldContextSelector.error("OUTLINE_AI_KEY_INVALID");
        var o=preferences.outline(user,outlineId);bindings.lockCurrent(o);
        String requestHash=hash(store.write(request));var prior=store.replay(outlineId,key);
        if(prior!=null){if(!prior.requestHash().equals(requestHash))throw WorldContextPreferences.conflict("OUTLINE_AI_IDEMPOTENCY_CONFLICT");return new Accepted(prior.id(),prior.operationId());}
        var frozen=prepare(user,outlineId,request);WorldContextSelector.requireUsable(frozen.worldContext());
        var quote=AiSelectionContext.current();
        if(quote!=null&&quote.writingInput()!=null&&"OUTLINE".equals(quote.writingInput().purpose())) {
            var quoted=json.convertValue(quote.writingInput().frozen(),Frozen.class);
            if(!fingerprint(quoted).equals(fingerprint(frozen)))throw WorldContextPreferences.conflict("WORLD_CONTEXT_CHANGED");frozen=quoted;
        }
        UUID id=store.create(outlineId,user.getId(),key,requestHash,request.action(),frozen);
        var accepted=operations.submit(user,TYPE,"OUTLINE",outlineId,Map.of("resultId",id),2,"准备冻结的大纲上下文","outline-result:"+id);
        store.attach(id,accepted.operationId());return new Accepted(id,accepted.operationId());
    }
    public Object execute(AiOperationExecution execution,UUID id) {
        var row=store.get(id,false);if(!row.userId().equals(execution.user().getId())||!Objects.equals(row.operationId(),execution.operationId()))throw WorldContextSelector.error("OUTLINE_AI_RESULT_MISSING");
        generate(execution.user(),id);
        execution.progress().step("保留候选与实际来源",1,2);
        return AiOperationExecutionContext.complete(()->tx.execute(status->{
            var current=preferences.outline(execution.user(),rowOutline(id));bindings.lockCurrent(current);var locked=store.get(id,true);
            var f=store.read(locked.frozenJson(),Frozen.class);
            if("CHAPTER_GENERATE".equals(f.action())&&"READY".equals(locked.status())&&current(f,current))apply(current,locked,"automatic");
            return result(store.get(id,false));
        }));
    }
    private void generate(User user,UUID id) {
        var row=store.get(id,false);
        if(row.candidateJson()==null&&row.rawResultJson()==null) {
            var f=store.read(row.frozenJson(),Frozen.class);WorldContextSelector.requireUsable(f.worldContext());
            var response=ai.chatWithIdempotency(user,f.prompt(),"outline-result:"+id);store.raw(id,response);
        }
        row=store.get(id,false);
        if(row.candidateJson()==null) {
            var f=store.read(row.frozenJson(),Frozen.class);String content=store.read(row.rawResultJson(),AiChatResponse.class).content();
            try {store.candidate(id,parse(id,f,content),"READY");}
            catch(RuntimeException error){store.candidate(id,null,"INVALID");throw WorldContextSelector.error("OUTLINE_AI_RESULT_INVALID");}
        }
    }
    public static Request chapterRequest(com.ainovel.app.story.dto.OutlineChapterGenerateRequest request) {
        return new Request("CHAPTER_GENERATE",null,null,null,request.chapterNumber(),request.sectionsPerChapter(),null,null,null);
    }
    @Transactional(readOnly=true)
    public UUID sceneOutline(User user,UUID sceneId) {
        for(var outline:outlines.findAllWithStoryUser())if(outline.getStory().getUser().getId().equals(user.getId()))
            for(var chapter:queries.tree(outline).path("chapters"))for(var scene:chapter.path("scenes"))if(sceneId.toString().equals(scene.path("id").asText()))return outline.getId();
        throw WorldContextSelector.error("WORLD_CONTEXT_NODE_MISSING");
    }
    /** Existing synchronous clients retain their response shape, with the same durable input/result path. */
    public Result compatible(User user,UUID outlineId,Request request) {
        var quote=AiSelectionContext.current();String key=quote==null?UUID.randomUUID().toString():"quote:"+quote.identity();
        UUID id=tx.execute(status->{
            var outline=preferences.outline(user,outlineId);bindings.lockCurrent(outline);
            String requestHash=hash(store.write(request));var replay=store.replay(outlineId,key);
            if(replay!=null){if(!replay.requestHash().equals(requestHash))throw WorldContextPreferences.conflict("OUTLINE_AI_IDEMPOTENCY_CONFLICT");return replay.id();}
            var frozen=prepare(user,outlineId,request);WorldContextSelector.requireUsable(frozen.worldContext());
            if(quote!=null&&quote.writingInput()!=null){var quoted=json.convertValue(quote.writingInput().frozen(),Frozen.class);
                if(!fingerprint(quoted).equals(fingerprint(frozen)))throw WorldContextPreferences.conflict("WORLD_CONTEXT_CHANGED");frozen=quoted;}
            return store.create(outlineId,user.getId(),key,requestHash,request.action(),frozen);
        });
        generate(user,id);
        return tx.execute(status->{var outline=preferences.outline(user,outlineId);bindings.lockCurrent(outline);var row=store.get(id,true);var frozen=store.read(row.frozenJson(),Frozen.class);
            if(frozen.action().equals("CHAPTER_GENERATE")&&row.status().equals("READY")&&current(frozen,outline))apply(outline,row,"automatic");return result(store.get(id,false));});
    }
    private UUID rowOutline(UUID id){return store.get(id,false).outlineId();}
    private boolean current(Frozen f,Outline outline) {
        var binding=bindings.resolve(outline.getStory(),outline,null);
        if(outline.getRevision()!=f.outlineRevision()||!bindings.same(f.worldContext().binding(),binding)||
                preferences.read(WorldContextPreferences.scope(outline),binding).revision()!=f.worldContext().selectionRevision())return false;
        try{return fingerprint(f).equals(fingerprint(prepare(outline.getStory().getUser(),outline.getId(),new Request(f.action(),f.nodeId(),f.instruction(),f.beforeText(),f.chapterNumber(),f.scenesCount(),null,null,f.outlineRevision()))));}
        catch(com.ainovel.app.common.ApiStatusException changed){return false;}
    }
    private JsonNode parse(UUID id,Frozen f,String raw) {
        if(raw==null||raw.isBlank())throw new IllegalArgumentException();
        if(!f.action().equals("CHAPTER_GENERATE"))return json.createObjectNode().put("text",raw.strip());
        String text=raw.strip();if(text.startsWith("```")){int start=text.indexOf('{'),end=text.lastIndexOf('}');if(start<0||end<start)throw new IllegalArgumentException();text=text.substring(start,end+1);}
        var node=store.tree(text);if(!node.isObject()||node.path("title").asText().isBlank()||node.path("summary").asText().isBlank()||!node.path("scenes").isArray()||node.path("scenes").size()!=f.scenesCount())throw new IllegalArgumentException();
        var chapter=json.createObjectNode().put("id",stable(id,"chapter")).put("title",node.path("title").asText()).put("summary",node.path("summary").asText());
        var scenes=chapter.putArray("scenes");int index=0;
        for(var s:node.path("scenes")){if(s.path("title").asText().isBlank()||s.path("summary").asText().isBlank())throw new IllegalArgumentException();
            scenes.addObject().put("id",stable(id,"scene:"+index)).put("title",s.path("title").asText()).put("summary",s.path("summary").asText()).put("order",++index);}
        return chapter;
    }
    private static String stable(UUID id,String part){return UUID.nameUUIDFromBytes((id+":"+part).getBytes(StandardCharsets.UTF_8)).toString();}
    private void apply(Outline outline,OutlineAiStore.Row row,String key) {
        var root=(ObjectNode)queries.tree(outline);var chapters=(ArrayNode)root.withArray("chapters");var candidate=(ObjectNode)store.tree(row.candidateJson());
        candidate.put("order",chapters.size()+1);chapters.add(candidate);outline.setContentJson(store.write(root));outlines.saveAndFlush(outline);
        store.applied(row.id(),Map.of("requestKey",key,"chapterId",candidate.path("id").asText(),"outlineRevision",outline.getRevision()));
        events.publishEvent(new com.ainovel.app.narrative.NarrativeSourceChanged(null,outline.getId()));
    }
    @Transactional
    public Result accept(User user,UUID outlineId,UUID id,Decision decision) {
        var outline=preferences.outline(user,outlineId);bindings.lockCurrent(outline);var row=owned(user,outlineId,id,true);
        if("APPLIED".equals(row.status())) {
            if(decision==null||!Objects.equals(store.tree(row.receiptJson()).path("requestKey").asText(),decision.requestKey()))throw WorldContextPreferences.conflict("OUTLINE_AI_RESULT_DECIDED");
            return result(row);
        }
        if(decision==null||decision.expectedRevision()==null||decision.expectedRevision()!=row.revision()||decision.expectedOutlineRevision()==null||decision.expectedOutlineRevision()!=outline.getRevision())throw WorldContextPreferences.conflict("OUTLINE_AI_RESULT_CHANGED");
        var f=store.read(row.frozenJson(),Frozen.class);
        if(!f.action().equals("CHAPTER_GENERATE")||!row.status().equals("READY"))throw WorldContextSelector.error("OUTLINE_AI_RESULT_NOT_APPLICABLE");
        var now=prepare(user,outlineId,new Request(f.action(),null,f.instruction(),null,f.chapterNumber(),f.scenesCount(),null,null,outline.getRevision()));
        WorldContextSelector.requireUsable(now.worldContext());
        if(!Objects.equals(decision.contextHash(),fingerprint(now)))throw WorldContextPreferences.conflict("WORLD_CONTEXT_CHANGED");
        if(decision.requestKey()==null||decision.requestKey().isBlank())throw WorldContextSelector.error("OUTLINE_AI_KEY_INVALID");
        apply(outline,row,decision.requestKey());return result(store.get(id,false));
    }
    @Transactional(readOnly=true) public List<Result> list(User user,UUID outline){preferences.outline(user,outline);return store.list(outline).stream().map(this::result).toList();}
    @Transactional(readOnly=true) public Result get(User user,UUID outline,UUID id){return result(owned(user,outline,id,false));}
    private OutlineAiStore.Row owned(User user,UUID outline,UUID id,boolean lock){preferences.outline(user,outline);var row=store.get(id,lock);if(!row.outlineId().equals(outline)||!row.userId().equals(user.getId()))throw WorldContextSelector.error("OUTLINE_AI_RESULT_MISSING");return row;}
    private Result result(OutlineAiStore.Row row){var f=store.read(row.frozenJson(),Frozen.class);return new Result(row.id(),row.operationId(),row.action(),f.nodeId(),row.status(),row.revision(),f.outlineRevision(),f.beforeText(),row.candidateJson()==null?null:store.tree(row.candidateJson()),f.worldContext(),row.rawResultJson()==null?null:store.read(row.rawResultJson(),AiChatResponse.class).content(),row.createdAt(),f.chapterNumber(),f.scenesCount(),f.instruction());}
}
