package com.ainovel.app.worldassistant;

import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.dto.*;
import com.ainovel.app.aioperation.*;
import com.ainovel.app.user.User;
import com.ainovel.app.world.WorldVersionService;
import com.ainovel.app.settings.dto.WorldPromptTemplatesResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;
import static com.ainovel.app.worldassistant.WorldAssistantDtos.*;
import static com.ainovel.app.worldassistant.WorldAssistantErrors.*;
import static com.ainovel.app.quality.language.LanguageProjection.hash;

@Service
public class WorldAssistantService {
    public static final String TYPE="WORLD_ASSISTANT_TURN";
    private final WorldAssistantStore store;
    private final WorldAssistantResources resources;
    private final WorldAssistantContext context;
    private final AiOperationService operations;
    private final AiService ai;
    private final ObjectMapper json;
    public WorldAssistantService(WorldAssistantStore store,WorldAssistantResources resources,WorldAssistantContext context,
            @Lazy AiOperationService operations,AiService ai,ObjectMapper json) {
        this.store=store;this.resources=resources;this.context=context;this.operations=operations;this.ai=ai;this.json=json;
    }
    public record History(UUID turnId,String instruction,String answer) {}
    public record Frozen(UUID worldId,UUID sessionId,String intent,String instruction,UUID parentProposalId,ProposalData parent,
            UUID targetId,WorldAssistantContext.Snapshot snapshot,List<History> history,WorldPromptTemplatesResponse templates,String singleFeature) {}
    public record Outcome(String kind,String answer,List<Suggestion> suggestions,List<UUID> proposalIds,List<Source> sources,List<String> warnings,List<SemanticConflict> conflicts) {}
    public static class ProposalData {
        public String action,resourceType,fieldKey,worldHash,rationale;
        public UUID targetId,parentProposalId;
        public JsonNode before,candidate,modelCandidate;
        public List<Source> sources;
        public List<UUID> sourceTurnIds;
        public List<SemanticConflict> conflicts;
        public ProposalData() {}
    }
    @Transactional(readOnly=true)
    public List<SessionSummary> sessions(User user,UUID worldId) {resources.world(user,worldId,false);return store.sessions(worldId,user.getId()).stream().map(this::summary).toList();}
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public SessionSummary createSession(User user,UUID worldId,SessionRequest request,String key) {
        key(key);resources.world(user,worldId,true);String requestHash=hash(store.write(request));
        var prior=replay(worldId,"sessions",key,requestHash,SessionSummary.class);if(prior!=null)return prior;
        var result=summary(store.createSession(worldId,user.getId(),title(request)));store.receipt(worldId,"sessions",key,requestHash,result);return result;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public SessionSummary renameSession(User user,UUID worldId,UUID sessionId,SessionRequest request,String key) {
        key(key);resources.world(user,worldId,true);ownedSession(user,worldId,sessionId,true);String requestHash=hash(store.write(request)),scope="session:"+sessionId;
        var prior=replay(worldId,scope,key,requestHash,SessionSummary.class);if(prior!=null)return prior;
        store.rename(sessionId,title(request));var result=summary(store.session(sessionId,false));store.receipt(worldId,scope,key,requestHash,result);return result;
    }
    @Transactional(readOnly=true)
    public SessionDto session(User user,UUID worldId,UUID sessionId,String before,int requestedLimit) {
        resources.world(user,worldId,false);
        if(sessionId==null){var all=store.sessions(worldId,user.getId());if(all.isEmpty())return new SessionDto(null,worldId,null,List.of(),false,null);sessionId=all.getFirst().id();}
        var session=ownedSession(user,worldId,sessionId,false);var cursor=cursor(before);int limit=Math.max(1,Math.min(50,requestedLimit));
        var rows=store.turns(session.id(),cursor.time(),cursor.id(),limit+1);boolean more=rows.size()>limit;var page=rows.stream().limit(limit).toList();
        return new SessionDto(session.id(),worldId,session.title(),page.stream().map(t->dto(user,t)).toList(),more,more?page.getLast().createdAt()+"|"+page.getLast().id():null);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Accepted submit(User user,UUID worldId,TurnRequest request,String key) {
        key(key);resources.world(user,worldId,true);validateRequest(request);ownedSession(user,worldId,request.sessionId(),true);
        String requestHash=hash(store.write(request));var old=store.replay(request.sessionId(),key);
        if(old!=null){if(!old.requestHash().equals(requestHash))throw conflict("WORLD_ASSISTANT_IDEMPOTENCY_CONFLICT");return new Accepted(old.id(),old.operationId(),old.sessionId());}
        return start(user,quoted(user,worldId,()->freeze(user,worldId,request)),key,requestHash);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Accepted single(User user,UUID worldId,UUID entryId,SingleRequest request,String key) {
        key(key);resources.world(user,worldId,true);String scope=entryId==null?"overview":"entry:"+entryId,requestHash=hash(store.write(request));
        var prior=replay(worldId,scope,key,requestHash,Accepted.class);if(prior!=null)return prior;
        var frozen=quoted(user,worldId,()->freezeSingle(user,worldId,entryId,request));context.prompt(frozen,context.read(frozen,List.of()),true);
        String title=entryId==null?"世界概览整合":"条目精修";
        var session=store.createSession(worldId,user.getId(),title);
        frozen=new Frozen(worldId,session.id(),frozen.intent(),frozen.instruction(),null,null,entryId,frozen.snapshot(),List.of(),frozen.templates(),frozen.singleFeature());
        var result=start(user,frozen,key,requestHash);store.receipt(worldId,scope,key,requestHash,result);return result;
    }
    /** Quote validation is read-only and uses exactly the same frozen material and template renderer. */
    @Transactional(readOnly=true)
    public Frozen validateQuote(User user,UUID worldId,UUID entryId,JsonNode body,boolean single) {
        Frozen frozen=single?freezeSingle(user,worldId,entryId,json.convertValue(body,SingleRequest.class)):freeze(user,worldId,json.convertValue(body,TurnRequest.class));
        context.prompt(frozen,context.read(frozen,List.of()),single);return frozen;
    }
    private Frozen quoted(User user,UUID world,java.util.function.Supplier<Frozen> fallback) {
        var selection=com.ainovel.app.modelconfig.AiSelectionContext.current();
        if(selection==null||selection.worldAssistantInput()==null)return fallback.get();
        var frozen=selection.worldAssistantInput();if(!world.equals(frozen.worldId()))throw conflict("AI_QUOTE_INPUT_CHANGED");
        WorldVersionService.checkRevision(resources.world(user,world,false),frozen.snapshot().revision());return frozen;
    }
    private Accepted start(User user,Frozen frozen,String key,String requestHash) {
        if(operations.active(user,"WORLD_ASSISTANT_SESSION",frozen.sessionId())!=null)throw conflict("WORLD_ASSISTANT_TURN_IN_PROGRESS");
        context.prompt(frozen,context.read(frozen,List.of()),frozen.singleFeature()!=null);
        UUID turn=store.createTurn(frozen.sessionId(),key,requestHash,frozen);
        var result=operations.submit(user,TYPE,"WORLD_ASSISTANT_SESSION",frozen.sessionId(),Map.of("turnId",turn),frozen.singleFeature()==null?2:1,"准备世界资料","world-assistant:"+turn);
        store.attach(turn,result.operationId());store.touch(frozen.sessionId());return new Accepted(turn,result.operationId(),frozen.sessionId());
    }
    private Frozen freezeSingle(User user,UUID worldId,UUID entryId,SingleRequest request) {
        if(request==null)throw invalid("WORLD_ASSISTANT_REQUEST_INVALID");var snapshot=context.freeze(user,worldId,request.expectedRevision());
        var entry=entryId==null?null:snapshot.content().entries().stream().filter(e->e.id().equals(entryId)).findFirst().orElseThrow(WorldAssistantErrors::missing);
        if(entry!=null&&Objects.toString(entry.content(),"").isBlank())throw invalid("WORLD_ENTRY_TEXT_REQUIRED");
        String instruction=Objects.toString(request.instruction(),"").strip();if(instruction.length()>8000)throw invalid("WORLD_ASSISTANT_REQUEST_INVALID");
        if(instruction.isBlank())instruction=entry==null?"整合已保存设定，形成世界概览。":"改善条目表述，保持已有事实与限制。";
        return new Frozen(worldId,null,entry==null?"OVERVIEW":"ENTRY_REFINE",instruction,null,null,entryId,snapshot,List.of(),context.templates(user),entry==null?"world.overview.integrate":resources.feature(entry,"refine"));
    }
    private Frozen freeze(User user,UUID worldId,TurnRequest request) {
        validateRequest(request);ownedSession(user,worldId,request.sessionId(),false);
        ProposalData parent=null;
        if(request.intent().equals("REFINE")) {
            if(request.parentProposalId()==null)throw invalid("WORLD_ASSISTANT_PARENT_REQUIRED");
            var proposal=requireProposal(user,worldId,request.parentProposalId(),false);
            if(!store.turn(proposal.turnId()).sessionId().equals(request.sessionId()))throw missing();
            if(!proposal.status().equals("READY")||!detail(user,worldId,proposal).applicable())throw conflict("WORLD_PROPOSAL_STALE");parent=data(proposal);
        } else if(request.parentProposalId()!=null)throw invalid("WORLD_ASSISTANT_REQUEST_INVALID");
        var ids=request.sourceTurnIds()==null?List.<UUID>of():request.sourceTurnIds();if(ids.size()>8||new HashSet<>(ids).size()!=ids.size())throw invalid("WORLD_ASSISTANT_SOURCES_INVALID");
        var histories=new ArrayList<History>();
        if(ids.isEmpty()) {
            for(var turn:store.turns(request.sessionId(),null,null,32))if(turn.resultJson()!=null){histories.add(history(turn));if(histories.size()==8)break;}
            Collections.reverse(histories);
        } else for(var id:ids) {
            var turn=requireTurn(user,worldId,id);if(!turn.sessionId().equals(request.sessionId())||turn.resultJson()==null)throw invalid("WORLD_ASSISTANT_SOURCES_INVALID");histories.add(history(turn));
        }
        if(request.suggestionId()!=null&&histories.stream().noneMatch(h->outcome(store.turn(h.turnId())).suggestions().stream().anyMatch(s->s.id().equals(request.suggestionId()))))throw invalid("WORLD_ASSISTANT_SOURCES_INVALID");
        return new Frozen(worldId,request.sessionId(),request.intent(),request.instruction(),request.parentProposalId(),parent,null,
            context.freeze(user,worldId,request.expectedDraftRevision()),List.copyOf(histories),context.templates(user),null);
    }
    private void validateRequest(TurnRequest request) {
        if(request==null||request.sessionId()==null||request.intent()==null||!Set.of("DISCUSS","PROPOSE","REFINE").contains(request.intent())||request.instruction()==null||request.instruction().isBlank()||request.instruction().length()>8000)throw invalid("WORLD_ASSISTANT_REQUEST_INVALID");
        if(request.intent().equals("DISCUSS")&&(request.suggestionId()!=null||request.parentProposalId()!=null))throw invalid("WORLD_ASSISTANT_REQUEST_INVALID");
    }
    @Transactional(readOnly=true)
    public TurnDto turn(User user,UUID worldId,UUID id){return dto(user,requireTurn(user,worldId,id));}
    @Transactional(readOnly=true)
    public ProposalDto proposal(User user,UUID worldId,UUID id){return detail(user,worldId,requireProposal(user,worldId,id,false));}
    @Transactional(readOnly=true)
    public ProposalPage proposals(User user,UUID worldId,String status,String type,UUID sessionId,String before) {
        resources.world(user,worldId,false);if(sessionId!=null)ownedSession(user,worldId,sessionId,false);
        var c=cursor(before);var rows=store.proposals(worldId,status,type,sessionId,c.time(),c.id(),21);boolean more=rows.size()>20;var page=rows.stream().limit(20).toList();
        return new ProposalPage(page.stream().map(p->detail(user,worldId,p)).toList(),more,more?page.getLast().createdAt()+"|"+page.getLast().id():null);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ProposalDto edit(User user,UUID worldId,UUID id,EditRequest request,String key) {
        key(key);resources.world(user,worldId,true);var proposal=requireProposal(user,worldId,id,true);String requestHash=hash("edit:"+store.write(request)),scope="proposal:"+id;
        var prior=replay(worldId,scope,key,requestHash,ProposalDto.class);if(prior!=null)return prior;
        checkProposal(proposal,request.expectedRevision());var data=data(proposal);data.candidate=resources.normalize(data.resourceType,data.fieldKey,data.action,request.candidate(),data.candidate);
        store.saveProposal(id,"READY",data);var result=detail(user,worldId,store.proposal(id,false));store.receipt(worldId,scope,key,requestHash,result);return result;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public DecisionResult decide(User user,UUID worldId,UUID id,String action,DecisionRequest request,String key) {
        key(key);var world=resources.world(user,worldId,true);var proposal=requireProposal(user,worldId,id,true);String requestHash=hash(action+":"+store.write(request)),scope="proposal:"+id;
        var prior=replay(worldId,scope,key,requestHash,DecisionResult.class);if(prior!=null)return prior;
        checkProposal(proposal,request.expectedRevision());var data=data(proposal);
        if(action.equals("accept")) {
            WorldVersionService.checkRevision(world,request.expectedDraftRevision());var state=resources.state(world,data);
            if(state.reason()!=null)throw conflict(state.reason());
            if(state.contextChanged()&&!request.acknowledgeContextChange())throw conflict("WORLD_PROPOSAL_CONTEXT_CHANGED");
            if(data.action.equals("UPDATE")&&data.candidate.equals(data.before))throw invalid("WORLD_PROPOSAL_NO_CHANGE");
            resources.apply(world,data);
        } else if(!action.equals("reject"))throw invalid("WORLD_ASSISTANT_DECISION_INVALID");
        store.saveProposal(id,action.equals("accept")?"ACCEPTED":"REJECTED",data);var result=new DecisionResult(detail(user,worldId,store.proposal(id,false)),world.getRevision());
        store.receipt(worldId,scope,key,requestHash,result);return result;
    }
    private void checkProposal(WorldAssistantStore.Proposal proposal,Long revision) {
        if(revision==null||proposal.revision()!=revision)throw conflict("WORLD_PROPOSAL_VERSION_CONFLICT");if(!proposal.status().equals("READY"))throw conflict("WORLD_PROPOSAL_DECIDED");
    }
    public Object execute(AiOperationExecution execution,UUID turnId) {
        var row=store.turn(turnId);var session=store.session(row.sessionId(),false);
        if(!session.userId().equals(execution.user().getId())||!execution.operationId().equals(row.operationId()))throw missing();
        if(row.resultJson()!=null)return result(turnId,outcome(row));
        var frozen=frozen(row);var reads=context.read(frozen,List.of());var exposed=new LinkedHashMap<String,Source>();reads.sources().forEach(s->exposed.put(s.id(),s));
        var response=call(execution,turnId,"plan",context.prompt(frozen,reads,frozen.singleFeature()!=null));var parsed=parse(response.content());
        if(parsed.path("kind").asText().equals("READ_BATCH")) {
            if(frozen.singleFeature()!=null)throw invalid("WORLD_ASSISTANT_READ_INVALID");
            execution.progress().step("读取冻结资料并形成结果",1,2);
            if(store.turn(turnId).readsJson()==null) {
                if(!parsed.path("reads").isArray())throw invalid("WORLD_ASSISTANT_READ_INVALID");
                List<ReadRequest> requests=json.convertValue(parsed.path("reads"),new TypeReference<>(){});var selected=context.read(frozen,requests);
                AiOperationExecutionContext.apply(()->{store.checkpoint(turnId,"reads_json",selected);return null;});
            }
            reads=store.read(store.turn(turnId).readsJson(),WorldAssistantContext.Reads.class);reads.sources().forEach(s->exposed.put(s.id(),s));
            response=call(execution,turnId,"final",context.prompt(frozen,reads,true));parsed=parse(response.content());
        }
        String kind=parsed.path("kind").asText(),answer=parsed.path("answer").asText("").strip();
        if(!Set.of("ANSWER","NEEDS_INPUT","PROPOSALS").contains(kind)||answer.isBlank()||frozen.intent().equals("DISCUSS")&&kind.equals("PROPOSALS"))throw invalid("WORLD_ASSISTANT_RESULT_INVALID");
        if(frozen.singleFeature()!=null&&!kind.equals("PROPOSALS")&&!kind.equals("NEEDS_INPUT"))throw invalid("WORLD_ASSISTANT_RESULT_INVALID");
        var conflicts=conflicts(parsed.path("conflicts"),exposed.keySet());var sources=new ArrayList<>(exposed.values());
        sources.add(WorldAssistantContext.source("turn:"+turnId,"AUTHOR_REQUEST","本次作者请求",frozen.instruction()));
        for(var history:frozen.history())sources.add(WorldAssistantContext.source("turn:"+history.turnId(),"DISCUSSION","未确认讨论",history.instruction()+"\n"+history.answer()));
        if(frozen.parent()!=null)sources.add(WorldAssistantContext.source("proposal:"+frozen.parentProposalId(),"PARENT_PROPOSAL","细化依据",store.write(frozen.parent())));
        var sourceTurns=new ArrayList<>(frozen.history().stream().map(History::turnId).toList());sourceTurns.add(turnId);
        var suggestions=new ArrayList<Suggestion>();
        if(frozen.intent().equals("DISCUSS")&&parsed.has("suggestions")) {
            if(!parsed.path("suggestions").isArray()||parsed.path("suggestions").size()>4)throw invalid("WORLD_ASSISTANT_RESULT_INVALID");
            for(var suggestion:parsed.path("suggestions")) {String title=suggestion.path("title").asText(""),reason=suggestion.path("reason").asText("");if(title.isBlank()||title.length()>120||reason.isBlank())throw invalid("WORLD_ASSISTANT_RESULT_INVALID");
                suggestions.add(new Suggestion(UUID.randomUUID(),title,reason,List.copyOf(sourceTurns.subList(Math.max(0,sourceTurns.size()-8),sourceTurns.size()))));}
        }
        var candidates=new ArrayList<ProposalData>();
        if(kind.equals("PROPOSALS")) {
            var items=parsed.path("items");if(!items.isArray()||items.isEmpty()||items.size()>12||(frozen.parent()!=null||frozen.singleFeature()!=null)&&items.size()!=1)throw invalid("WORLD_ASSISTANT_RESULT_INVALID");
            var ids=new HashMap<String,UUID>();int index=0;
            for(var item:items) {if(item.path("action").asText().equals("CREATE")&&item.path("resourceType").asText().equals("ENTRY")) {
                String key=item.path("clientKey").asText("item:"+index);if(key.isBlank()||ids.putIfAbsent(key,frozen.parent()==null?UUID.randomUUID():frozen.parent().targetId)!=null)throw invalid("WORLD_PROPOSAL_DUPLICATE_TARGET");}index++;}
            index=0;var targets=new HashSet<String>();for(var item:items) {
                var data=prepare(frozen,item,index++,ids,reads.sources());String target=data.resourceType+":"+data.targetId+":"+data.fieldKey;
                if(!targets.add(target))throw invalid("WORLD_PROPOSAL_DUPLICATE_TARGET");data.sources=List.copyOf(sources);data.sourceTurnIds=List.copyOf(sourceTurns);data.conflicts=conflicts;candidates.add(data);
            }
        }
        long unread=frozen.snapshot().pool().stream().filter(s->!exposed.containsKey(s.id())).count();var warnings=unread>0?List.of("WORLD_UNREAD_RESOURCES:"+unread):List.<String>of();
        return AiOperationExecutionContext.complete(()->{
            var ids=new ArrayList<UUID>();for(int i=0;i<candidates.size();i++)ids.add(store.createProposal(turnId,i,candidates.get(i)));
            var outcome=new Outcome(kind,answer,List.copyOf(suggestions),List.copyOf(ids),List.copyOf(sources),warnings,conflicts);store.finish(turnId,outcome);store.touch(session.id());return result(turnId,outcome);
        });
    }
    private ProposalData prepare(Frozen frozen,JsonNode item,int index,Map<String,UUID> ids,List<Source> finalSources) {
        var data=new ProposalData();data.action=item.path("action").asText();data.resourceType=item.path("resourceType").asText();data.fieldKey=item.hasNonNull("fieldKey")?item.path("fieldKey").asText():null;
        if(!Set.of("CREATE","UPDATE").contains(data.action)||!Set.of("ENTRY","RELATION","METADATA","OVERVIEW").contains(data.resourceType))throw invalid("WORLD_ASSISTANT_RESULT_INVALID");
        if(!data.resourceType.equals("METADATA")&&data.fieldKey!=null)throw invalid("WORLD_PROPOSAL_TARGET_INVALID");
        data.targetId=data.action.equals("UPDATE")?WorldAssistantResources.id(item.path("targetId")):data.resourceType.equals("ENTRY")?ids.get(item.path("clientKey").asText("item:"+index)):UUID.randomUUID();
        if(frozen.parent()!=null&&data.action.equals("CREATE"))data.targetId=frozen.parent().targetId;
        data.worldHash=frozen.snapshot().hash();data.parentProposalId=frozen.parentProposalId();
        if(data.action.equals("UPDATE")) {
            var target=resources.target(frozen.worldId(),frozen.snapshot().content(),data.resourceType,data.targetId,data.fieldKey);
            if(target==null||finalSources.stream().noneMatch(s->s.id().equals(WorldAssistantResources.sourceId(target))&&!s.truncated()))throw invalid("WORLD_TARGET_NOT_READ");data.before=target.fields();
        }
        if(frozen.parent()!=null) {
            var parent=frozen.parent();if(!data.action.equals(parent.action)||!data.resourceType.equals(parent.resourceType)||!data.targetId.equals(parent.targetId)||!Objects.equals(data.fieldKey,parent.fieldKey))throw invalid("WORLD_CANDIDATE_STRUCTURE_CHANGED");data.before=parent.before;
        }
        if(frozen.intent().equals("ENTRY_REFINE")&&(!data.action.equals("UPDATE")||!data.resourceType.equals("ENTRY")||!data.targetId.equals(frozen.targetId())))throw invalid("WORLD_CANDIDATE_STRUCTURE_CHANGED");
        if(frozen.intent().equals("OVERVIEW")&&(!data.action.equals("UPDATE")||!data.resourceType.equals("OVERVIEW")))throw invalid("WORLD_CANDIDATE_STRUCTURE_CHANGED");
        if(data.resourceType.equals("OVERVIEW")&&!frozen.intent().equals("OVERVIEW")&&(finalSources.size()!=frozen.snapshot().pool().size()))throw invalid("WORLD_OVERVIEW_FULL_CONTEXT_REQUIRED");
        JsonNode fields=item.path("fields").deepCopy();
        if(data.resourceType.equals("RELATION")&&fields.isObject())for(String key:List.of("sourceId","targetId")) {
            String raw=fields.path(key).asText();if(ids.containsKey(raw))((ObjectNode)fields).put(key,ids.get(raw).toString());
        }
        data.candidate=resources.normalize(data.resourceType,data.fieldKey,data.action,fields,frozen.parent()==null?data.before:frozen.parent().candidate);
        if(data.resourceType.equals("RELATION"))for(String key:List.of("sourceId","targetId")) {
            UUID endpoint=WorldAssistantResources.id(data.candidate.path(key));if(frozen.snapshot().content().entries().stream().noneMatch(e->e.id().equals(endpoint))&&!ids.containsValue(endpoint)&&frozen.parent()==null)throw invalid("WORLD_PROPOSAL_ENDPOINT_MISSING");
            if(frozen.parent()!=null&&!frozen.parent().candidate.path(key).equals(data.candidate.path(key)))throw invalid("WORLD_CANDIDATE_STRUCTURE_CHANGED");
        }
        if(frozen.intent().equals("ENTRY_REFINE")) {
            var original=(ObjectNode)data.before.deepCopy();
            for(var names=fields.fieldNames();names.hasNext();) {String key=names.next();if(!key.equals("content")&&!Objects.equals(data.before.get(key),fields.get(key)))throw invalid("WORLD_CANDIDATE_STRUCTURE_CHANGED");}
            original.set("content",data.candidate.path("content"));data.candidate=original;
        }
        if(data.action.equals("UPDATE")&&data.candidate.equals(data.before))throw invalid("WORLD_PROPOSAL_NO_CHANGE");
        data.modelCandidate=data.candidate.deepCopy();data.rationale=item.path("rationale").asText("").strip();if(data.rationale.isBlank())throw invalid("WORLD_ASSISTANT_RESULT_INVALID");return data;
    }
    private List<SemanticConflict> conflicts(JsonNode raw,Set<String> read) {
        if(raw.isMissingNode()||raw.isNull())return List.of();if(!raw.isArray()||raw.size()>12)throw invalid("WORLD_ASSISTANT_RESULT_INVALID");
        var result=new ArrayList<SemanticConflict>();for(var item:raw) {
            String title=item.path("title").asText(""),explanation=item.path("explanation").asText("");var ids=new LinkedHashSet<String>();
            if(!item.path("sourceIds").isArray())throw invalid("WORLD_CONFLICT_SOURCE_INVALID");item.path("sourceIds").forEach(id->ids.add(id.asText()));
            if(title.isBlank()||explanation.isBlank()||ids.size()<2||!read.containsAll(ids))throw invalid("WORLD_CONFLICT_SOURCE_INVALID");result.add(new SemanticConflict(title,explanation,List.copyOf(ids)));
        }return List.copyOf(result);
    }
    private AiChatResponse call(AiOperationExecution execution,UUID turnId,String stage,AiChatRequest request) {
        var row=store.turn(turnId);String result=stage.equals("plan")?row.planResultJson():row.finalResultJson();if(result!=null)return store.read(result,AiChatResponse.class);
        var frozen=frozen(row);String feature=frozen.singleFeature()==null?"world_assistant."+frozen.intent().toLowerCase(Locale.ROOT)+"."+stage:frozen.singleFeature();
        String saved=stage.equals("plan")?row.planRequestJson():row.finalRequestJson();
        if(saved==null){var configured=request.forFeature(feature);AiOperationExecutionContext.apply(()->{store.checkpoint(turnId,stage+"_request_json",configured);return null;});row=store.turn(turnId);saved=stage.equals("plan")?row.planRequestJson():row.finalRequestJson();}
        var input=store.read(saved,AiChatRequest.class);AiOperationExecutionContext.apply(()->null);
        var response=ai.chatWithIdempotency(execution.user(),input,execution.operationId()+":world-assistant:"+stage);store.checkpoint(turnId,stage+"_result_json",response);return response;
    }
    private ProposalDto detail(User user,UUID worldId,WorldAssistantStore.Proposal proposal) {
        var data=data(proposal);var world=resources.world(user,worldId,false);var state=resources.state(world,data);
        var changes=state.contextChanged()?WorldVersionService.compare(null,null,world.getRevision(),frozen(store.turn(proposal.turnId())).snapshot().content(),resources.content(world)).changes():List.<com.ainovel.app.world.WorldDraftDtos.Change>of();
        return new ProposalDto(proposal.id(),proposal.turnId(),store.turn(proposal.turnId()).sessionId(),data.parentProposalId,data.action,data.resourceType,data.targetId,data.fieldKey,proposal.status(),proposal.revision(),state.revision(),data.before,data.candidate,data.modelCandidate,state.current(),data.rationale,data.sources,data.sourceTurnIds,data.conflicts,!data.candidate.equals(data.modelCandidate),state.contextChanged(),proposal.status().equals("READY")&&state.reason()==null,state.reason(),proposal.createdAt(),changes);
    }
    private TurnDto dto(User user,WorldAssistantStore.Turn turn) {
        var frozen=frozen(turn);var out=turn.resultJson()==null?null:outcome(turn);double input=0,output=0,cost=0;int calls=0;
        for(String raw:new String[]{turn.planResultJson(),turn.finalResultJson()})if(raw!=null){calls++;var usage=store.read(raw,AiChatResponse.class).usage();if(usage!=null){input+=usage.inputTokens();output+=usage.outputTokens();cost+=usage.cost();}}
        String status=turn.operationId()==null?"QUEUED":operations.get(user,turn.operationId()).status().name();
        return new TurnDto(turn.id(),turn.operationId(),status,out==null?null:out.kind(),frozen.instruction(),out==null?null:out.answer(),frozen.intent(),out==null?List.of():out.suggestions(),out==null?List.of():out.proposalIds(),frozen.parentProposalId(),out==null?List.of():out.sources(),out==null?List.of():out.warnings(),out==null?List.of():out.conflicts(),new Usage(input,output,cost,calls),turn.createdAt());
    }
    private record Cursor(java.time.Instant time,UUID id) {}
    private Cursor cursor(String before){if(before==null)return new Cursor(null,null);try{var parts=before.split("\\|",2);return new Cursor(java.time.Instant.parse(parts[0]),UUID.fromString(parts[1]));}catch(Exception ex){throw invalid("WORLD_ASSISTANT_CURSOR_INVALID");}}
    private WorldAssistantStore.Session ownedSession(User user,UUID worldId,UUID id,boolean lock){resources.world(user,worldId,false);var session=store.session(id,lock);if(!session.worldId().equals(worldId)||!session.userId().equals(user.getId()))throw missing();return session;}
    private WorldAssistantStore.Turn requireTurn(User user,UUID worldId,UUID id){var turn=store.turn(id);ownedSession(user,worldId,turn.sessionId(),false);return turn;}
    private WorldAssistantStore.Proposal requireProposal(User user,UUID worldId,UUID id,boolean lock){var proposal=store.proposal(id,lock);requireTurn(user,worldId,proposal.turnId());return proposal;}
    private ProposalData data(WorldAssistantStore.Proposal proposal){return store.read(proposal.dataJson(),ProposalData.class);}
    private Frozen frozen(WorldAssistantStore.Turn turn){return store.read(turn.frozenJson(),Frozen.class);}
    private Outcome outcome(WorldAssistantStore.Turn turn){return store.read(turn.resultJson(),Outcome.class);}
    private History history(WorldAssistantStore.Turn turn){return new History(turn.id(),frozen(turn).instruction(),outcome(turn).answer());}
    private SessionSummary summary(WorldAssistantStore.Session session){return new SessionSummary(session.id(),session.worldId(),session.title(),session.createdAt(),session.updatedAt());}
    private String title(SessionRequest request){if(request==null||request.title()==null||request.title().isBlank()||request.title().strip().length()>120)throw invalid("WORLD_ASSISTANT_TITLE_INVALID");return request.title().strip();}
    private <T>T replay(UUID world,String scope,String key,String requestHash,Class<T> type){var old=store.receipt(world,scope,key);if(old==null)return null;if(!old.requestHash().equals(requestHash))throw conflict("WORLD_ASSISTANT_IDEMPOTENCY_CONFLICT");return store.read(old.responseJson(),type);}
    private JsonNode parse(String raw){try{var value=json.readTree(raw);if(!value.isObject())throw new IllegalArgumentException();return value;}catch(Exception ex){throw invalid("WORLD_ASSISTANT_RESULT_INVALID");}}
    private Map<String,Object> result(UUID turn,Outcome out){return Map.of("turnId",turn,"outcome",out.kind(),"proposalIds",out.proposalIds());}
}
