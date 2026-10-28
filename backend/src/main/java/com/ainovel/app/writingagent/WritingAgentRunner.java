package com.ainovel.app.writingagent;

import com.ainovel.app.ai.*;
import com.ainovel.app.ai.dto.*;
import com.ainovel.app.aioperation.*;
import com.ainovel.app.assistant.AssistantDocumentProjection;
import com.ainovel.app.assistant.AssistantDtos.Source;
import com.ainovel.app.modelconfig.AiSelectionContext;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Component;
import java.util.*;
import static com.ainovel.app.writingagent.WritingAgentDtos.*;
import static com.ainovel.app.writingagent.WritingAgentStore.hash;
import static com.ainovel.app.modelconfig.AiModelConfigurationService.bytes;

/** Replays the immutable journal to the first missing step; never rebuilds an old model request. */
@Component
public class WritingAgentRunner {
    private final WritingAgentService service;
    private final WritingAgentStore store;
    public WritingAgentRunner(WritingAgentService service,WritingAgentStore store){this.service=service;this.store=store;}
    public record ToolCheckpoint(JsonNode output,WorldAgentTools.ReadState world,List<Source> contextReads,
            Candidate candidate,Map<String,JsonNode> checks,String pause,int sequence,int modelCalls,int controls,int paid,int local,int revisions,int drafts,int noProgress,int languageChecks,int plotChecks) {}
    private static final class Execution {
        int sequence,modelCalls,controls,paid,local,revisions,drafts,noProgress,languageChecks,plotChecks;
        WorldAgentTools.ReadState world=WorldAgentTools.ReadState.empty();
        List<Source> reads=new ArrayList<>();Candidate candidate;
        Map<String,JsonNode> checks=new LinkedHashMap<>();
    }
    public Object execute(AiOperationExecution operation,UUID runId){
        var run=store.run(runId);if(!run.userId().equals(operation.user().getId())||!operation.operationId().equals(run.operationId()))throw error("WRITING_AGENT_NOT_FOUND");
        var f=store.read(run.frozen(),Frozen.class);if(!PROTOCOL.equals(f.protocol())||f.selections()==null)throw error("WRITING_AGENT_PROTOCOL_UNSUPPORTED");
        if(run.status().equals("CANCELLED"))return Map.of("runId",runId,"status","CANCELLED");
        var x=new Execution();
        if(f.toolsJson()==null||f.controlMessages()==null||f.controlMessages().isEmpty()||!Objects.equals(f.candidateTemplateHash(),service.candidates().templateHash()))return stop(runId,x,"PAUSED_INPUT","WRITING_AGENT_PROTOCOL_UNAVAILABLE",null);
        List<AiChatRequest.Message> messages=new ArrayList<>(f.controlMessages());
        var names=new HashSet<String>();for(var t:store.tree(f.toolsJson()))names.add(t.path("function").path("name").asText());
        try(var selection=AiSelectionContext.install(f.selections())){
            AiOperationExecutionContext.apply(()->{if(!store.run(runId).status().equals("PAUSE_REQUESTED"))store.state(runId,"RUNNING",Map.of("message","准备按需取材与自主创作"));return null;});
            while(true){
                if(store.run(runId).status().equals("CANCELLED"))return Map.of("runId",runId,"status","CANCELLED");
                if(store.run(runId).status().equals("PAUSE_REQUESTED"))return stop(runId,x,"PAUSED_USER","已暂停，保留所有步骤与候选",null);
                if(x.controls>=f.limits().controlCalls()||x.modelCalls>=f.limits().modelCalls())return stop(runId,x,"PAUSED_BUDGET","WRITING_AGENT_CALL_LIMIT",null);
                x.controls++;x.modelCalls++;int ordinal=++x.sequence;String modelKey="control:"+x.controls;
                var controlSources=retrievedSources(f,x);String editable=store.write(f.document().runs());
                controlSources.add(new Source("scene:"+f.sceneId()+":editable","CANDIDATE","本次可编辑原文",editable,f.sceneId(),hash(editable),true,"initial-input"));
                var controlManifest=store.mapper().createObjectNode();controlManifest.set("writingAgentSources",store.mapper().valueToTree(controlSources));
                var request=new AiChatRequest(List.copyOf(messages),null,controlManifest).forFeature("writing_agent.control").withNativeTools(f.toolsJson(),f.selections().outputBudget("writing_agent.control"));
                operation.progress().step("自主决策 "+x.controls,x.modelCalls-1,f.limits().modelCalls());
                var response=service.model(operation.user(),runId,ordinal,modelKey,request);
                if(store.run(runId).status().equals("CANCELLED"))return Map.of("runId",runId,"status","CANCELLED");
                if(store.run(runId).status().equals("PAUSE_REQUESTED"))return stop(runId,x,"PAUSED_USER","已暂停，保留原模型结果等待继续",null);
                final AiNativeResult nativeResult;try{nativeResult=response.nativeResult();}catch(IllegalArgumentException invalid){return stop(runId,x,"PAUSED_INPUT","WRITING_AGENT_"+AiResultDiagnostics.unusable(response).name(),null);}
                if(nativeResult==null||"length".equalsIgnoreCase(nativeResult.finishReason())||"max_tokens".equalsIgnoreCase(nativeResult.finishReason()))return stop(runId,x,"PAUSED_INPUT","WRITING_AGENT_INCOMPLETE_RESPONSE",null);
                if(nativeResult.toolCalls().isEmpty()&&(response.content()==null||response.content().isBlank()))return stop(runId,x,"PAUSED_INPUT","WRITING_AGENT_"+AiResultDiagnostics.unusable(response).name(),null);
                if(f.context().h2()&&!"AUTHOR_QA".equals(f.intent())&&nativeResult.toolCalls().stream().anyMatch(t->t.name().startsWith("world_")))return stop(runId,x,"PAUSED_INPUT","H2_WORLD_TOOL_UNAVAILABLE",null);
                if("AUTHOR_QA".equals(f.intent())&&nativeResult.toolCalls().stream().anyMatch(t->t.name().startsWith("candidate_")))return stop(runId,x,"PAUSED_INPUT","AUTHOR_QA_READ_ONLY",null);
                messages.add(nativeResult.message());
                if(nativeResult.toolCalls().isEmpty()){
                    String status="AUTHOR_QA".equals(f.intent())||x.candidate!=null&&service.worlds().requiredComplete(f.world(),x.world)?"COMPLETED":"WAITING_AUTHOR";
                    if(status.equals("WAITING_AUTHOR")){
                        String answer=authorAnswer(runId,modelKey);
                        if(!answer.isBlank()){messages.add(new AiChatRequest.Message("user","作者补充的创作要求：\n"+answer));continue;}
                    }
                    return stop(runId,x,status,Objects.toString(response.content(),"未产生候选"),modelKey);
                }
                boolean dependentBatch=nativeResult.toolCalls().size()>1&&nativeResult.toolCalls().stream().anyMatch(t->!t.name().startsWith("world_")&&!t.name().startsWith("context_"));
                for(var call:nativeResult.toolCalls()){
                    var key="tool:"+call.id();var prior=store.step(runId,key);ToolCheckpoint result;var arguments=parseArguments(call.arguments());
                    if(prior!=null&&(prior.sequence()!=x.sequence+1||!prior.feature().equals(call.name())||!prior.requestHash().equals(hash(store.write(arguments)))))throw error("WRITING_AGENT_REQUEST_CHANGED");
                    if(prior!=null&&prior.result()!=null){result=store.read(prior.result(),ToolCheckpoint.class);}
                    else {
                        store.request(runId,++x.sequence,key,"TOOL",call.name(),arguments);
                        if(!names.contains(call.name())||dependentBatch)result=checkpoint(x,Map.of("error",dependentBatch?"WRITING_AGENT_SEPARATE_DEPENDENT_TOOLS":"WRITING_AGENT_TOOL_UNAVAILABLE"),null);
                        else result=tool(operation,runId,f,x,call.name(),arguments);
                        var saved=result;AiOperationExecutionContext.apply(()->{store.result(runId,key,saved);return null;});
                    }
                    x.world=result.world();x.reads=new ArrayList<>(result.contextReads());x.candidate=result.candidate();x.checks=new LinkedHashMap<>(result.checks());
                    x.sequence=result.sequence();x.modelCalls=result.modelCalls();x.controls=result.controls();x.paid=result.paid();x.local=result.local();x.revisions=result.revisions();x.drafts=result.drafts();x.noProgress=result.noProgress();
                    x.languageChecks=result.languageChecks();x.plotChecks=result.plotChecks();
                    messages.add(new AiChatRequest.Message("tool",store.write(result.output()),call.id(),null,null,null));
                    if(result.pause()!=null){
                        if(result.pause().equals("WAITING_AUTHOR")){
                            String answer=store.tree(store.run(runId).state()).path("authorAnswers").path(key).asText();
                            if(!answer.isBlank()){messages.add(new AiChatRequest.Message("user","作者对本次问题的答复：\n"+answer));continue;}
                        }
                        return stop(runId,x,result.pause(),result.output().path("answer").asText(result.output().path("question").asText("请查看已保留结果")),key);
                    }
                }
                if(x.noProgress>=2){
                    String questionKey="progress:"+modelKey,answer=authorAnswer(runId,questionKey);
                    if(answer.isBlank())return stop(runId,x,"WAITING_AUTHOR","WRITING_AGENT_NO_PROGRESS",questionKey);
                    messages.add(new AiChatRequest.Message("user","作者对继续打磨的要求：\n"+answer));x.noProgress=0;
                }
                var state=progress(x,"继续根据工具结果自主决定下一步");AiOperationExecutionContext.apply(()->{String status=store.run(runId).status();store.state(runId,status.equals("PAUSE_REQUESTED")?status:"RUNNING",state);return null;});
            }
        }catch(AiResultUncertainException uncertain){return stop(runId,x,"RECOVERY_REQUIRED","AI_RESULT_RECONCILIATION_REQUIRED",null);}
        catch(AiRequestNotSentException failure){return stop(runId,x,"WRITING_AGENT_PAUSED".equals(failure.getMessage())?"PAUSED_USER":"PAUSED_BUDGET",safe(failure.getMessage()),null);}
        catch(com.ainovel.app.modelconfig.AiPaymentRequiredException pending){return stop(runId,x,"PAUSED_BUDGET","AI_PAYMENT_REQUIRED",null);}
        catch(com.ainovel.app.common.ApiStatusException failure){String code=safe(failure.getMessage());return stop(runId,x,code.contains("BUDGET")||code.contains("LIMIT")?"PAUSED_BUDGET":"PAUSED_INPUT",code,null);}
        catch(RuntimeException failure){if(Thread.currentThread().isInterrupted())throw failure;return stop(runId,x,"FAILED",safe(failure.getMessage()),null);}
    }
    private String authorAnswer(UUID runId,String key){return store.tree(store.run(runId).state()).path("authorAnswers").path(key).asText();}
    private ToolCheckpoint tool(AiOperationExecution op,UUID runId,Frozen f,Execution x,String name,JsonNode args){
        if(name.equals("ask_author"))return checkpoint(x,Map.of("question",args.path("question").asText("请补充关键设定")),"WAITING_AUTHOR");
        if(name.equals("finish")){
            if(!service.worlds().requiredComplete(f.world(),x.world))return checkpoint(x,Map.of("error","WRITING_AGENT_REQUIRED_READS","mustRead",service.worlds().requiredReads(f.world(),x.world)),null);
            if(!"AUTHOR_QA".equals(f.intent())&&x.candidate==null)return checkpoint(x,Map.of("error","WRITING_AGENT_CANDIDATE_REQUIRED"),null);
            return checkpoint(x,Map.of("answer",args.path("answer").asText("候选已交付作者审阅")),"COMPLETED");
        }
        if(name.startsWith("world_")||name.startsWith("context_")){
            if(++x.local>f.limits().localTools())return checkpoint(x,Map.of("error","WRITING_AGENT_TOOL_LIMIT"),"PAUSED_BUDGET");
            if(name.startsWith("world_")){var out=service.worlds().invoke(f.world(),name,args,x.world);x.world=out.state();return checkpoint(x,out.output(),null);}
            return context(f,x,name,args);
        }
        if("AUTHOR_QA".equals(f.intent()))return checkpoint(x,Map.of("error","AUTHOR_QA_READ_ONLY"),null);
        boolean drafting=name.equals("candidate_draft"),refining=name.equals("candidate_refine");
        if(drafting&&x.drafts>=1)return checkpoint(x,Map.of("error","WRITING_AGENT_DRAFT_LIMIT"),null);
        if(!drafting&&x.candidate==null)return checkpoint(x,Map.of("error","WRITING_AGENT_CANDIDATE_REQUIRED"),null);
        if(refining&&x.revisions>=f.limits().revisions())return checkpoint(x,Map.of("error","WRITING_AGENT_REVISION_LIMIT"),"PAUSED_BUDGET");
        if((drafting||refining)&&!service.worlds().requiredComplete(f.world(),x.world))return checkpoint(x,Map.of("error","WRITING_AGENT_REQUIRED_READS","mustRead",service.worlds().requiredReads(f.world(),x.world)),null);
        var reads=retrievedSources(f,x);
        String instruction=f.instruction()+(refining?"\n本次修订要求："+args.path("instruction").asText():"");
        var input=new CandidateAgentTools.Input(instruction,x.candidate==null?f.document().sourceHtml():x.candidate.html(),f.generationRules(),reads,f.context().minHan(),f.context().maxHan(),f.context().h2()&&!"AUTHOR_QA".equals(f.intent()),f.document(),x.candidate==null?List.of():x.candidate.replacements());
        String cacheKey=null;AiChatResponse generated=null;
        try {
            AiChatRequest request=switch(name){case "candidate_draft"->service.candidates().draftRequest(input,f.mode());case "candidate_refine"->service.candidates().refineRequest(input);case "candidate_check_language"->service.candidates().languageRequest(input);case "candidate_check_plot"->service.candidates().plotRequest(input);default->throw error("WRITING_AGENT_TOOL_UNAVAILABLE");};
            var actualSources=new ArrayList<Source>();if(!name.equals("candidate_check_language"))actualSources.addAll(reads);
            String sentBody=name.equals("candidate_check_language")||name.equals("candidate_check_plot")?com.ainovel.app.quality.language.LanguageProjection.of(input.candidateHtml()).text():input.candidateHtml();
            actualSources.add(new Source(x.candidate==null?"scene:"+f.sceneId():"candidate:"+x.candidate.id(),"CANDIDATE","当前场景候选",sentBody,f.sceneId(),hash(sentBody),false,"current-target"));
            var manifest=store.mapper().createObjectNode();manifest.set("writingAgentSources",store.mapper().valueToTree(actualSources));
            request=new AiChatRequest(request.messages(),request.modelId(),manifest,request.featureKey(),request.toolsJson(),request.maxOutputTokens());
            String cache=hash(store.write(request));cacheKey=cache;if(!drafting&&!refining&&x.checks.containsKey(cache))return checkpoint(x,x.checks.get(cache),null);
            if(x.paid>=f.limits().paidTools()||x.modelCalls>=f.limits().modelCalls())return checkpoint(x,Map.of("error","WRITING_AGENT_CALL_LIMIT"),"PAUSED_BUDGET");
            if(name.equals("candidate_check_language")&&x.languageChecks>=4||name.equals("candidate_check_plot")&&x.plotChecks>=4)return checkpoint(x,Map.of("error","WRITING_AGENT_CHECK_LIMIT"),"PAUSED_BUDGET");
            recount(x,name);x.modelCalls++;var response=service.model(op.user(),runId,++x.sequence,"worker:"+x.paid,request);generated=response;
            if(response.content()==null||response.content().isBlank()){var failure=store.mapper().valueToTree(Map.of("error","WRITING_AGENT_"+AiResultDiagnostics.unusable(response).name()));x.checks.put(cache,failure);return checkpoint(x,failure,null);}
            if(drafting||refining){
                var parsed=service.candidates().parseCandidate(response.content(),input);int sequence=(x.candidate==null?0:x.candidate.sequence())+1;
                UUID id=UUID.nameUUIDFromBytes((runId+":candidate:"+x.paid).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                var sourceNodes=actualSources.stream().map(s->store.mapper().<JsonNode>valueToTree(s)).toList();
                var c=new Candidate(id,sequence,x.candidate==null?null:x.candidate.id(),parsed.html(),parsed.contentHash(),parsed.actualHan(),parsed.replacements(),sourceNodes);
                // The candidate's deterministic key handles replay. Do not hold the run lock while
                // InnoDB checks manuscript/branch foreign keys; adoption locks manuscript before run.
                store.isolated(()->{store.jdbc().update("insert into narrative_generation_candidates(id,manuscript_id,branch_id,scene_id,content,stamp_json,status,created_at,operation_id,generation_sequence,manuscript_version,min_han,max_han,actual_han,quality_status,source_kind,metadata_json) values(?,?,?,?,?,?,'READY',current_timestamp(6),?,?,?,?,?,?,'NOT_PROCESSED','AGENT',?) on duplicate key update id=id",
                        bytes(c.id()),bytes(f.manuscriptId()),bytes(f.branchId()),bytes(f.sceneId()),c.html(),"{}",bytes(runId),c.sequence(),f.sourceVersion(),f.context().minHan(),f.context().maxHan(),c.actualHan(),store.write(c));return null;});
                x.candidate=c;x.noProgress=0;return checkpoint(x,Map.of("candidateId",id,"candidateHash",c.hash(),"actualHan",c.actualHan(),"minHan",f.context().minHan(),"maxHan",f.context().maxHan(),"message","候选已保留，尚未修改正文"),null);
            }
            var report=name.equals("candidate_check_language")?service.candidates().parseLanguage(response.content(),input):service.candidates().parsePlot(response.content(),input);
            var output=store.mapper().valueToTree(Map.of("candidateId",x.candidate.id(),"candidateHash",x.candidate.hash(),"kind",report.kind(),"report",report.data()));x.checks.put(cache,output);return checkpoint(x,output,null);
        }catch(AiRequestNotSentException notSent){throw notSent;}
        catch(com.ainovel.app.modelconfig.AiPaymentRequiredException pending){throw pending;}
        catch(com.ainovel.app.common.ApiStatusException business){String code=safe(business.getMessage());if(code.equals("WRITING_AGENT_NO_CHANGE"))x.noProgress++;else if(generated!=null&&!code.contains("INPUT_LIMIT"))code="WRITING_AGENT_"+AiResultDiagnostics.unusable(generated).name();var failure=store.mapper().valueToTree(Map.of("error",code));if(cacheKey!=null&&!drafting&&!refining)x.checks.put(cacheKey,failure);return checkpoint(x,failure,code.contains("INPUT_LIMIT")?"PAUSED_INPUT":null);}
        catch(IllegalArgumentException invalid){String code=generated==null?"WRITING_AGENT_TOOL_INPUT_INVALID":"WRITING_AGENT_"+AiResultDiagnostics.unusable(generated).name();var failure=store.mapper().valueToTree(Map.of("error",code));if(cacheKey!=null&&!drafting&&!refining)x.checks.put(cacheKey,failure);return checkpoint(x,failure,generated==null?"PAUSED_INPUT":null);}
    }
    private ArrayList<Source> retrievedSources(Frozen f,Execution x){
        var sources=new ArrayList<>(f.base());sources.addAll(x.reads);
        for(var r:x.world.receipts()){
            var origin=f.world().sources().stream().filter(s->s.id().equals(r.sourceId())).findFirst().orElse(null);
            sources.add(new Source("world:"+f.world().binding().versionId()+":"+r.sourceId()+":"+r.blockId()+":"+r.start(),origin==null?"WORLD_BLOCK":origin.type(),origin==null?"世界原文":origin.title(),r.text(),null,r.contentHash(),!r.fullBlock(),r.tool()));
        }
        return sources;
    }
    private ToolCheckpoint context(Frozen f,Execution x,String name,JsonNode args){
        var pool="AUTHOR_QA".equals(f.intent())?f.context().authorPool():f.context().editPool();var hits=new ArrayList<Map<String,Object>>();
        if(name.equals("context_search")){
            String q=args.path("query").asText("").strip();if(q.isBlank()||q.length()>500)return checkpoint(x,Map.of("error","WRITING_AGENT_QUERY_INVALID"),null);
            for(var s:pool.stream().filter(s->(s.title()+s.content()).toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT))).sorted(Comparator.comparing(Source::id)).limit(4).toList()){
                String text=s.content().substring(0,s.content().offsetByCodePoints(0,Math.min(160,s.content().codePointCount(0,s.content().length()))));hits.add(Map.of("sourceId",s.id(),"type",s.type(),"title",s.title(),"snippet",text,"block",0,"hasMore",s.content().length()>text.length()));
                addRead(x,new Source(s.id()+":snippet",s.type(),s.title(),text,s.sceneId(),hash(text),true,"context_search"));
            }
        }else{
            var source=pool.stream().filter(s->s.id().equals(args.path("sourceId").asText())).findFirst().orElse(null);
            if(source==null)return checkpoint(x,Map.of("error","WRITING_AGENT_SOURCE_UNAVAILABLE"),null);
            int block=args.path("block").asInt(0);if(block<0||block>100000)return checkpoint(x,Map.of("error","WRITING_AGENT_BLOCK_INVALID"),null);
            long cpStart=(long)block*900;int count=source.content().codePointCount(0,source.content().length());if(cpStart>=count)return checkpoint(x,Map.of("error","WRITING_AGENT_BLOCK_INVALID"),null);
            int start=source.content().offsetByCodePoints(0,(int)cpStart),end=source.content().offsetByCodePoints(start,Math.min(900,count-(int)cpStart));String text=source.content().substring(start,end);
            addRead(x,new Source(source.id()+":block:"+block,source.type(),source.title(),text,source.sceneId(),hash(text),start+text.length()<source.content().length(),"context_read"));
            hits.add(Map.of("sourceId",source.id(),"title",source.title(),"text",text,"start",start,"end",start+text.length(),"block",block,"hasMore",start+text.length()<source.content().length()));
        }
        return checkpoint(x,Map.of("sources",hits),null);
    }
    private void addRead(Execution x,Source source){if(x.reads.stream().noneMatch(s->s.id().equals(source.id()))){if(com.ainovel.app.manuscript.context.SceneDraftContextCompiler.estimateTokens(store.write(x.reads)+store.write(source))>3500)throw error("WRITING_AGENT_CONTEXT_LIMIT");x.reads.add(source);}}
    private static void recount(Execution x,String name){if(name.startsWith("world_")||name.startsWith("context_"))x.local++;else if(name.startsWith("candidate_")){x.paid++;if(name.equals("candidate_draft"))x.drafts++;if(name.equals("candidate_refine"))x.revisions++;if(name.equals("candidate_check_language"))x.languageChecks++;if(name.equals("candidate_check_plot"))x.plotChecks++;}}
    private ToolCheckpoint checkpoint(Execution x,Object output,String pause){return new ToolCheckpoint(store.mapper().valueToTree(output),x.world,List.copyOf(x.reads),x.candidate,Map.copyOf(x.checks),pause,x.sequence,x.modelCalls,x.controls,x.paid,x.local,x.revisions,x.drafts,x.noProgress,x.languageChecks,x.plotChecks);}
    private Object stop(UUID id,Execution x,String status,String answer,String questionKey){var progress=progress(x,answer);progress.put("answer",answer);if(questionKey!=null)progress.put("questionKey",questionKey);return AiOperationExecutionContext.complete(()->{store.state(id,status,progress);return Map.of("runId",id,"status",status);});}
    private Map<String,Object> progress(Execution x,String message){var state=new LinkedHashMap<String,Object>();state.put("message",message);state.put("modelCalls",x.modelCalls);state.put("controlCalls",x.controls);state.put("paidTools",x.paid);state.put("localTools",x.local);state.put("revisions",x.revisions);if(x.candidate!=null)state.put("candidateId",x.candidate.id());return state;}
    private JsonNode parseArguments(String text){try{var args=store.mapper().readTree(text);if(args==null||!args.isObject()||text.length()>16000)throw error("WRITING_AGENT_ARGUMENTS_INVALID");return args;}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw error("WRITING_AGENT_ARGUMENTS_INVALID");}}
    private static String safe(String value){return value!=null&&value.matches("[A-Z][A-Z0-9_]{1,100}")?value:"WRITING_AGENT_STEP_FAILED";}
}
