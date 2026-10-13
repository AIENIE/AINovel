package com.ainovel.app.assistant;

import com.ainovel.app.ai.*;
import com.ainovel.app.ai.dto.*;
import com.ainovel.app.aioperation.*;
import com.ainovel.app.common.ApiStatusException;
import com.ainovel.app.manuscript.*;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.manuscript.attribution.SceneContentEditedEvent;
import com.ainovel.app.narrative.*;
import com.ainovel.app.quality.language.*;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.user.User;
import com.ainovel.app.v2.V2VersionPersistenceService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import static com.ainovel.app.assistant.AssistantDtos.*;
import static com.ainovel.app.quality.language.LanguageProjection.hash;

@Service
public class WritingAssistantService {
    public static final String TYPE="WRITING_ASSISTANT_TURN";
    private final AssistantStore store;
    private final ResourceAccessGuard access;
    private final ManuscriptRepository manuscripts;
    private final ManuscriptContentService contents;
    private final ManuscriptService manuscriptService;
    private final NarrativeContextService narrative;
    private final AssistantContext context;
    private final AssistantPrompts prompts;
    private final AiOperationService operations;
    private final AiService ai;
    private final ObjectMapper json;
    private final V2VersionPersistenceService versions;
    private final ApplicationEventPublisher events;
    private final LanguageFeature language;
    public WritingAssistantService(AssistantStore store,ResourceAccessGuard access,ManuscriptRepository manuscripts,
            ManuscriptContentService contents,ManuscriptService manuscriptService,NarrativeContextService narrative,
            AssistantContext context,AssistantPrompts prompts,@Lazy AiOperationService operations,AiService ai,ObjectMapper json,
            V2VersionPersistenceService versions,ApplicationEventPublisher events,LanguageFeature language){
        this.store=store;this.access=access;this.manuscripts=manuscripts;this.contents=contents;this.manuscriptService=manuscriptService;
        this.narrative=narrative;this.context=context;this.prompts=prompts;this.operations=operations;this.ai=ai;this.json=json;
        this.versions=versions;this.events=events;this.language=language;
    }
    public record History(String intent,String instruction,String answer) {}
    public record Frozen(UUID manuscriptId,UUID branchId,UUID sceneId,long sourceVersion,String serverHash,String instruction,
            String intent,UUID parentProposalId,AssistantDocumentProjection.Document document,
            List<AssistantDocumentProjection.Replacement> parentChanges,AssistantContext.Snapshot context,List<History> history,String languageRules,List<String> warnings) {
        public Frozen(UUID manuscriptId,UUID branchId,UUID sceneId,long sourceVersion,String serverHash,String instruction,
                String intent,UUID parentProposalId,AssistantDocumentProjection.Document document,
                List<AssistantDocumentProjection.Replacement> parentChanges,AssistantContext.Snapshot context,List<History> history,String languageRules){
            this(manuscriptId,branchId,sceneId,sourceVersion,serverHash,instruction,intent,parentProposalId,document,parentChanges,context,history,languageRules,List.of());
        }
        public Frozen {warnings=warnings==null?List.of():List.copyOf(warnings);}
    }
    public record Outcome(String intent,String kind,String answer,UUID proposalId,List<Source> sources,List<String> warnings) {}
    public static class ProposalData {
        public String beforeHtml;
        public String afterHtml;
        public List<AssistantDocumentProjection.Replacement> replacements;
        public UUID beforeSnapshotId;
        public UUID afterSnapshotId;
        public UUID undoSnapshotId;
        public Long acceptedVersion;
        public ProposalData(){}
        ProposalData(String before,String after,List<AssistantDocumentProjection.Replacement> changes){beforeHtml=before;afterHtml=after;replacements=changes;}
    }
    public record Receipt(String action,String requestHash,DecisionResult result) {}

