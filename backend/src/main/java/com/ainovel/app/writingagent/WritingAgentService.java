package com.ainovel.app.writingagent;

import com.ainovel.app.ai.*;
import com.ainovel.app.ai.dto.*;
import com.ainovel.app.aioperation.*;
import com.ainovel.app.assistant.*;
import com.ainovel.app.economy.EconomyService;
import com.ainovel.app.integration.AiGatewayGrpcClient;
import com.ainovel.app.manuscript.*;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.manuscript.attribution.SceneContentEditedEvent;
import com.ainovel.app.modelconfig.*;
import com.ainovel.app.narrative.*;
import com.ainovel.app.prompt.*;
import com.ainovel.app.quality.SlopPatternSamplingService;
import com.ainovel.app.quality.language.*;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.user.User;
import com.ainovel.app.v2.V2VersionPersistenceService;
import com.fasterxml.jackson.databind.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import static com.ainovel.app.writingagent.WritingAgentDtos.*;
import static com.ainovel.app.writingagent.WritingAgentStore.hash;
import static com.ainovel.app.modelconfig.AiModelConfigurationService.bytes;

@Service
public class WritingAgentService {
    public static final String TYPE="WRITING_AGENT";
    final WritingAgentStore store;
    final WorldAgentTools worlds;
    final CandidateAgentTools candidates;
    final WritingAgentPrompts prompts;
    private final ResourceAccessGuard access;
    private final ManuscriptRepository manuscripts;
    private final ManuscriptContentService contents;
    private final AssistantContext contexts;
    private final NarrativeContextService narrative;
    private final SceneGenerationService generation;
    private final PromptAssemblyService assembly;
    private final SlopPatternSamplingService slop;
    private final AiService ai;
    private final AiGatewayGrpcClient gateway;
    private final WritingAgentMoney money;
    private final EconomyService economy;
    private final AiModelConfigurationService configuration;
    private final AiOperationService operations;
    private final ManuscriptService manuscriptService;
    private final V2VersionPersistenceService versions;
    private final ApplicationEventPublisher events;
    @org.springframework.beans.factory.annotation.Autowired
    private com.ainovel.app.world.WorldBindingService worldBindings;
    public WritingAgentService(WritingAgentStore store,WorldAgentTools worlds,CandidateAgentTools candidates,WritingAgentPrompts prompts,
            ResourceAccessGuard access,ManuscriptRepository manuscripts,ManuscriptContentService contents,AssistantContext contexts,
            NarrativeContextService narrative,SceneGenerationService generation,PromptAssemblyService assembly,SlopPatternSamplingService slop,
            AiService ai,AiGatewayGrpcClient gateway,WritingAgentMoney money,EconomyService economy,AiModelConfigurationService configuration,
            @Lazy AiOperationService operations,@Lazy ManuscriptService manuscriptService,V2VersionPersistenceService versions,ApplicationEventPublisher events){
        this.store=store;this.worlds=worlds;this.candidates=candidates;this.prompts=prompts;this.access=access;this.manuscripts=manuscripts;
        this.contents=contents;this.contexts=contexts;this.narrative=narrative;this.generation=generation;this.assembly=assembly;this.slop=slop;
        this.ai=ai;this.gateway=gateway;this.money=money;this.economy=economy;this.configuration=configuration;this.operations=operations;
        this.manuscriptService=manuscriptService;this.versions=versions;this.events=events;
    }
    @Transactional(readOnly=true)
    public Frozen prepare(User user,UUID manuscriptId,UUID sceneId,Request request){
        if(request==null||request.branchId()==null||request.expectedVersion()==null||request.sourceHtml()==null||request.instruction()==null
                ||request.instruction().isBlank()||request.instruction().length()>8000||request.maxCostCny()==null||request.maxCostCny().signum()<=0
                ||request.maxCostCny().scale()>6||request.maxProjectCredits()==null||request.maxProjectCredits()<1)throw error("WRITING_AGENT_REQUEST_INVALID");
        var m=owned(user,manuscriptId,request.branchId());if(m.getVersion()!=request.expectedVersion())throw error("MANUSCRIPT_VERSION_CONFLICT");
        String intent=Objects.toString(request.intent(),"EDIT"),mode=Objects.toString(request.mode(),"fast");
        if(!Set.of("EDIT","AUTHOR_QA").contains(intent)||!Set.of("fast","crafted").contains(mode))throw error("WRITING_AGENT_REQUEST_INVALID");
        var limits=(request.limits()==null?Limits.defaults():request.limits()).checked();String server=contents.readScene(m,sceneId);
        if("EDIT".equals(intent)&&!server.equals(request.sourceHtml()))throw error("WRITING_AGENT_SAVE_REQUIRED");
        var snapshot=contexts.freeze(user,m,sceneId,request.instruction(),request.sourceHtml());
        var document=AssistantDocumentProjection.project(request.sourceHtml(),request.selection());
        if(!"AUTHOR_QA".equals(intent)&&document.runs().isEmpty())throw error("ASSISTANT_NO_EDITABLE_TEXT");
        var world=worlds.freeze(m,sceneId,snapshot.h2()&&!"AUTHOR_QA".equals(intent));
        if(request.expectedSelectionRevision()!=null&&request.expectedSelectionRevision()!=world.selectionRevision())throw error("WORLD_CONTEXT_SELECTION_CHANGED");
        var filtered=new AssistantContext.Snapshot(snapshot.h2(),snapshot.stamp(),stripWorld(snapshot.base()),stripWorld(snapshot.editPool()),stripWorld(snapshot.authorPool()),snapshot.minHan(),snapshot.maxHan(),null);
        var story=m.getOutline().getStory();var lang=generation.languageSelection(m);
        String safeTitle=snapshot.h2()?"当前作品":Objects.toString(story.getTitle(),""),genre=snapshot.h2()?"未指定":Objects.toString(story.getGenre(),"未指定"),tone=snapshot.h2()?"沉浸、连贯":Objects.toString(story.getTone(),"");
        var ruleInput=new SceneGenerationPromptInput(safeTitle,genre,tone,"","当前章节","",0,"当前场景","",0,"","",List.of(),List.of(),snapshot.minHan(),snapshot.maxHan(),"",24000);
        var rulePrompt=mode.equals("crafted")?assembly.assembleWithCreativeConstraints(ruleInput,slop.sample(sceneId),0):assembly.assembleSceneDraft(ruleInput);
        if(lang.generation())rulePrompt=LanguageStandard.augment(rulePrompt,lang.version(),true);
        var rules=rulePrompt.messages().stream().filter(x->"system".equals(x.role())).toList();
        var frozen=new Frozen(PROTOCOL,manuscriptId,request.branchId(),sceneId,m.getVersion(),hash(server),request.instruction(),intent,mode,document,filtered,world,filtered.base(),lang.generation()?LanguageStandard.text(lang.version()):"",rules,generation.planningHash(m),request.languageCheck(),request.plotCheck(),request.maxCostCny().setScale(6),request.maxProjectCredits(),limits,null);
        frozen=frozen.withProtocol(prompts.tools(frozen),prompts.initial(frozen,worlds.requiredReads(world,WorldAgentTools.ReadState.empty())),candidates.templateHash());
        if(request.contextHash()!=null&&!request.contextHash().equals(fingerprint(frozen)))throw error("WRITING_AGENT_CONTEXT_CHANGED");
        return frozen;
    }
    public String fingerprint(Frozen f){var n=(com.fasterxml.jackson.databind.node.ObjectNode)store.json.valueToTree(f);n.remove("selections");return hash(n.toString());}
    @Transactional(readOnly=true)
    public Preview preview(User user,UUID m,UUID scene,Request request){var f=prepare(user,m,scene,request);return new Preview(fingerprint(f),f.world().selectionRevision(),PROTOCOL,f.context().h2(),f.world().binding(),toolNames(f),f.world().issues(),f.limits(),f.maxCostCny(),f.maxProjectCredits(),f.controlMessages());}
    private List<String> toolNames(Frozen f){var names=new ArrayList<String>();for(var t:store.tree(f.toolsJson()))names.add(t.path("function").path("name").asText());return names;}
    public AiActionPlanner.Plan quote(User user,UUID m,UUID scene,JsonNode body){
        var f=prepare(user,m,scene,store.json.convertValue(body,Request.class));var steps=new ArrayList<AiActionPlanner.Step>();
        steps.add(step("writing_agent.control",false,f.limits().controlCalls(),4096));
        if(!"AUTHOR_QA".equals(f.intent())){
            steps.add(step("scene."+f.mode()+".draft",true,1,8192));
            if(f.limits().revisions()>0)steps.add(step("writing_agent.refine",true,f.limits().revisions(),8192));
            if(f.languageCheck())steps.add(step("language.diagnose",true,4,4096));
            if(f.plotCheck())steps.add(step("plot.diagnose",true,4,4096));
        }
        return new AiActionPlanner.Plan("/v1/manuscripts/"+m+"/scenes/"+scene+"/agent-runs",steps,fingerprint(f),m.toString(),null,null,new com.ainovel.app.worldcontext.WorldWritingInput("AGENT",fingerprint(f),f.world().selectionRevision(),store.json.valueToTree(f)));
    }
    private static AiActionPlanner.Step step(String feature,boolean conditional,int maximum,int output){return new AiActionPlanner.Step(feature,conditional,"UP_TO_"+maximum,24000,output);}
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Accepted submit(User user,UUID m,UUID scene,Request request,String key){
        key(key);lockOwned(user,m);
        var replay=store.replay(user.getId(),m,scene,key);String requestHash=hash(store.write(request));
        if(replay!=null){if(!replay.requestHash().equals(requestHash))throw error("WRITING_AGENT_IDEMPOTENCY_CONFLICT");return new Accepted(replay.id(),replay.operationId());}
        var f=prepare(user,m,scene,request);if(!f.world().issues().isEmpty())throw error("WRITING_AGENT_WORLD_REVIEW_REQUIRED");
        var selection=AiSelectionContext.current();if(selection==null||selection.writingInput()==null||!"AGENT".equals(selection.writingInput().purpose()))throw error("AI_QUOTE_REQUIRED");
        var quoted=store.json.convertValue(selection.writingInput().frozen(),Frozen.class);if(!fingerprint(f).equals(fingerprint(quoted)))throw error("AI_QUOTE_INPUT_CHANGED");
        var capability=configuration.nativeCapabilities(selection.selections().get("writing_agent.control").modelKey());
        if(!capability.budgetToolsDeclared()||!capability.budgetToolsAvailable())throw error("AI_NATIVE_BUDGET_UNAVAILABLE");
        if(operations.active(user,"AGENT_SCENE",scene)!=null)throw error("WRITING_AGENT_BUSY");
        var frozen=quoted.withSelection(selection);
        UUID id=store.create(user.getId(),frozen,key,requestHash);var op=operations.submit(user,TYPE,"AGENT_SCENE",scene,Map.of("runId",id),f.limits().modelCalls(),"准备自主创作","writing-agent:"+id);
        store.attach(id,op.operationId());return new Accepted(id,op.operationId());
    }
    private Manuscript owned(User user,UUID id,UUID branch){var m=access.requireOwnedManuscript(id,user);if(!Objects.equals(branch,m.getCurrentBranchId()))throw error("MANUSCRIPT_BRANCH_CONFLICT");return m;}
    private Manuscript lockOwned(User user,UUID id){var m=access.requireOwnedManuscript(id,user);worldBindings.lockCurrent(m);return m;}
    WritingAgentStore.Run require(User user,UUID m,UUID scene,UUID id){access.requireOwnedManuscript(m,user);var r=store.run(id);if(!r.userId().equals(user.getId())||!r.manuscriptId().equals(m)||!r.sceneId().equals(scene))throw error("WRITING_AGENT_NOT_FOUND");return r;}
    @Transactional(readOnly=true)
    public List<View> list(User user,UUID m,UUID scene,UUID branch){access.requireOwnedManuscript(m,user);return store.recent(user.getId(),m,scene).stream().filter(r->r.branchId().equals(branch)).map(this::view).toList();}
    @Transactional(readOnly=true)
    public View get(User user,UUID m,UUID scene,UUID id){return view(require(user,m,scene,id));}
    View view(WritingAgentStore.Run run){
        var f=store.read(run.frozen(),Frozen.class);var steps=store.steps(run.id());var views=new ArrayList<StepView>();BigDecimal settled=BigDecimal.ZERO,reserved=BigDecimal.ZERO;long credits=0;
        for(var s:steps){if(s.settledCny()!=null)settled=settled.add(s.settledCny());else reserved=reserved.add(s.reservedCny());if(s.settledCredits()!=null)credits+=s.settledCredits();
            JsonNode result=store.json.createObjectNode();if(s.result()!=null){var raw=store.tree(s.result());if(s.kind().equals("MODEL")){var out=store.read(s.result(),AiChatResponse.class);var node=store.json.createObjectNode();node.put("message",Objects.toString(out.content(),""));node.set("usage",store.json.valueToTree(out.usage()));node.put("transport","BUDGETED_UNARY");var requestRecord=store.read(s.request(),ModelRecord.class);if(requestRecord.request().context()!=null)node.set("sources",requestRecord.request().context().path("writingAgentSources"));if(out.nativeResponseJson()!=null){try{node.set("tools",store.json.valueToTree(out.nativeResult().toolCalls().stream().map(AiNativeResult.ToolCall::name).toList()));}catch(IllegalArgumentException ignored){node.put("error","AI_NATIVE_RESPONSE_INVALID");}}result=node;}else result=raw.path("output");}
            views.add(new StepView(s.sequence(),s.kind(),s.feature(),s.result()!=null?"COMPLETED":Objects.toString(s.settlement(),"PREPARED"),result,s.reservedCny(),s.settledCny(),s.settledCredits()));}
        var values=store.jdbc.query("select metadata_json from narrative_generation_candidates where operation_id=? and source_kind='AGENT' order by generation_sequence",(r,n)->store.read(r.getString(1),Candidate.class),bytes(run.id()));
        return new View(run.id(),run.manuscriptId(),run.sceneId(),run.branchId(),run.status(),run.revision(),run.operationId(),store.tree(run.state()),values,views,settled,reserved,credits,run.maxCny(),run.maxCredits(),run.createdAt(),f.document().sourceHtml(),f.sourceVersion(),f.intent(),f.context().h2(),f.world().binding(),f.world().selectionRevision());
    }
    @Transactional
    public View control(User user,UUID m,UUID scene,UUID id,String action,Revision request,String key){
        key(key);var r=require(user,m,scene,id);store.lock(id);r=store.run(id);String actionHash=hash(action+store.write(request));
        var receipts=(com.fasterxml.jackson.databind.node.ObjectNode)store.tree(r.decision());var old=receipts.path(key);
        if(!old.isMissingNode()){if(!old.path("hash").asText().equals(actionHash))throw error("WRITING_AGENT_IDEMPOTENCY_CONFLICT");return view(r);}
        if(request==null||r.revision()!=request.expectedRevision())throw error("WRITING_AGENT_REVISION_CONFLICT");
        var state=(com.fasterxml.jackson.databind.node.ObjectNode)store.tree(r.state());
        switch(action){
            case "pause"->{if(!Set.of("QUEUED","RUNNING").contains(r.status()))throw error("WRITING_AGENT_STATE_INVALID");store.state(id,"PAUSE_REQUESTED",state);}
            case "cancel"->{if(Set.of("CANCELLED","ACCEPTED").contains(r.status()))throw error("WRITING_AGENT_STATE_INVALID");store.state(id,"CANCELLED",state);UUID operation=r.operationId();if(operation!=null)org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){@Override public void afterCommit(){operations.cancel(user,operation);}});}
            case "resume"->{
                if(!Set.of("PAUSED_USER","WAITING_AUTHOR","RECOVERY_REQUIRED").contains(r.status()))throw error("WRITING_AGENT_STATE_INVALID");
                if(r.status().equals("WAITING_AUTHOR")){if(request.answer()==null||request.answer().isBlank()||request.answer().length()>8000)throw error("WRITING_AGENT_ANSWER_REQUIRED");var answers=state.withObject("authorAnswers");answers.put(state.path("questionKey").asText(),request.answer());}
                store.state(id,"QUEUED",state);var f=store.read(r.frozen(),Frozen.class);try(var ignored=AiSelectionContext.install(f.selections())){var op=operations.submit(user,TYPE,"AGENT_SCENE",scene,Map.of("runId",id),f.limits().modelCalls(),"恢复原自主任务","writing-agent:"+id+":resume:"+r.revision());store.attach(id,op.operationId());}
            }
            default->throw error("WRITING_AGENT_ACTION_INVALID");
        }
        receipts.set(key,store.json.valueToTree(Map.of("hash",actionHash)));store.jdbc.update("update writing_agent_runs set decision_json=? where id=?",store.write(receipts),bytes(id));return view(store.run(id));
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public com.ainovel.app.manuscript.dto.ManuscriptDto accept(User user,UUID mId,UUID scene,UUID id,Decision request,String key){
        key(key);lockOwned(user,mId);var r=require(user,mId,scene,id);store.lock(id);r=store.run(id);var f=store.read(r.frozen(),Frozen.class);
        if("AUTHOR_QA".equals(f.intent())||request==null||request.candidateId()==null)throw error("WRITING_AGENT_DECISION_INVALID");
        var receipt=store.tree(r.decision());String dh=hash(store.write(request));if(receipt.has(key)){if(!receipt.path(key).path("hash").asText().equals(dh))throw error("WRITING_AGENT_IDEMPOTENCY_CONFLICT");return store.json.convertValue(receipt.path(key).path("manuscript"),com.ainovel.app.manuscript.dto.ManuscriptDto.class);}
        if(request.expectedRevision()!=r.revision()||Set.of("RUNNING","QUEUED","PAUSE_REQUESTED").contains(r.status()))throw error("WRITING_AGENT_REVISION_CONFLICT");
        var m=owned(user,mId,request.branchId());
        if(m.getVersion()!=request.expectedVersion()||m.getVersion()!=f.sourceVersion()||!hash(contents.readScene(m,scene)).equals(f.serverHash())||!f.document().sourceHtml().equals(contents.readScene(m,scene)))throw error("WRITING_AGENT_CANDIDATE_STALE");
        if(!generation.planningHash(m).equals(f.planningHash())||!worlds.freeze(m,scene,f.world().isolated()).fingerprint().equals(f.world().fingerprint()))throw error("WRITING_AGENT_CONTEXT_CHANGED");
        if(f.context().h2()&&!narrative.stampCurrent(user,f.context().stamp()))throw error("WRITING_AGENT_CONTEXT_CHANGED");
        var rows=store.jdbc.query("select metadata_json from narrative_generation_candidates where id=? and operation_id=? and source_kind='AGENT'",(rs,n)->store.read(rs.getString(1),Candidate.class),bytes(request.candidateId()),bytes(id));
        if(rows.size()!=1)throw error("WRITING_AGENT_CANDIDATE_MISSING");var c=rows.getFirst();String html=AssistantDocumentProjection.apply(f.document(),c.replacements());if(!hash(html).equals(c.hash()))throw error("WRITING_AGENT_CANDIDATE_INVALID");
        versions.createVersion(m,user,Map.of("snapshotType","agent_before","label","自主创作 · 采纳前"));contents.writeScene(m,scene,html);manuscripts.saveAndFlush(m);
        versions.createVersion(m,user,Map.of("snapshotType","agent_accept","label","自主创作 · 作者采纳"));
        events.publishEvent(new SceneContentEditedEvent(mId,scene,html));events.publishEvent(new NarrativeSourceChanged(mId,null));
        store.jdbc.update("update narrative_generation_candidates set status='APPLIED',adopted_version=? where id=?",m.getVersion(),bytes(c.id()));
        var saved=manuscriptService.get(mId);var next=(com.fasterxml.jackson.databind.node.ObjectNode)receipt;next.set(key,store.json.valueToTree(Map.of("hash",dh,"candidateId",c.id(),"manuscript",saved)));store.decision(id,next);
        return saved;
    }
    AiChatResponse model(User user,UUID runId,int sequence,String key,AiChatRequest request){
        var step=store.step(runId,key);
        if(step==null)step=store.request(runId,sequence,key,"MODEL",request.featureKey(),new ModelRecord(request,request.featureKey()));
        else {if(!step.kind().equals("MODEL")||step.sequence()!=sequence||!step.feature().equals(request.featureKey()))throw error("WRITING_AGENT_REQUEST_CHANGED");request=store.read(step.request(),ModelRecord.class).request();}
        if(step.result()!=null)return store.read(step.result(),AiChatResponse.class);
        var selected=AiSelectionContext.selection(request.featureKey());if(selected==null)throw error("AI_STEP_NOT_QUOTED");
        var usage=new AiUsageContext("WRITING_AGENT",runId.toString(),"step:"+sequence);AiChatResponse response;
        final fireflychat.ai.v1.ChatCompletionsResponse prior;
        try{prior=money.recoverIfPresent(runId,key,gateway.moneyTransport());}
        catch(AiRequestNotSentException notSent){
            if("WRITING_AGENT_CONFIRMED_NOT_SENT".equals(notSent.getMessage()))economy.releaseConfirmedNotSentWritingAgentReservation(user,usage);
            throw notSent;
        }
        if(prior!=null){
            var metadata=AiResponseMetadata.reported(prior.getFinishReason(),prior.hasReasoningTokens()?prior.getReasoningTokens():null,prior.hasUsageAuthoritative()?prior.getUsageAuthoritative():null);
            economy.recordAiResult(user,usage.idempotencyKey(),prior.getContent(),prior.getPromptTokens(),prior.getCompletionTokens(),prior.getCacheTokens(),prior.getModelKey(),metadata,prior.hasNativeResponseJson()?prior.getNativeResponseJson():null);
            var charged=economy.settleAiUsage(user,usage.idempotencyKey(),prior.getContent(),prior.getPromptTokens(),prior.getCompletionTokens(),prior.getCacheTokens());
            response=new AiChatResponse("assistant",prior.getContent(),new AiUsageDto(prior.getPromptTokens(),prior.getCompletionTokens(),prior.getCacheTokens(),0,charged.charged()),economy.currentBalance(user).totalCredits(),selected,prior.getModelKey(),metadata,prior.hasNativeResponseJson()?prior.getNativeResponseJson():null);
        }else try(var budget=AiNativeBudgetContext.open(money.owner(runId,key,selected))){response=ai.chat(user,request,usage);}
        money.retail(runId,key,Math.round(response.usage().cost()));store.result(runId,key,response);return response;
    }
    private static List<AssistantDtos.Source> stripWorld(List<AssistantDtos.Source> sources){return sources.stream().filter(s->!s.type().startsWith("WORLD")).toList();}
    private static void key(String key){if(key==null||key.isBlank()||key.length()>128)throw error("WRITING_AGENT_IDEMPOTENCY_REQUIRED");}
}
