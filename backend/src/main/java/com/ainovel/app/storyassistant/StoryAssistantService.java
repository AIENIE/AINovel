package com.ainovel.app.storyassistant;

import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.dto.*;
import com.ainovel.app.aioperation.*;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import static com.ainovel.app.quality.language.LanguageProjection.hash;
import static com.ainovel.app.storyassistant.StoryAssistantDtos.*;
import static com.ainovel.app.storyassistant.StoryAssistantErrors.*;

@Service
public class StoryAssistantService {
    public static final String TYPE="STORY_ASSISTANT_TURN";
    private final StoryAssistantStore store;
    private final StoryAssistantResources resources;
    private final StoryAssistantContext context;
    private final AiOperationService operations;
    private final AiService ai;
    private final ObjectMapper json;
    public StoryAssistantService(StoryAssistantStore store,StoryAssistantResources resources,StoryAssistantContext context,
            @Lazy AiOperationService operations,AiService ai,ObjectMapper json){
        this.store=store;this.resources=resources;this.context=context;this.operations=operations;this.ai=ai;this.json=json;
    }
    public record History(UUID turnId,String instruction,String answer) {}
    public record Frozen(UUID storyId,UUID sessionId,String intent,String instruction,UUID outlineId,
            UUID parentProposalId,ProposalData parent,StoryAssistantContext.Snapshot snapshot,List<History> history) {}
    public record Outcome(String kind,String answer,List<Suggestion> suggestions,List<UUID> proposalIds,List<Source> sources,List<String> warnings) {}
    public static class ProposalData {
        public String action;
        public String resourceType;
        public UUID targetId;
        public UUID outlineId;
        public UUID parentId;
        public UUID parentProposalId;
        public JsonNode before;
        public JsonNode candidate;
        public JsonNode modelCandidate;
        public JsonNode acceptedCandidate;
        public String rationale;
        public List<Source> sources;
        public List<UUID> sourceTurnIds;
        public ProposalData() {}
    }
    @Transactional(readOnly=true)
    public List<SessionSummary> sessions(User user,UUID storyId){resources.story(user,storyId,false);return store.sessions(storyId,user.getId()).stream().map(this::summary).toList();}
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public SessionSummary createSession(User user,UUID storyId,SessionRequest request,String key){
        key(key);resources.story(user,storyId,true);String title=title(request);String requestHash=hash(store.write(request));
        var old=replay(storyId,"sessions",key,requestHash,SessionSummary.class);if(old!=null)return old;
        var result=summary(store.createSession(storyId,user.getId(),title));store.receipt(storyId,"sessions",key,requestHash,result);return result;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public SessionSummary renameSession(User user,UUID storyId,UUID sessionId,SessionRequest request,String key){
        key(key);ownedSession(user,storyId,sessionId,true);String name=title(request);String requestHash=hash(store.write(request));String scope="session:"+sessionId;
        var old=replay(storyId,scope,key,requestHash,SessionSummary.class);if(old!=null)return old;
        store.rename(sessionId,name);var result=summary(store.session(sessionId,false));store.receipt(storyId,scope,key,requestHash,result);return result;
    }
    @Transactional(readOnly=true)
    public SessionDto session(User user,UUID storyId,UUID sessionId,String before,int requestedLimit){
        resources.story(user,storyId,false);
        if(sessionId==null){var all=store.sessions(storyId,user.getId());if(all.isEmpty())return new SessionDto(null,storyId,null,List.of(),false,null);sessionId=all.getFirst().id();}
        var s=ownedSession(user,storyId,sessionId,false);int limit=Math.max(1,Math.min(50,requestedLimit));Instant time=null;UUID beforeId=null;
        if(before!=null&&!before.isBlank())try{var parts=before.split("\\|",2);time=Instant.parse(parts[0]);beforeId=UUID.fromString(parts[1]);}catch(Exception e){throw invalid("STORY_ASSISTANT_CURSOR_INVALID");}
        var rows=store.turns(s.id(),time,beforeId,limit+1);boolean more=rows.size()>limit;var page=rows.stream().limit(limit).toList();
        return new SessionDto(s.id(),storyId,s.title(),page.stream().map(t->dto(user,t)).toList(),more,more?page.getLast().createdAt()+"|"+page.getLast().id():null);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Accepted submit(User user,UUID storyId,TurnRequest request,String key){
        key(key);if(request==null||request.sessionId()==null||request.intent()==null||!Set.of("DISCUSS","PROPOSE","REFINE").contains(request.intent())||request.instruction()==null||request.instruction().isBlank()||request.instruction().length()>8000)throw invalid("STORY_ASSISTANT_REQUEST_INVALID");
        var session=ownedSession(user,storyId,request.sessionId(),true);String requestHash=hash(store.write(request));var replay=store.replay(session.id(),key);
        if(replay!=null){if(!replay.requestHash().equals(requestHash))throw conflict("STORY_ASSISTANT_IDEMPOTENCY_CONFLICT");return new Accepted(replay.id(),replay.operationId());}
        if(operations.active(user,"STORY_ASSISTANT_SESSION",session.id())!=null)throw conflict("STORY_ASSISTANT_TURN_IN_PROGRESS");
        if(request.intent().equals("DISCUSS")&&(request.suggestionId()!=null||request.parentProposalId()!=null))throw invalid("STORY_ASSISTANT_REQUEST_INVALID");
        ProposalData parent=null;UUID outlineId=request.outlineId();
        if(request.intent().equals("REFINE")){
            if(request.parentProposalId()==null)throw invalid("STORY_ASSISTANT_PARENT_REQUIRED");
            var p=requireProposal(user,storyId,request.parentProposalId(),false);var t=store.turn(p.turnId());
            if(!t.sessionId().equals(session.id()))throw missing();if(!p.status().equals("READY"))throw conflict("STORY_PROPOSAL_DECIDED");
            parent=data(p);if(!detail(user,storyId,p).applicable())throw conflict("STORY_PROPOSAL_STALE");
            outlineId=parent.outlineId;
        }else if(request.parentProposalId()!=null)throw invalid("STORY_ASSISTANT_REQUEST_INVALID");
        List<UUID> sourceIds=request.sourceTurnIds()==null?List.of():request.sourceTurnIds();if(sourceIds.size()>8||new HashSet<>(sourceIds).size()!=sourceIds.size())throw invalid("STORY_ASSISTANT_SOURCES_INVALID");
        var histories=new ArrayList<History>();
        if(!sourceIds.isEmpty())for(UUID id:sourceIds){var t=requireTurn(user,storyId,id);if(!t.sessionId().equals(session.id())||t.resultJson()==null)throw invalid("STORY_ASSISTANT_SOURCES_INVALID");histories.add(history(t));}
        else for(var t:store.turns(session.id(),null,null,32)){if(t.resultJson()!=null){histories.add(history(t));if(histories.size()==8)break;}};
        if(sourceIds.isEmpty())Collections.reverse(histories);
        if(request.suggestionId()!=null){
            boolean found=false;for(var h:histories){var out=outcome(store.turn(h.turnId()));if(out.suggestions().stream().anyMatch(s->s.id().equals(request.suggestionId())))found=true;}
            if(!found)throw invalid("STORY_ASSISTANT_SUGGESTION_MISSING");
        }
        var frozen=new Frozen(storyId,session.id(),request.intent(),request.instruction(),outlineId,request.parentProposalId(),parent,
                context.freeze(user,storyId,outlineId),List.copyOf(histories));
        context.prompt(frozen,context.read(frozen.snapshot(),frozen.instruction(),List.of()),false);
        UUID turn=store.createTurn(session.id(),key,requestHash,frozen);
        var accepted=operations.submit(user,TYPE,"STORY_ASSISTANT_SESSION",session.id(),Map.of("turnId",turn),2,"理解讨论与准备资料","story-assistant:"+turn);
        store.attach(turn,accepted.operationId());store.touch(session.id());return new Accepted(turn,accepted.operationId());
    }
    @Transactional(readOnly=true)
    public TurnDto turn(User user,UUID storyId,UUID id){return dto(user,requireTurn(user,storyId,id));}
    @Transactional(readOnly=true)
    public ProposalDto proposal(User user,UUID storyId,UUID id){return detail(user,storyId,requireProposal(user,storyId,id,false));}
    @Transactional
    public ProposalDto edit(User user,UUID storyId,UUID id,EditRequest request,String key){
        key(key);var p=requireProposal(user,storyId,id,true);String hash=hash("edit:"+store.write(request));String scope="proposal:"+id;
        var replay=replay(storyId,scope,key,hash,ProposalDto.class);if(replay!=null)return replay;
        if(request==null||request.expectedRevision()==null||p.revision()!=request.expectedRevision())throw conflict("STORY_PROPOSAL_VERSION_CONFLICT");
        if(!p.status().equals("READY"))throw conflict("STORY_PROPOSAL_DECIDED");var d=data(p);
        d.candidate=resources.normalize(d.resourceType,d.action,request.candidate(),d.candidate,d.action.equals("CREATE")?d.candidate:null);
        store.saveProposal(id,p.status(),d);var result=detail(user,storyId,store.proposal(id,false));store.receipt(storyId,scope,key,hash,result);return result;
    }
    @Transactional
    public DecisionResult decide(User user,UUID storyId,UUID id,String action,DecisionRequest request,String key){
        key(key);if(!Set.of("accept","reject","undo").contains(action))throw invalid("STORY_ASSISTANT_DECISION_INVALID");
        var p=requireProposal(user,storyId,id,true);String requestHash=hash(action+":"+store.write(request));String scope="proposal:"+id;
        var replay=replay(storyId,scope,key,requestHash,DecisionResult.class);if(replay!=null)return replay;
        if(request==null||request.expectedRevision()==null||p.revision()!=request.expectedRevision())throw conflict("STORY_PROPOSAL_VERSION_CONFLICT");
        var d=data(p);String next;
        if(action.equals("reject")){
            if(!p.status().equals("READY"))throw conflict("STORY_PROPOSAL_DECIDED");next="REJECTED";
        }else{
            boolean undo=action.equals("undo");
            if(undo&&d.action.equals("CREATE"))throw conflict("STORY_CREATE_UNDO_UNSUPPORTED");
            if(!(undo?"ACCEPTED":"READY").equals(p.status()))throw conflict("STORY_PROPOSAL_DECIDED");
            if(!undo&&d.action.equals("UPDATE")&&d.candidate.equals(d.before))throw invalid("STORY_PROPOSAL_NO_CHANGE");
            resources.apply(user,storyId,d,undo?d.before:d.candidate,undo,request.expectedResourceRevision());
            if(!undo)d.acceptedCandidate=d.candidate.deepCopy();next=undo?"UNDONE":"ACCEPTED";
        }
        store.saveProposal(id,next,d);var proposal=detail(user,storyId,store.proposal(id,false));var result=new DecisionResult(proposal,proposal.resourceRevision());
        store.receipt(storyId,scope,key,requestHash,result);return result;
    }
    public Object execute(AiOperationExecution execution,UUID turnId){
        var turn=store.turn(turnId);var session=store.session(turn.sessionId(),false);
        if(!session.userId().equals(execution.user().getId())||!execution.operationId().equals(turn.operationId()))throw missing();
        if(turn.resultJson()!=null)return result(turnId,outcome(turn));
        var f=frozen(turn);var reads=context.read(f.snapshot(),f.instruction(),List.of());var firstReads=reads;
        var response=call(execution,turnId,"plan",context.prompt(f,reads,false));JsonNode parsed=parse(response.content());
        if(parsed.path("kind").asText().equals("READ_BATCH")){
            execution.progress().step("读取冻结资料并形成结果",1,2);
            var row=store.turn(turnId);
            if(row.readsJson()==null){
                if(!parsed.path("reads").isArray())throw invalid("STORY_ASSISTANT_READ_INVALID");
                List<ReadRequest> requests=json.convertValue(parsed.path("reads"),new TypeReference<>(){});
                var gathered=context.read(f.snapshot(),f.instruction(),requests);AiOperationExecutionContext.apply(()->{store.checkpoint(turnId,"reads_json",gathered);return null;});
            }
            reads=store.read(store.turn(turnId).readsJson(),StoryAssistantContext.Reads.class);
            response=call(execution,turnId,"final",context.prompt(f,reads,true));parsed=parse(response.content());
        }
        String kind=parsed.path("kind").asText();String answer=parsed.path("answer").asText("").strip();
        if(!Set.of("ANSWER","NEEDS_INPUT","PROPOSALS").contains(kind)||answer.isBlank())throw invalid("STORY_ASSISTANT_RESULT_INVALID");
        if(f.intent().equals("DISCUSS")&&kind.equals("PROPOSALS"))throw invalid("STORY_ASSISTANT_RESULT_INVALID");
        // The final request can replace background within its budget. The receipt still shows everything
        // actually exposed in either request; UPDATE validation below uses the final request only.
        var exposed=new LinkedHashMap<String,Source>();firstReads.sources().forEach(s->exposed.put(s.id(),s));reads.sources().forEach(s->exposed.put(s.id(),s));
        var sources=new ArrayList<>(exposed.values());sources.add(StoryAssistantContext.source("turn:"+turnId,"AUTHOR_REQUEST","本次作者请求",f.instruction()));
        if(f.parent()!=null)sources.add(context.parentSource(f));
        for(var h:f.history())sources.add(StoryAssistantContext.source("turn:"+h.turnId(),"DISCUSSION","讨论依据",h.instruction()+"\n"+h.answer()));
        var sourceTurns=new ArrayList<>(f.history().stream().map(History::turnId).toList());sourceTurns.add(turnId);
        var suggestions=new ArrayList<Suggestion>();
        if(f.intent().equals("DISCUSS")&&parsed.has("suggestions")){
            if(!parsed.path("suggestions").isArray()||parsed.path("suggestions").size()>4)throw invalid("STORY_ASSISTANT_RESULT_INVALID");
            var suggestionSources=sourceTurns.subList(Math.max(0,sourceTurns.size()-8),sourceTurns.size());
            for(var s:parsed.path("suggestions")){String title=s.path("title").asText("").strip(),reason=s.path("reason").asText("").strip();if(title.isBlank()||title.length()>120||reason.isBlank())throw invalid("STORY_ASSISTANT_RESULT_INVALID");suggestions.add(new Suggestion(UUID.randomUUID(),title,reason,List.copyOf(suggestionSources)));}
        }
        var candidates=new ArrayList<ProposalData>();
        if(kind.equals("PROPOSALS")){
            if(!parsed.path("items").isArray()||parsed.path("items").isEmpty()||parsed.path("items").size()>12||f.parent()!=null&&parsed.path("items").size()!=1)throw invalid("STORY_ASSISTANT_RESULT_INVALID");
            var targets=new HashSet<String>();for(var item:parsed.path("items")){
                var d=prepare(f,item,sources,sourceTurns,reads.sources());if(!targets.add(d.resourceType+":"+d.targetId))throw invalid("STORY_PROPOSAL_DUPLICATE_TARGET");candidates.add(d);
            }
        }
        var finalSources=List.copyOf(sources);var allWarnings=new ArrayList<String>();
        for(String warning:firstReads.warnings())if(!warning.startsWith("STORY_UNREAD_RESOURCES:")&&(!warning.startsWith("STORY_CONTEXT_OMITTED:")||!exposed.containsKey(warning.substring("STORY_CONTEXT_OMITTED:".length()))))allWarnings.add(warning);
        for(String warning:reads.warnings())if(!warning.startsWith("STORY_UNREAD_RESOURCES:"))allWarnings.add(warning);
        long unread=f.snapshot().pool().stream().filter(s->!exposed.containsKey(s.id())).count();if(unread>0)allWarnings.add("STORY_UNREAD_RESOURCES:"+unread);
        var warnings=allWarnings.stream().distinct().toList();
        return AiOperationExecutionContext.complete(()->{
            var ids=new ArrayList<UUID>();for(int i=0;i<candidates.size();i++)ids.add(store.createProposal(turnId,i,candidates.get(i)));
            var out=new Outcome(kind,answer,List.copyOf(suggestions),List.copyOf(ids),finalSources,warnings);store.finish(turnId,out);store.touch(session.id());return result(turnId,out);
        });
    }
    private ProposalData prepare(Frozen f,JsonNode item,List<Source> sources,List<UUID> sourceTurns,List<Source> finalSources){
        String action=item.path("action").asText(),type=item.path("resourceType").asText();
        if(!Set.of("CREATE","UPDATE").contains(action)||!Set.of("CHARACTER","CHAPTER","SCENE").contains(type))throw invalid("STORY_ASSISTANT_RESULT_INVALID");
        if(!type.equals("CHARACTER")&&f.outlineId()==null)throw invalid("STORY_OUTLINE_REQUIRED");
        var d=new ProposalData();d.action=action;d.resourceType=type;d.outlineId=type.equals("CHARACTER")?null:f.outlineId();d.parentProposalId=f.parentProposalId();
        d.targetId=action.equals("CREATE")?f.parent()==null?UUID.randomUUID():f.parent().targetId:StoryAssistantResources.id(item.path("targetId"));
        d.parentId=type.equals("SCENE")&&item.hasNonNull("parentId")?StoryAssistantResources.id(item.path("parentId")):null;
        if(action.equals("UPDATE")){
            var target=f.snapshot().resources().stream().filter(r->r.type().equals(type)&&r.id().equals(d.targetId)&&Objects.equals(r.outlineId(),d.outlineId)).findFirst().orElseThrow(()->invalid("STORY_RESOURCE_NOT_IN_CONTEXT"));
            d.parentId=target.parentId();d.before=target.fields();
            if(f.parent()==null&&finalSources.stream().noneMatch(s->s.id().equals(type.toLowerCase(Locale.ROOT)+":"+d.targetId)&&!s.truncated()))throw invalid("STORY_TARGET_NOT_READ");
        }else if(type.equals("SCENE")&&(d.parentId==null||f.snapshot().resources().stream().noneMatch(r->r.type().equals("CHAPTER")&&r.id().equals(d.parentId)&&Objects.equals(r.outlineId(),d.outlineId))))throw invalid("STORY_PARENT_CHANGED");
        if(f.parent()!=null){
            var parent=f.parent();if(!action.equals(parent.action)||!type.equals(parent.resourceType)||!d.targetId.equals(parent.targetId)||!Objects.equals(d.parentId,parent.parentId))throw invalid("STORY_CANDIDATE_STRUCTURE_CHANGED");
            d.before=parent.before;
        }
        JsonNode basis=f.parent()==null?d.before:f.parent().candidate;
        d.candidate=resources.normalize(type,action,item.path("fields"),basis,f.parent()==null?null:f.parent().candidate);
        if(action.equals("UPDATE")&&d.candidate.equals(d.before))throw invalid("STORY_PROPOSAL_NO_CHANGE");
        d.modelCandidate=d.candidate.deepCopy();d.rationale=item.path("rationale").asText("").strip();if(d.rationale.isBlank())throw invalid("STORY_ASSISTANT_RESULT_INVALID");
        d.sources=List.copyOf(sources);d.sourceTurnIds=List.copyOf(sourceTurns);return d;
    }
    private AiChatResponse call(AiOperationExecution execution,UUID turnId,String stage,AiChatRequest request){
        var row=store.turn(turnId);String raw=stage.equals("plan")?row.planResultJson():row.finalResultJson();if(raw!=null)return store.read(raw,AiChatResponse.class);
        String saved=stage.equals("plan")?row.planRequestJson():row.finalRequestJson();
        if(saved==null){AiOperationExecutionContext.apply(()->{store.checkpoint(turnId,stage+"_request_json",request);return null;});row=store.turn(turnId);saved=stage.equals("plan")?row.planRequestJson():row.finalRequestJson();}
        var frozen=store.read(saved,AiChatRequest.class);AiOperationExecutionContext.apply(()->null);
        var response=ai.chatWithIdempotency(execution.user(),frozen,execution.operationId()+":story-assistant:"+stage);store.checkpoint(turnId,stage+"_result_json",response);return response;
    }
    private ProposalDto detail(User user,UUID storyId,StoryAssistantStore.Proposal p){
        var d=data(p);var current=resources.state(user,storyId,d,false);String reason=current.reason();
        if(p.status().equals("READY")&&reason==null&&d.action.equals("UPDATE")&&!Objects.equals(d.before,current.current()))reason="STORY_PROPOSAL_STALE";
        boolean undo=p.status().equals("ACCEPTED")&&d.action.equals("UPDATE")&&reason==null&&Objects.equals(d.acceptedCandidate,current.current());
        if(p.status().equals("ACCEPTED")&&d.action.equals("CREATE"))reason=null;
        if(p.status().equals("ACCEPTED")&&d.action.equals("UPDATE")&&!undo&&reason==null)reason="STORY_PROPOSAL_STALE";
        return new ProposalDto(p.id(),p.turnId(),d.parentProposalId,d.action,d.resourceType,d.targetId,d.outlineId,d.parentId,p.status(),p.revision(),current.revision(),d.before,d.candidate,d.modelCandidate,current.current(),d.rationale,d.sources,d.sourceTurnIds,p.status().equals("READY")&&reason==null,reason,undo,p.createdAt());
    }
    private TurnDto dto(User user,StoryAssistantStore.Turn t){
        var f=frozen(t);var out=t.resultJson()==null?null:outcome(t);double input=0,output=0,cost=0;int calls=0;
        for(String raw:new String[]{t.planResultJson(),t.finalResultJson()})if(raw!=null){var usage=store.read(raw,AiChatResponse.class).usage();calls++;if(usage!=null){input+=usage.inputTokens();output+=usage.outputTokens();cost+=usage.cost();}}
        String status=t.operationId()==null?"QUEUED":operations.get(user,t.operationId()).status().name();
        return new TurnDto(t.id(),t.operationId(),status,out==null?null:out.kind(),f.instruction(),out==null?null:out.answer(),f.intent(),f.outlineId(),out==null?List.of():out.suggestions(),out==null?List.of():out.proposalIds(),f.parentProposalId(),out==null?List.of():out.sources(),out==null?List.of():out.warnings(),new Usage(input,output,cost,calls),t.createdAt());
    }
    private StoryAssistantStore.Session ownedSession(User user,UUID storyId,UUID id,boolean lock){resources.story(user,storyId,false);var s=store.session(id,lock);if(!s.storyId().equals(storyId)||!s.userId().equals(user.getId()))throw missing();return s;}
    private StoryAssistantStore.Turn requireTurn(User user,UUID story,UUID id){var t=store.turn(id);ownedSession(user,story,t.sessionId(),false);return t;}
    private StoryAssistantStore.Proposal requireProposal(User user,UUID story,UUID id,boolean lock){var p=store.proposal(id,lock);requireTurn(user,story,p.turnId());return p;}
    private ProposalData data(StoryAssistantStore.Proposal p){return store.read(p.dataJson(),ProposalData.class);}
    private Frozen frozen(StoryAssistantStore.Turn t){return store.read(t.frozenJson(),Frozen.class);}
    private Outcome outcome(StoryAssistantStore.Turn t){return store.read(t.resultJson(),Outcome.class);}
    private History history(StoryAssistantStore.Turn t){return new History(t.id(),frozen(t).instruction(),outcome(t).answer());}
    private SessionSummary summary(StoryAssistantStore.Session s){return new SessionSummary(s.id(),s.storyId(),s.title(),s.createdAt(),s.updatedAt());}
    private String title(SessionRequest r){if(r==null||r.title()==null||r.title().isBlank()||r.title().strip().length()>120)throw invalid("STORY_ASSISTANT_TITLE_INVALID");return r.title().strip();}
    private <T>T replay(UUID story,String scope,String key,String requestHash,Class<T> type){var old=store.receipt(story,scope,key);if(old==null)return null;if(!old.requestHash().equals(requestHash))throw conflict("STORY_ASSISTANT_IDEMPOTENCY_CONFLICT");return store.read(old.responseJson(),type);}
    private JsonNode parse(String value){try{JsonNode node=json.readTree(value);if(!node.isObject())throw new IllegalArgumentException();return node;}catch(Exception e){throw invalid("STORY_ASSISTANT_RESULT_INVALID");}}
    private Map<String,Object> result(UUID turn,Outcome out){return Map.of("turnId",turn,"outcome",out.kind(),"proposalIds",out.proposalIds());}
}