    @Transactional(readOnly=true)
    public SessionDto session(User user,UUID manuscriptId,UUID sceneId,UUID branchId,String before,int requestedLimit){
        var m=owned(user,manuscriptId,branchId);int limit=Math.max(1,Math.min(50,requestedLimit));
        var session=store.session(user.getId(),manuscriptId,branchId,sceneId);
        if(session==null)return new SessionDto(null,branchId,sceneId,narrative.enabled(m),List.of(),false,null);
        Instant instant=null;UUID beforeId=null;
        if(before!=null&&!before.isBlank())try{var parts=before.split("\\|",2);instant=Instant.parse(parts[0]);beforeId=UUID.fromString(parts[1]);}catch(Exception e){throw invalid("ASSISTANT_CURSOR_INVALID");}
        var rows=store.turns(session.id(),instant,beforeId,limit+1);boolean more=rows.size()>limit;
        var page=rows.stream().limit(limit).toList();String cursor=more?page.getLast().createdAt()+"|"+page.getLast().id():null;
        return new SessionDto(session.id(),branchId,sceneId,narrative.enabled(m),page.stream().map(t->dto(user,t)).toList(),more,cursor);
    }
    @Transactional
    public Accepted submit(User user,UUID manuscriptId,UUID sceneId,TurnRequest request,String key){
        requireKey(key);if(request==null||request.branchId()==null||request.expectedVersion()==null||request.sourceHtml()==null||request.instruction()==null||request.instruction().isBlank()||request.instruction().length()>8000)throw invalid("ASSISTANT_REQUEST_INVALID");
        String intent=Objects.toString(request.intent(),"AUTO");if(!Set.of("AUTO","EDIT","AUTHOR_QA").contains(intent))throw invalid("ASSISTANT_INTENT_INVALID");
        // One manuscript lock also arbitrates first-session creation and the frozen baseline.
        manuscripts.findByIdForUpdate(manuscriptId).orElseThrow(()->missing());var m=owned(user,manuscriptId,request.branchId());
        var session=store.session(user.getId(),manuscriptId,request.branchId(),sceneId);
        String requestHash=hash(store.write(request));
        if(session!=null){
            store.lockSession(session.id());var replay=store.replay(session.id(),key);
            if(replay!=null){if(!replay.requestHash().equals(requestHash))throw conflict("ASSISTANT_IDEMPOTENCY_CONFLICT");return new Accepted(replay.id(),replay.operationId());}
            if(operations.active(user,"ASSISTANT_SESSION",session.id())!=null)throw conflict("ASSISTANT_TURN_IN_PROGRESS");
        }
        if(m.getVersion()!=request.expectedVersion())throw conflict("MANUSCRIPT_VERSION_CONFLICT");
        String server=contents.readScene(m,sceneId);boolean h2=narrative.enabled(m);
        if(h2&&!server.equals(request.sourceHtml()))throw conflict("ASSISTANT_H2_SAVE_REQUIRED");
        AssistantDocumentProjection.Document document;
        try{document=AssistantDocumentProjection.project(request.sourceHtml(),request.selection());}catch(IllegalArgumentException e){throw invalid(projectionCode(e));}
        List<AssistantDocumentProjection.Replacement> parentChanges=List.of();
        if(request.parentProposalId()!=null){
            var parent=requireProposal(user,manuscriptId,sceneId,request.parentProposalId(),false);var parentTurn=store.turn(parent.turnId());var parentFrozen=frozen(parentTurn);
            if(!parentFrozen.branchId().equals(request.branchId()))throw conflict("MANUSCRIPT_BRANCH_CONFLICT");
            if("READY".equals(parent.status())){
                var data=store.read(parent.dataJson(),ProposalData.class);
                if(!data.beforeHtml.equals(request.sourceHtml()))throw conflict("ASSISTANT_PROPOSAL_STALE");
                if(!sourcesCurrent(user,m,parentFrozen,parentTurn))throw conflict("ASSISTANT_CONTEXT_CHANGED");
                document=parentFrozen.document();parentChanges=data.replacements;
            }else if(!"ACCEPTED".equals(parent.status()))throw conflict("ASSISTANT_PROPOSAL_DECIDED");
        }
        var snapshot=context.freeze(user,m,sceneId);var histories=new ArrayList<History>();var warnings=new ArrayList<String>();
        if(session!=null)for(var t:store.turns(session.id(),null,null,32)){
            if(t.resultJson()==null)continue;var out=store.read(t.resultJson(),Outcome.class);
            if(!out.intent().equals(intent.equals("AUTO")?"EDIT":intent))continue;
            var previous=frozen(t);
            if(!historyAllowed(snapshot,previous,out.intent())){warnings.add("ASSISTANT_HISTORY_OMITTED");continue;}
            histories.add(new History(out.intent(),previous.instruction(),out.answer()));if(histories.size()==8)break;
        }
        Collections.reverse(histories);
        var selection=language.selection(m.getOutline().getStory().getId());
        String rules=selection.generation()?LanguageStandard.text(selection.version()):"";
        var frozen=new Frozen(manuscriptId,request.branchId(),sceneId,m.getVersion(),hash(server),request.instruction(),intent,
                request.parentProposalId(),document,parentChanges,snapshot,List.copyOf(histories),rules,warnings.stream().distinct().toList());
        // Required input is checked before a task or external call is accepted.
        prompts.build(frozen,context.read(snapshot,intent.equals("AUTHOR_QA")?intent:"EDIT",List.of()),false);
        if(session==null)session=store.createSession(user.getId(),manuscriptId,request.branchId(),sceneId);
        UUID turnId=store.createTurn(session.id(),key,requestHash,frozen);
        var accepted=operations.submit(user,TYPE,"ASSISTANT_SESSION",session.id(),Map.of("turnId",turnId),2,"理解请求与准备上下文","assistant-turn:"+turnId);
        store.attach(turnId,accepted.operationId());return new Accepted(turnId,accepted.operationId());
    }
    @Transactional(readOnly=true)
    public TurnDto turn(User user,UUID manuscriptId,UUID sceneId,UUID turnId){return dto(user,requireTurn(user,manuscriptId,sceneId,turnId));}
    @Transactional
    public ProposalDto proposal(User user,UUID manuscriptId,UUID sceneId,UUID proposalId){
        var p=requireProposal(user,manuscriptId,sceneId,proposalId,false);var t=store.turn(p.turnId());var f=frozen(t);var m=access.requireOwnedManuscript(manuscriptId,user);
        var d=store.read(p.dataJson(),ProposalData.class);String stale=null;
        if(!f.branchId().equals(m.getCurrentBranchId()))stale="MANUSCRIPT_BRANCH_CONFLICT";
        else if(p.status().equals("ACCEPTED")){if(!d.afterHtml.equals(contents.readScene(m,sceneId)))stale="ASSISTANT_PROPOSAL_STALE";}
        else if(!hash(d.beforeHtml).equals(hash(contents.readScene(m,sceneId)))&&!f.serverHash().equals(hash(contents.readScene(m,sceneId))))stale="ASSISTANT_PROPOSAL_STALE";
        else if(!sourcesCurrent(user,m,f,t))stale="ASSISTANT_CONTEXT_CHANGED";
        return new ProposalDto(p.id(),t.id(),f.parentProposalId(),p.status(),Set.of("READY","ACCEPTED").contains(p.status())&&stale==null,stale,d.beforeHtml,d.afterHtml,
                f.sourceVersion(),f.branchId(),han(d.beforeHtml),han(d.afterHtml),f.context().minHan(),f.context().maxHan(),f.document().sourceHash());
    }
    @Transactional
    public DecisionResult decide(User user,UUID manuscriptId,UUID sceneId,UUID proposalId,String action,DecisionRequest request,String key){
        requireKey(key);if(request==null||request.expectedVersion()==null||request.branchId()==null||!Set.of("accept","reject","undo").contains(action))throw invalid("ASSISTANT_DECISION_INVALID");
        manuscripts.findByIdForUpdate(manuscriptId).orElseThrow(WritingAssistantService::missing);
        var p=requireProposal(user,manuscriptId,sceneId,proposalId,true);var t=store.turn(p.turnId());var f=frozen(t);
        var receipts=readReceipts(p.receiptsJson());String requestHash=hash(action+":"+store.write(request));
        var replay=receipts.get(key);if(replay!=null){if(!replay.requestHash().equals(requestHash))throw conflict("ASSISTANT_IDEMPOTENCY_CONFLICT");return replay.result();}
        var m=owned(user,manuscriptId,request.branchId());if(!f.branchId().equals(request.branchId()))throw conflict("MANUSCRIPT_BRANCH_CONFLICT");
        if(m.getVersion()!=request.expectedVersion())throw conflict("MANUSCRIPT_VERSION_CONFLICT");
        var d=store.read(p.dataJson(),ProposalData.class);String next;
        if(action.equals("reject")){
            if(!p.status().equals("READY"))throw conflict("ASSISTANT_PROPOSAL_DECIDED");next="REJECTED";
        }else{
            boolean undo=action.equals("undo");
            if(undo?!p.status().equals("ACCEPTED"):!p.status().equals("READY"))throw conflict("ASSISTANT_PROPOSAL_DECIDED");
            String expected=undo?d.afterHtml:d.beforeHtml;
            if(!contents.readScene(m,sceneId).equals(expected))throw conflict("ASSISTANT_PROPOSAL_STALE");
            if(!undo&&!sourcesCurrent(user,m,f,t))throw conflict("ASSISTANT_CONTEXT_CHANGED");
            d.beforeSnapshotId=undo?d.beforeSnapshotId:snapshot(m,user,"assistant_before",proposalId);
            contents.writeScene(m,sceneId,undo?d.beforeHtml:d.afterHtml);manuscripts.saveAndFlush(m);
            UUID saved=snapshot(m,user,undo?"assistant_undo":"assistant_accept",proposalId);
            if(undo)d.undoSnapshotId=saved;else{d.afterSnapshotId=saved;d.acceptedVersion=m.getVersion();}
            events.publishEvent(new SceneContentEditedEvent(manuscriptId,sceneId,undo?d.beforeHtml:d.afterHtml));
            events.publishEvent(new NarrativeSourceChanged(manuscriptId,null));next=undo?"UNDONE":"ACCEPTED";
        }
        var result=new DecisionResult(proposalId,next,manuscriptService.get(manuscriptId));receipts.put(key,new Receipt(action,requestHash,result));
        store.saveProposal(proposalId,next,d,receipts);return result;
    }
    public Object execute(AiOperationExecution execution,UUID turnId){
        var turn=store.turn(turnId);var session=store.session(turn.sessionId());
        if(!session.userId().equals(execution.user().getId())||!execution.operationId().equals(turn.operationId()))throw missing();
        if(turn.resultJson()!=null)return resultView(turnId,store.read(turn.resultJson(),Outcome.class));
        var f=frozen(turn);String initialIntent=f.intent().equals("AUTHOR_QA")?"AUTHOR_QA":"EDIT";
        var initial=context.read(f.context(),initialIntent,List.of());
        var first=call(execution,turnId,"plan",()->prompts.build(f,initial,false));
        JsonNode parsed=parse(first.content());String kind=kind(parsed),intent=intent(f,parsed);
        AssistantContext.Reads reads=initial;
        if(kind.equals("READ_BATCH")){
            execution.progress().step("读取上下文并形成结果",1,2);
            var row=store.turn(turnId);
            if(row.readsJson()==null){var queries=readRequests(parsed);var gathered=context.read(f.context(),intent,queries);AiOperationExecutionContext.apply(()->{store.checkpoint(turnId,"reads_json",gathered);return null;});}
            reads=store.read(store.turn(turnId).readsJson(),AssistantContext.Reads.class);
            var finalReads=reads;var second=call(execution,turnId,"final",()->prompts.build(f,finalReads,true));
            parsed=parse(second.content());kind=kind(parsed);
            if(kind.equals("READ_BATCH")||!intent.equals(intent(f,parsed)))throw invalid("ASSISTANT_RESULT_INVALID");
        }else if(intent.equals("AUTHOR_QA")&&!initialIntent.equals(intent)){
            // An AUTO first step can identify author questions, but it has seen only the edit-safe context.
            reads=new AssistantContext.Reads(intent,initial.sources(),initial.warnings());
        }
        String answer=parsed.path("answer").asText("").strip();if(answer.isBlank())throw invalid("ASSISTANT_RESULT_INVALID");
        if(kind.equals("EDIT_PROPOSAL")&&!intent.equals("EDIT"))throw invalid("ASSISTANT_RESULT_INVALID");
        ProposalData proposal=null;
        if(kind.equals("EDIT_PROPOSAL")){
            try{
                if(!parsed.path("replacements").isArray())throw new IllegalArgumentException();
                var edits=json.convertValue(parsed.path("replacements"),new TypeReference<List<AssistantDocumentProjection.Replacement>>(){});
                var combined=AssistantDocumentProjection.compose(f.document(),f.parentChanges(),edits);
                String after=AssistantDocumentProjection.apply(f.document(),combined);
                if(after.equals(f.document().sourceHtml()))throw new IllegalArgumentException("ASSISTANT_NO_CHANGE");
                proposal=new ProposalData(f.document().sourceHtml(),after,combined);
            }catch(IllegalArgumentException e){throw invalid(projectionCode(e));}
        }
        var proposed=proposal;String outcomeKind=kind.equals("EDIT_PROPOSAL")?"PROPOSAL_READY":kind;
        var finalSources=reads.sources();var warnings=new ArrayList<>(reads.warnings());warnings.addAll(f.warnings());
        var usedTurn=store.turn(turnId);var usedRequest=store.read(usedTurn.finalRequestJson()==null?usedTurn.planRequestJson():usedTurn.finalRequestJson(),AiChatRequest.class);
        long includedHistory=usedRequest.context()==null?0:usedRequest.context().path("assistantHistoryCount").asLong();
        if(includedHistory<f.history().stream().filter(h->h.intent().equals(intent)).count())warnings.add("ASSISTANT_HISTORY_OMITTED");
        if(proposed!=null&&(han(proposed.afterHtml)<f.context().minHan()||han(proposed.afterHtml)>f.context().maxHan()))warnings.add("SCENE_LENGTH_OUT_OF_RANGE");
        return AiOperationExecutionContext.complete(()->{
            UUID proposalId=proposed==null?null:store.createProposal(turnId,proposed);
            var out=new Outcome(intent,outcomeKind,answer,proposalId,finalSources,warnings.stream().distinct().toList());store.finish(turnId,out);
            return resultView(turnId,out);
        });
    }
    private AiChatResponse call(AiOperationExecution execution,UUID turnId,String stage,java.util.function.Supplier<AiChatRequest> builder){
        var row=store.turn(turnId);String raw=stage.equals("plan")?row.planResultJson():row.finalResultJson();
        if(raw!=null)return store.read(raw,AiChatResponse.class);
        String frozen=stage.equals("plan")?row.planRequestJson():row.finalRequestJson();
        if(frozen==null){var request=builder.get();AiOperationExecutionContext.apply(()->{store.checkpoint(turnId,stage+"_request_json",request);return null;});row=store.turn(turnId);frozen=stage.equals("plan")?row.planRequestJson():row.finalRequestJson();}
        var request=store.read(frozen,AiChatRequest.class);
        AiOperationExecutionContext.apply(()->null);
        var response=ai.chatWithIdempotency(execution.user(),request,execution.operationId()+":assistant:"+stage);
        // Even if cancellation wins just after the provider returns, retain the returned text and usage.
        store.checkpoint(turnId,stage+"_result_json",response);return response;
    }
    private Map<String,Object> resultView(UUID turnId,Outcome outcome){var result=new LinkedHashMap<String,Object>();result.put("turnId",turnId);result.put("outcome",outcome.kind());if(outcome.proposalId()!=null)result.put("proposalId",outcome.proposalId());return result;}
    private TurnDto dto(User user,AssistantStore.Turn turn){
        var f=frozen(turn);var out=turn.resultJson()==null?null:store.read(turn.resultJson(),Outcome.class);var usage=Usage.zero();
        for(String raw:new String[]{turn.planResultJson(),turn.finalResultJson()})if(raw!=null){var r=store.read(raw,AiChatResponse.class).usage();usage=new Usage(usage.inputTokens()+r.inputTokens(),usage.outputTokens()+r.outputTokens(),usage.cost()+r.cost(),usage.modelCalls()+1);}
        String status=turn.operationId()==null?"QUEUED":operations.get(user,turn.operationId()).status().name();
        return new TurnDto(turn.id(),turn.operationId(),status,out==null?null:out.kind(),f.instruction(),out==null?null:out.answer(),out==null?null:out.proposalId(),f.parentProposalId(),out==null?List.of():out.sources(),out==null?List.of():out.warnings(),usage,turn.createdAt());
    }
    private Frozen frozen(AssistantStore.Turn turn){return store.read(turn.frozenJson(),Frozen.class);}
    private AssistantStore.Turn requireTurn(User user,UUID manuscript,UUID scene,UUID id){var t=store.turn(id);var s=store.session(t.sessionId());access.requireOwnedManuscript(manuscript,user);if(!s.userId().equals(user.getId())||!s.manuscriptId().equals(manuscript)||!s.sceneId().equals(scene))throw missing();return t;}
    private AssistantStore.Proposal requireProposal(User user,UUID manuscript,UUID scene,UUID id,boolean lock){var p=store.proposal(id,lock);requireTurn(user,manuscript,scene,p.turnId());return p;}
    private Manuscript owned(User user,UUID manuscript,UUID branch){var m=access.requireOwnedManuscript(manuscript,user);if(branch==null||!branch.equals(m.getCurrentBranchId()))throw conflict("MANUSCRIPT_BRANCH_CONFLICT");return m;}
    private boolean sourcesCurrent(User user,Manuscript m,Frozen f,AssistantStore.Turn turn){var out=turn.resultJson()==null?null:store.read(turn.resultJson(),Outcome.class);return context.sourcesCurrent(user,m,f.sceneId(),f.context(),out==null?f.context().base():out.sources());}
    private UUID snapshot(Manuscript m,User user,String type,UUID proposal){return (UUID)versions.createVersion(m,user,Map.of("snapshotType",type,"label","AI 助手 · "+type,"metadata",Map.of("assistantProposalId",proposal))).get("id");}
    private Map<String,Receipt> readReceipts(String raw){try{return json.readValue(raw,new TypeReference<LinkedHashMap<String,Receipt>>(){});}catch(Exception e){throw new IllegalStateException(e);}}
    private JsonNode parse(String raw){try{String value=raw.strip();if(value.startsWith("```")){int start=value.indexOf('\n'),end=value.lastIndexOf("```");value=value.substring(start+1,end).strip();}var node=json.readTree(value);if(!node.isObject())throw new IllegalArgumentException();return node;}catch(Exception e){throw invalid("ASSISTANT_RESULT_INVALID");}}
    private String kind(JsonNode node){String kind=node.path("kind").asText();if(!Set.of("ANSWER","NEEDS_INPUT","EDIT_PROPOSAL","READ_BATCH").contains(kind))throw invalid("ASSISTANT_RESULT_INVALID");return kind;}
    private String intent(Frozen f,JsonNode node){String value=node.path("intent").asText();if(!Set.of("EDIT","AUTHOR_QA").contains(value)||!f.intent().equals("AUTO")&&!f.intent().equals(value))throw invalid("ASSISTANT_RESULT_INVALID");return value;}
    private List<ReadRequest> readRequests(JsonNode node){try{if(!node.path("reads").isArray()||node.path("reads").size()>4)throw new IllegalArgumentException();return json.convertValue(node.path("reads"),new TypeReference<List<ReadRequest>>(){});}catch(Exception e){throw invalid("ASSISTANT_TOOL_REQUEST_INVALID");}}
    public static int han(String html){return (int)AssistantContext.plain(html).codePoints().filter(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN).count();}
    static boolean historyAllowed(AssistantContext.Snapshot current,Frozen previous,String purpose){
        return !current.h2()||!purpose.equals("EDIT")||previous.context().h2()&&Objects.equals(current.stamp(),previous.context().stamp());
    }
    private static void requireKey(String key){if(key==null||!key.matches("[A-Za-z0-9:_-]{8,128}"))throw invalid("INVALID_IDEMPOTENCY_KEY");}
    private static String projectionCode(IllegalArgumentException e){return e.getMessage()!=null&&e.getMessage().matches("ASSISTANT_[A-Z_]+")?e.getMessage():"ASSISTANT_RESULT_INVALID";}
    private static ApiStatusException invalid(String code){return new ApiStatusException(HttpStatus.UNPROCESSABLE_ENTITY,code);}
    private static ApiStatusException conflict(String code){return new ApiStatusException(HttpStatus.CONFLICT,code);}
    private static ApiStatusException missing(){return new ApiStatusException(HttpStatus.NOT_FOUND,"ASSISTANT_NOT_FOUND");}
}
