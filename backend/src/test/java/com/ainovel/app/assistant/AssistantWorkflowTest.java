package com.ainovel.app.assistant;

import com.ainovel.app.ai.*;
import com.ainovel.app.ai.dto.*;
import com.ainovel.app.aioperation.*;
import com.ainovel.app.common.ApiStatusException;
import com.ainovel.app.manuscript.*;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.narrative.*;
import com.ainovel.app.quality.language.LanguageFeature;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.user.User;
import com.ainovel.app.v2.V2VersionPersistenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.ainovel.app.assistant.AssistantDtos.*;
import static com.ainovel.app.quality.language.LanguageProjection.hash;

class AssistantWorkflowTest {
    final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    final AiService ai=mock(AiService.class);
    final AssistantStore store=mock(AssistantStore.class);
    final User user=new User();
    final UUID turnId=UUID.randomUUID(),operationId=UUID.randomUUID(),sessionId=UUID.randomUUID(),manuscriptId=UUID.randomUUID(),branchId=UUID.randomUUID(),sceneId=UUID.randomUUID();
    final Map<String,String> checkpoints=new HashMap<>();
    final AtomicReference<WritingAssistantService.Outcome> outcome=new AtomicReference<>();
    final AtomicReference<WritingAssistantService.ProposalData> proposal=new AtomicReference<>();
    AssistantContext context;
    AssistantPrompts prompts;
    WritingAssistantService service;
    WritingAssistantService.Frozen frozen;
    @BeforeEach void setup()throws Exception{
        ReflectionTestUtils.setField(user,"id",UUID.randomUUID());
        context=new AssistantContext(json,mock(ManuscriptContentService.class),mock(NarrativeContextService.class),mock(com.ainovel.app.story.repo.CharacterCardRepository.class),mock(com.ainovel.app.world.WorldBindingService.class));
        var admission=new AiAdmissionGuard(mock(StringRedisTemplate.class),json,64,20000,100000,20,2,32);
        prompts=new AssistantPrompts(json,admission);
        service=new WritingAssistantService(store,mock(ResourceAccessGuard.class),mock(ManuscriptRepository.class),mock(ManuscriptContentService.class),mock(ManuscriptService.class),mock(NarrativeContextService.class),context,prompts,mock(AiOperationService.class),ai,json,mock(V2VersionPersistenceService.class),mock(ApplicationEventPublisher.class),mock(LanguageFeature.class));
        frozen=frozen("<p>她推开门。</p>","EDIT",snapshot(false),List.of());
        when(store.write(any())).thenAnswer(a->json.writeValueAsString(a.getArgument(0)));
        when(store.read(anyString(),any())).thenAnswer(a->json.readValue((String)a.getArgument(0),(Class<?>)a.getArgument(1)));
        when(store.turn(turnId)).thenAnswer(a->new AssistantStore.Turn(turnId,sessionId,"request-key","hash",operationId,json.writeValueAsString(frozen),checkpoints.get("plan_request_json"),checkpoints.get("plan_result_json"),checkpoints.get("final_request_json"),checkpoints.get("final_result_json"),checkpoints.get("reads_json"),outcome.get()==null?null:json.writeValueAsString(outcome.get()),Instant.EPOCH));
        when(store.session(sessionId)).thenReturn(new AssistantStore.Session(sessionId,user.getId(),manuscriptId,branchId,sceneId));
        doAnswer(a->{checkpoints.putIfAbsent(a.getArgument(1),json.writeValueAsString(a.getArgument(2)));return null;}).when(store).checkpoint(eq(turnId),anyString(),any());
        doAnswer(a->{outcome.set(a.getArgument(1));return null;}).when(store).finish(eq(turnId),any());
        when(store.createProposal(eq(turnId),any())).thenAnswer(a->{proposal.set(a.getArgument(1));return UUID.fromString("00000000-0000-0000-0000-000000000007");});
    }
    @Test void firstCallCanDeliverAndReplayWithoutAnotherModelCall(){
        when(ai.chatWithIdempotency(eq(user),any(),anyString())).thenReturn(response(edit()));
        execute();assertEquals("PROPOSAL_READY",outcome.get().kind());assertTrue(proposal.get().afterHtml.contains("缓缓"));
        execute();verify(ai,times(1)).chatWithIdempotency(eq(user),any(),eq(operationId+":assistant:plan"));
    }
    @Test void readsOnceThenCreatesProposalWithEvidence(){
        when(ai.chatWithIdempotency(eq(user),any(),anyString())).thenReturn(response(read("EDIT","钥匙")),response(edit()));
        execute();assertEquals(2,checkpoints.keySet().stream().filter(k->k.endsWith("result_json")).count());
        assertTrue(outcome.get().sources().stream().anyMatch(s->s.type().equals("PREVIOUS_SCENE")));verify(ai,times(2)).chatWithIdempotency(eq(user),any(),anyString());
    }
    @Test void secondCallFailureRetainsFirstReceiptAndExactFinalRequest(){
        when(ai.chatWithIdempotency(eq(user),any(),eq(operationId+":assistant:plan"))).thenReturn(response(read("EDIT","钥匙")));
        when(ai.chatWithIdempotency(eq(user),any(),eq(operationId+":assistant:final"))).thenThrow(new AiResultUncertainException(new RuntimeException("timeout"))).thenReturn(response(edit()));
        assertThrows(AiResultUncertainException.class,this::execute);String request=checkpoints.get("final_request_json");assertNotNull(checkpoints.get("plan_result_json"));
        execute();assertEquals(request,checkpoints.get("final_request_json"));verify(ai,times(1)).chatWithIdempotency(eq(user),any(),eq(operationId+":assistant:plan"));
    }
    @Test void invalidFirstResultIsRetainedAndNeverAutomaticallyRepaired(){
        when(ai.chatWithIdempotency(eq(user),any(),anyString())).thenReturn(response("not-json"));
        assertThrows(ApiStatusException.class,this::execute);assertNotNull(checkpoints.get("plan_result_json"));
        assertThrows(ApiStatusException.class,this::execute);verify(ai,times(1)).chatWithIdempotency(eq(user),any(),anyString());
    }
    @Test void cannotRequestThirdModelRound(){
        when(ai.chatWithIdempotency(eq(user),any(),anyString())).thenReturn(response(read("EDIT","钥匙")));
        assertThrows(ApiStatusException.class,this::execute);verify(ai,times(2)).chatWithIdempotency(eq(user),any(),anyString());assertNull(proposal.get());
    }
    @Test void h2ReadAndHistoryDoNotLeakAuthorOnlyInformation(){
        frozen=frozen("<p>她推开门。</p>","EDIT",snapshot(true),List.of(new WritingAssistantService.History("AUTHOR_QA","秘密问题","凶手秘密XYZ")));
        when(ai.chatWithIdempotency(eq(user),any(),anyString())).thenReturn(response(read("EDIT","秘密")),response(edit()));execute();
        assertFalse(checkpoints.get("plan_request_json").contains("凶手秘密XYZ"));assertFalse(checkpoints.get("final_request_json").contains("凶手秘密XYZ"));assertTrue(outcome.get().warnings().contains("ASSISTANT_SOURCE_NOT_FOUND:秘密"));
        assertTrue(checkpoints.get("final_request_json").contains("unmatchedQueries"));
    }
    @Test void authorQuestionCannotProduceEdit(){
        frozen=frozen("<p>她推开门。</p>","AUTHOR_QA",snapshot(false),List.of());
        when(ai.chatWithIdempotency(eq(user),any(),anyString())).thenReturn(response(edit()));assertThrows(ApiStatusException.class,this::execute);assertNull(proposal.get());
        assertTrue(checkpoints.get("plan_request_json").contains("所有结果的 intent 必须保持 AUTHOR_QA"));
    }
    @Test void explicitEditCanRequestClarificationWithoutChangingPurpose(){
        when(ai.chatWithIdempotency(eq(user),any(),anyString())).thenReturn(response("{\"kind\":\"NEEDS_INPUT\",\"intent\":\"EDIT\",\"answer\":\"请说明希望保留的结尾。\"}"));
        execute();assertEquals("NEEDS_INPUT",outcome.get().kind());assertEquals("EDIT",outcome.get().intent());assertNull(proposal.get());
        assertTrue(checkpoints.get("plan_request_json").contains("所有结果的 intent 必须保持 EDIT"));
        assertTrue(AssistantPrompts.RULES.contains("\"kind\":\"NEEDS_INPUT\",\"intent\":\"EDIT\""));
        verify(ai,times(1)).chatWithIdempotency(eq(user),any(),anyString());
    }
    @Test void autoCanChooseAuthorQuestionAfterAnEditSafeFirstStep(){
        frozen=frozen("<p>她推开门。</p>","AUTO",snapshot(true),List.of());
        when(ai.chatWithIdempotency(eq(user),any(),anyString())).thenReturn(response(read("AUTHOR_QA","秘密")),response("{\"kind\":\"ANSWER\",\"intent\":\"AUTHOR_QA\",\"answer\":\"仅向作者解释 scene:2 中的秘密。\"}"));
        execute();assertEquals("ANSWER",outcome.get().kind());assertEquals("AUTHOR_QA",outcome.get().intent());assertNull(proposal.get());
        assertTrue(checkpoints.get("plan_request_json").contains("请求用途：AUTO"));
        assertTrue(checkpoints.get("plan_request_json").contains("首步只提供受控编辑上下文"));
        assertFalse(checkpoints.get("plan_request_json").contains("凶手秘密XYZ"));
        assertTrue(checkpoints.get("final_request_json").contains("凶手秘密XYZ"));
        assertTrue(checkpoints.get("final_request_json").contains("所有结果的 intent 必须保持 AUTHOR_QA"));
        verify(ai,times(2)).chatWithIdempotency(eq(user),any(),anyString());
    }
    @Test void readLimitAndUnsupportedToolsAreRejectedWithoutExecution(){
        var requests=Collections.nCopies(5,new ReadRequest("search_previous","钥匙",null));
        assertThrows(ApiStatusException.class,()->context.read(snapshot(false),"EDIT",requests));
        assertThrows(ApiStatusException.class,()->context.read(snapshot(false),"EDIT",List.of(new ReadRequest("save_scene","x",null))));
    }
    @Test void completeOriginalIsNeverTruncatedToFitBudget(){
        var f=frozen("<p>"+"文".repeat(30000)+"最终结尾</p>","EDIT",snapshot(false),List.of());
        assertThrows(ApiStatusException.class,()->prompts.build(f,context.read(f.context(),"EDIT",List.of()),false));verifyNoInteractions(ai);
    }
    @Test void requiredContextCannotBeSilentlyOmitted(){
        var source=new Source("required","OUTLINE","大纲","文".repeat(3600),null,"hash",false,null);
        var snapshot=new AssistantContext.Snapshot(false,null,List.of(source),List.of(),List.of(),2800,3200);
        assertThrows(ApiStatusException.class,()->context.read(snapshot,"EDIT",List.of()));
    }
    @Test void parentCandidateIsIncludedInFullAndBudgeted(){
        var original=frozen("<p>她推开门。</p>","EDIT",snapshot(false),List.of());
        var changes=List.of(new AssistantDocumentProjection.Replacement(original.document().runs().getFirst().runId(),List.of("她缓缓推开门。")));
        var f=new WritingAssistantService.Frozen(manuscriptId,branchId,sceneId,1,original.serverHash(),"保留结尾","EDIT",UUID.randomUUID(),original.document(),changes,original.context(),List.of(),"");
        var request=prompts.build(f,context.read(f.context(),"EDIT",List.of()),false);
        assertTrue(request.messages().stream().anyMatch(m->m.content().contains("上一份完整候选")&&m.content().contains("<p>她缓缓推开门。</p>")));
    }
    @Test void optionalHistoryOmissionIsVisible(){
        frozen=frozen("<p>她推开门。</p>","EDIT",snapshot(false),List.of(new WritingAssistantService.History("EDIT","旧问题","汉".repeat(40000))));
        when(ai.chatWithIdempotency(eq(user),any(),anyString())).thenReturn(response(edit()));execute();assertTrue(outcome.get().warnings().contains("ASSISTANT_HISTORY_OMITTED"));
    }
    @Test void budgetDropsOldHistoryBeforeRecentHistory(){
        var f=frozen("<p>她推开门。</p>","EDIT",snapshot(false),List.of(new WritingAssistantService.History("EDIT","最旧问题","旧".repeat(39000)),new WritingAssistantService.History("EDIT","最新问题","最新答复")));
        var request=prompts.build(f,context.read(f.context(),"EDIT",List.of()),false);
        assertEquals(1,request.context().path("assistantHistoryCount").asInt());
        assertTrue(request.messages().stream().anyMatch(m->m.content().contains("最新答复")));
        assertFalse(request.messages().stream().anyMatch(m->m.content().contains("最旧问题")));
    }
    @Test void h2CannotReuseNonIsolatedOrChangedStampEditHistory(){
        assertFalse(WritingAssistantService.historyAllowed(snapshot(true),frozen,"EDIT"));
        var stamp=new NarrativeContextDtos.Stamp(manuscriptId,branchId,1,1,1,1,"order");
        var current=new AssistantContext.Snapshot(true,stamp,List.of(),List.of(),List.of(),2800,3200);
        var changed=frozen("<p>原稿</p>","EDIT",snapshot(true),List.of());
        assertFalse(WritingAssistantService.historyAllowed(current,changed,"EDIT"));
        assertTrue(WritingAssistantService.historyAllowed(current,changed,"AUTHOR_QA"));
    }
    @Test void unreadWarningsIdentifyOnlyVisibleSourcesAndModelMetadataFitsBudget()throws Exception{
        var hits=new ArrayList<Source>();
        for(int i=0;i<4;i++)hits.add(new Source("source:"+i,"PREVIOUS_SCENE","可见前文"+i,"钥匙"+"字".repeat(1200),UUID.randomUUID(),"hash"+i,false,null));
        var snapshot=new AssistantContext.Snapshot(false,null,snapshot(false).base(),hits,hits,2800,3200);
        var reads=context.read(snapshot,"EDIT",List.of(new ReadRequest("search_previous","钥匙",null),new ReadRequest("read_context","未命中线索",null)));
        assertTrue(reads.warnings().stream().anyMatch(w->w.startsWith("ASSISTANT_CONTEXT_OMITTED:可见前文")));
        assertTrue(reads.warnings().contains("ASSISTANT_SOURCE_NOT_FOUND:未命中线索"));
        int actual=AssistantContext.tokens(json.writeValueAsString(reads.sources()))+AssistantContext.tokens("读取范围（未命中或省略不等于作品中不存在）：\n"+json.writeValueAsString(AssistantContext.readMetadata(reads)));
        assertTrue(actual<=AssistantContext.BUDGET);
    }
    private Object execute(){return service.execute(new AiOperationExecution(operationId,user,"{}",json,(a,b,c)->{}),turnId);}
    private AssistantContext.Snapshot snapshot(boolean h2){
        var base=new Source(h2?"h2:scene":"outline:1",h2?"H2_SCENE":"OUTLINE","本场","她推门进屋",sceneId,hash("她推门进屋"),false,null);
        var previous=new Source("scene:1","PREVIOUS_SCENE","上场","她把钥匙交给姐姐。",UUID.randomUUID(),"oldhash",false,null);
        var secret=new Source("scene:2","PREVIOUS_SCENE","秘密","凶手秘密XYZ",UUID.randomUUID(),"secret",false,null);
        return new AssistantContext.Snapshot(h2,null,List.of(base),h2?List.of(base):List.of(previous),List.of(previous,secret),2800,3200);
    }
    private WritingAssistantService.Frozen frozen(String html,String intent,AssistantContext.Snapshot context,List<WritingAssistantService.History> history){return new WritingAssistantService.Frozen(manuscriptId,branchId,sceneId,1,hash(html),"动作自然些，保留结尾",intent,null,AssistantDocumentProjection.project(html,null),List.of(),context,history,"");}
    private String edit(){return "{\"kind\":\"EDIT_PROPOSAL\",\"intent\":\"EDIT\",\"answer\":\"调整动作\",\"replacements\":[{\"runId\":\""+frozen.document().runs().getFirst().runId()+"\",\"paragraphs\":[\"她缓缓推开门。\"]}]}";}
    private static String read(String intent,String query){return "{\"kind\":\"READ_BATCH\",\"intent\":\""+intent+"\",\"reads\":[{\"tool\":\"search_previous\",\"query\":\""+query+"\"}]}";}
    private static AiChatResponse response(String text){return new AiChatResponse("assistant",text,new AiUsageDto(10,20,0,0,1),100);}
}
