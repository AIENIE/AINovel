package com.ainovel.app.writingagent;

import com.ainovel.app.ai.*;
import com.ainovel.app.ai.dto.*;
import com.ainovel.app.assistant.*;
import com.ainovel.app.manuscript.*;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.modelconfig.*;
import com.ainovel.app.prompt.*;
import com.ainovel.app.quality.language.*;
import com.ainovel.app.story.model.*;
import com.ainovel.app.user.User;
import com.ainovel.app.world.*;
import com.ainovel.app.worldcontext.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import fireflychat.ai.v1.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static com.ainovel.app.modelconfig.AiModelConfigurationService.bytes;
import static com.ainovel.app.writingagent.WritingAgentDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Actual InnoDB locks/uniqueness and persistence; transport is always a local Java double. */
@EnabledIfEnvironmentVariable(named="AIENIE_AUDIT_MYSQL_URL",matches="jdbc:mysql:.*")
class WritingAgentMysqlTest {
    static DriverManagerDataSource data;static JdbcTemplate jdbc;static DataSourceTransactionManager manager;
    final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    WritingAgentStore store;WritingAgentMoney money;Fixture fixture;
    record Fixture(User user,Manuscript manuscript,UUID scene,UUID version,WorldBindingService.Resolved world) {}
    @BeforeAll static void database() {
        String url=System.getenv("AIENIE_AUDIT_MYSQL_URL");
        if(url==null||!url.matches("jdbc:mysql://[^/]+/aienie_novel_audit_test_[A-Za-z0-9_]+(?:\\?.*)?"))throw new IllegalArgumentException("Isolated schema required");
        data=new DriverManagerDataSource(url,System.getenv("AIENIE_AUDIT_MYSQL_USERNAME"),System.getenv("AIENIE_AUDIT_MYSQL_PASSWORD"));
        Flyway.configure().dataSource(data).target("50").load().migrate();jdbc=new JdbcTemplate(data);manager=new DataSourceTransactionManager(data);
    }
    @BeforeEach void fixture() {
        store=new WritingAgentStore(jdbc,json,manager);money=new WritingAgentMoney(store);
        User user=new User();user.setId(UUID.randomUUID());user.setUsername("agent-"+UUID.randomUUID());user.setRemoteUid(7L);
        UUID storyId=UUID.randomUUID(),outlineId=UUID.randomUUID(),manuscriptId=UUID.randomUUID(),branch=UUID.randomUUID(),scene=UUID.randomUUID(),world=UUID.randomUUID(),version=UUID.randomUUID();
        jdbc.update("insert into users(id,username,email,password_hash,banned,credits) values(?,?,?,'x',false,1000)",bytes(user.getId()),user.getUsername(),user.getUsername()+"@example.invalid");
        jdbc.update("insert into stories(id,user_id,title,status) values(?,?,'故事','draft')",bytes(storyId),bytes(user.getId()));
        jdbc.update("insert into outlines(id,story_id,title,content_json) values(?,?,'大纲','{\"chapters\":[]}' )",bytes(outlineId),bytes(storyId));
        String html="<p>她推开灯塔的门。</p>";
        jdbc.update("insert into manuscripts(id,outline_id,title,sections_json,version) values(?,?,'稿件',?,0)",bytes(manuscriptId),bytes(outlineId),store.write(Map.of(scene.toString(),html)));
        jdbc.update("insert into manuscript_branches(id,manuscript_id,name,status,is_main) values(?,?,'main','active',true)",bytes(branch),bytes(manuscriptId));
        jdbc.update("update manuscripts set current_branch_id=? where id=?",bytes(branch),bytes(manuscriptId));
        jdbc.update("insert into worlds(id,user_id,name,status) values(?,?,'世界','draft')",bytes(world),bytes(user.getId()));
        var content=new WorldDraftDtos.Content("世界","简介",List.of(),"","",List.of(new WorldDraftDtos.Entry(UUID.randomUUID(),"RULE","灯塔规则","","WORLD_SECRET_FULL_BODY",List.of(),List.of(),null,null)),List.of(),Map.of(),"{}","WORLD_SECRET_OVERVIEW");
        String snapshot=store.write(content),hash=WritingAgentStore.hash(snapshot);
        jdbc.update("insert into world_versions(id,world_id,sequence_number,origin,draft_revision,content_hash,snapshot_json,created_at) values(?,?,1,'PUBLISHED',1,?,?,UTC_TIMESTAMP(6))",bytes(version),bytes(world),hash,snapshot);
        var story=new Story();story.setId(storyId);story.setTitle("故事");story.setUser(user);
        var outline=new Outline();outline.setId(outlineId);outline.setStory(story);outline.setContentJson("{\"chapters\":[]}");
        var manuscript=new Manuscript();manuscript.setId(manuscriptId);manuscript.setOutline(outline);manuscript.setCurrentBranchId(branch);manuscript.setSectionsJson(store.write(Map.of(scene.toString(),html)));
        fixture=new Fixture(user,manuscript,scene,version,new WorldBindingService.Resolved(world,version,"STORY",storyId,1,"世界",1,"PUBLISHED",hash,null,content));
    }
    WorldAgentTools worlds() {
        var binding=mock(WorldBindingService.class);var preferences=mock(WorldContextPreferences.class);
        when(binding.resolve(fixture.manuscript())).thenReturn(fixture.world());when(binding.requireUsable(any())).thenAnswer(i->i.getArgument(0));
        when(preferences.read(anyString(),any())).thenReturn(WorldContextDtos.Preferences.defaults(fixture.world().worldId()));
        return new WorldAgentTools(json,binding,preferences);
    }
    Frozen frozen() {
        String html="<p>她推开灯塔的门。</p>";var context=new AssistantContext.Snapshot(false,null,List.of(),List.of(),List.of(),1,5000);
        return new Frozen(PROTOCOL,fixture.manuscript().getId(),fixture.manuscript().getCurrentBranchId(),fixture.scene(),0,WritingAgentStore.hash(html),"完善动作","EDIT","fast",AssistantDocumentProjection.project(html,null),context,worlds().freeze(fixture.manuscript(),fixture.scene(),false),List.of(),"",List.of(),"planning",false,false,new BigDecimal("2"),10,Limits.defaults(),null);
    }
    UUID run(){return store.isolated(()->store.create(fixture.user().getId(),frozen(),"request-"+UUID.randomUUID(),"hash"));}
    @Test void requestsStepsAndCandidatesRemainDurableWithoutTouchingFormalTextOrSnapshot() {
        UUID run=run();var f=frozen();var request=new AiChatRequest(List.of(new AiChatRequest.Message("user","原输入")),null,null);
        var original=store.run(run);assertEquals(run,store.replay(fixture.user().getId(),f.manuscriptId(),f.sceneId(),original.requestKey()).id());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->store.create(fixture.user().getId(),f,original.requestKey(),"different-hash"));
        var step=store.request(run,0,"step-zero","MODEL","writing_agent.control",request);
        assertEquals(step.id(),store.request(run,0,"step-zero","MODEL","writing_agent.control",request).id());
        assertThrows(com.ainovel.app.common.ApiStatusException.class,()->store.request(run,0,"step-zero","MODEL","writing_agent.control",request.forFeature("changed")));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->store.request(run,0,"other-key","MODEL","writing_agent.control",request));
        assertThrows(IllegalStateException.class,()->store.isolated(()->{store.result(run,"step-zero",Map.of("answer","已产生"));throw new IllegalStateException("business rollback");}));
        assertTrue(new WritingAgentStore(jdbc,json,manager).step(run,"step-zero").result().contains("已产生"));
        UUID candidate=UUID.randomUUID();var c=new Candidate(candidate,1,null,"<p>候选正文</p>",WritingAgentStore.hash("<p>候选正文</p>"),4,List.of(),List.of());
        jdbc.update("insert into narrative_generation_candidates(id,manuscript_id,branch_id,scene_id,content,stamp_json,status,created_at,operation_id,generation_sequence,source_kind,metadata_json) values(?,?,?,?,?,'{}','READY',UTC_TIMESTAMP(6),?,1,'AGENT',?)",bytes(candidate),bytes(f.manuscriptId()),bytes(f.branchId()),bytes(f.sceneId()),c.html(),bytes(run),store.write(c));
        store.state(run,"PAUSED_USER",Map.of("candidateId",candidate));
        var reloaded=new WritingAgentStore(jdbc,json,manager);assertEquals("PAUSED_USER",reloaded.run(run).status());assertEquals(f.world().fingerprint(),reloaded.read(reloaded.run(run).frozen(),Frozen.class).world().fingerprint());
        assertEquals(c,store.read(jdbc.queryForObject("select metadata_json from narrative_generation_candidates where id=?",String.class,bytes(candidate)),Candidate.class));
        assertEquals(f.document().sourceHtml(),jsonReadSection());
        assertEquals(fixture.world().contentHash(),jdbc.queryForObject("select SHA2(snapshot_json,256) from world_versions where id=?",String.class,bytes(fixture.version())));
    }
    @Test void concurrentReservationsCannotExceedTheRunOrDispatchBothSteps() throws Exception {
        UUID run=run();store.state(run,"RUNNING",Map.of());
        var selected=new AiModelSelection("writing_agent.control","option","model","Model","OFF",null,false,true,"FIXED",1L,null,null,1);
        var requests=new ArrayList<ChatCompletionsRequest>();
        for(int i=0;i<2;i++){var request=ChatCompletionsRequest.newBuilder().setRequestId("concurrent-"+i).setProjectKey("ainovel").setUserId(7).setModel("model").setMaxOutputTokens(100).build();requests.add(request);store.request(run,i,"step-"+i,"MODEL",selected.featureKey(),Map.of("request",i));}
        AtomicInteger sends=new AtomicInteger();var wire=mock(AiValidationMoneyGateway.Transport.class);
        when(wire.prepare(any())).thenAnswer(i->{PrepareCallBudgetRequest r=i.getArgument(0);return PrepareCallBudgetResponse.newBuilder().setBudgetId("budget-"+r.getChat().getRequestId()).setBudgetCredential("ephemeral")
                .setRequestSha256(AiValidationCallBudget.hash(r.getChat())).setMaxProviderAttempts(1).setMaxPromptTokens(1000).setMaxCompletionTokens(100).setExpiresAtEpochMs(System.currentTimeMillis()+60000)
                .setPriceQuote(BudgetPriceQuote.newBuilder().setCurrency("CNY").setPriceVersionId(1).setPriceSha256("a".repeat(64)).setMaxCost("2.000000")).build();});
        when(wire.send(any())).thenAnswer(i->{sends.incrementAndGet();throw new IllegalStateException("sent outcome unknown fixture");});
        when(wire.status(any())).thenAnswer(i->{GetBudgetCallStatusRequest r=i.getArgument(0);var original=requests.stream().filter(x->x.getRequestId().equals(r.getRequestId())).findFirst().orElseThrow();return GetBudgetCallStatusResponse.newBuilder().setBudgetId(r.getBudgetId()).setRequestSha256(AiValidationCallBudget.hash(original)).setState(BudgetCallState.BUDGET_CALL_STATE_RECONCILIATION_REQUIRED).build();});
        var start=new CountDownLatch(1);var outcomes=new ArrayList<String>();
        try(var pool=Executors.newFixedThreadPool(2)){var tasks=new ArrayList<Future<String>>();for(int i=0;i<2;i++){final int index=i;tasks.add(pool.submit(()->{start.await();try{money.owner(run,"step-"+index,selected).call(requests.get(index),wire,null);return "unexpected";}catch(AiRequestNotSentException denied){return denied.getMessage();}catch(AiResultUncertainException pending){return "UNKNOWN";}}));}start.countDown();for(var result:tasks)outcomes.add(result.get(30,TimeUnit.SECONDS));}
        assertEquals(Set.of("UNKNOWN","WRITING_AGENT_BUDGET_EXHAUSTED"),new HashSet<>(outcomes));assertEquals(1,sends.get());
        assertEquals(0,new BigDecimal("2").compareTo(jdbc.queryForObject("select sum(reserved_cny) from writing_agent_steps where run_id=?",BigDecimal.class,bytes(run))));
        assertEquals(1,jdbc.queryForObject("select count(*) from writing_agent_steps where run_id=? and gateway_request_base64 is not null",Integer.class,bytes(run)));
    }
    @Test void servicePreviewMakesNoInferenceAndInitialPromptContainsNoWorldFullText() {
        var access=mock(com.ainovel.app.security.ResourceAccessGuard.class);var contents=mock(ManuscriptContentService.class);var context=mock(AssistantContext.class);var generation=mock(SceneGenerationService.class);var assembly=mock(PromptAssemblyService.class);var ai=mock(AiService.class);
        String sourceHtml=frozen().document().sourceHtml();
        when(access.requireOwnedManuscript(fixture.manuscript().getId(),fixture.user())).thenReturn(fixture.manuscript());when(contents.readScene(any(),eq(fixture.scene()))).thenReturn(sourceHtml);
        var source=new AssistantDtos.Source("world:secret","WORLD_RULE","灯塔","WORLD_SECRET_FULL_BODY",null,"hash",false,null);
        when(context.freeze(any(),any(),any(),anyString(),anyString())).thenReturn(new AssistantContext.Snapshot(false,null,List.of(),List.of(source),List.of(source),1,5000));
        when(generation.languageSelection(any())).thenReturn(new LanguageStandard.Selection(LanguageStandard.VERSION,false,false));when(generation.planningHash(any())).thenReturn("planning");
        when(assembly.assembleSceneDraft(any())).thenReturn(new AssembledPrompt(List.of(new AiChatRequest.Message("system","冻结起草规则")),24000));
        var worldTools=worlds();var prompts=new WritingAgentPrompts(json);
        var service=new WritingAgentService(store,worldTools,new CandidateAgentTools(json,20000,100000,24000),prompts,access,mock(com.ainovel.app.manuscript.repo.ManuscriptRepository.class),contents,context,mock(com.ainovel.app.narrative.NarrativeContextService.class),generation,assembly,mock(com.ainovel.app.quality.SlopPatternSamplingService.class),ai,mock(com.ainovel.app.integration.AiGatewayGrpcClient.class),money,mock(com.ainovel.app.economy.EconomyService.class),mock(AiModelConfigurationService.class),mock(com.ainovel.app.aioperation.AiOperationService.class),mock(ManuscriptService.class),mock(com.ainovel.app.v2.V2VersionPersistenceService.class),mock(org.springframework.context.ApplicationEventPublisher.class));
        var request=new Request(fixture.manuscript().getCurrentBranchId(),0L,frozen().document().sourceHtml(),null,"完善动作","EDIT","fast",false,false,new BigDecimal("2"),10L,Limits.defaults(),null,null);
        var preview=service.preview(fixture.user(),fixture.manuscript().getId(),fixture.scene(),request);assertTrue(preview.tools().contains("world_search"));
        var f=service.prepare(fixture.user(),fixture.manuscript().getId(),fixture.scene(),request);
        String initial=store.write(prompts.initial(f,json.createArrayNode()));assertFalse(initial.contains("WORLD_SECRET_FULL_BODY"));assertFalse(initial.contains("WORLD_SECRET_OVERVIEW"));assertTrue(f.context().editPool().isEmpty());
        var dirty=new Request(request.branchId(),request.expectedVersion(),sourceHtml+"<p>未保存补充</p>",null,"完善动作","EDIT","fast",false,false,new BigDecimal("2"),10L,Limits.defaults(),null,null);
        assertEquals("WRITING_AGENT_SAVE_REQUIRED",assertThrows(com.ainovel.app.common.ApiStatusException.class,()->service.preview(fixture.user(),fixture.manuscript().getId(),fixture.scene(),dirty)).getMessage());
        when(context.freeze(any(),any(),any(),anyString(),anyString())).thenReturn(new AssistantContext.Snapshot(true,null,List.of(),List.of(),List.of(source),1,5000));
        var qa=new Request(request.branchId(),request.expectedVersion(),dirty.sourceHtml(),null,"解释草稿","AUTHOR_QA","fast",false,false,new BigDecimal("2"),10L,Limits.defaults(),null,null);
        var qaPreview=service.preview(fixture.user(),fixture.manuscript().getId(),fixture.scene(),qa);assertTrue(qaPreview.h2());assertTrue(qaPreview.tools().contains("world_read"));assertTrue(qaPreview.tools().stream().noneMatch(name->name.startsWith("candidate_")));
        verifyNoInteractions(ai);assertEquals(0,jdbc.queryForObject("select count(*) from writing_agent_runs where manuscript_id=?",Integer.class,bytes(f.manuscriptId())));
    }
    @Test void authorAnswerSurvivesProgressAndReplayContinuesWithoutRepeatingTheFirstModel() {
        var f=frozen();var node=(com.fasterxml.jackson.databind.node.ObjectNode)json.valueToTree(f);node.put("intent","AUTHOR_QA");
        var selected=new AiModelSelection("writing_agent.control","option","model","Model","OFF",null,false,true,"FIXED",1L,null,null,1);
        node.set("selections",json.valueToTree(new AiSelectionContext.Snapshot("agent-run",Map.of(selected.featureKey(),selected))));
        var candidateTools=new CandidateAgentTools(json,20000,100000,24000);var prompts=new WritingAgentPrompts(json);
        var first=store.read(node.toString(),Frozen.class);node.put("toolsJson",prompts.tools(first));node.set("controlMessages",json.valueToTree(prompts.initial(first,json.createArrayNode())));node.put("candidateTemplateHash",candidateTools.templateHash());
        var frozen=store.read(node.toString(),Frozen.class);UUID run=store.isolated(()->store.create(fixture.user().getId(),frozen,"resume-script","hash"));
        UUID operation=UUID.randomUUID();store.attach(run,operation);
        var service=mock(WritingAgentService.class,CALLS_REAL_METHODS);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"store",store);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"worlds",worlds());
        org.springframework.test.util.ReflectionTestUtils.setField(service,"prompts",prompts);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"candidates",candidateTools);
        AtomicInteger executions=new AtomicInteger();
        doAnswer(i->{
            UUID runId=i.getArgument(1);int sequence=i.getArgument(2);String key=i.getArgument(3);AiChatRequest request=i.getArgument(4);
            var step=store.request(runId,sequence,key,"MODEL",request.featureKey(),new ModelRecord(request,request.featureKey()));
            if(step.result()!=null)return store.read(step.result(),AiChatResponse.class);
            int call=executions.incrementAndGet();String name=call==1?"ask_author":"finish",id=call==1?"question1":"finish1";
            String arguments=store.write(call==1?Map.of("question","希望保留哪个结尾？"):Map.of("answer","已根据作者答复完成"));
            String raw=store.write(Map.of("message",Map.of("role","assistant","content","","tool_calls",List.of(Map.of("id",id,"type","function","function",Map.of("name",name,"arguments",arguments))),"provider_state","opaque"),"finish_reason","tool_calls"));
            var response=new AiChatResponse("assistant","",new AiUsageDto(10,10,0,0,0),0,null,"model",AiResponseMetadata.reported("tool_calls",0L,true),raw);
            store.result(runId,key,response);return response;
        }).when(service).model(any(),any(),anyInt(),anyString(),any());
        var runner=new WritingAgentRunner(service,store);var execution=new com.ainovel.app.aioperation.AiOperationExecution(operation,fixture.user(),"{}",json,mock(com.ainovel.app.aioperation.AiOperationProgressUpdater.class));
        runner.execute(execution,run);assertEquals("WAITING_AUTHOR",store.run(run).status());assertEquals(1,executions.get());
        var state=(com.fasterxml.jackson.databind.node.ObjectNode)store.tree(store.run(run).state());state.putObject("authorAnswers").put("tool:question1","保留原结尾");store.state(run,"QUEUED",state);
        runner.execute(execution,run);assertEquals("COMPLETED",store.run(run).status());assertEquals(2,executions.get());
        assertEquals("保留原结尾",store.tree(store.run(run).state()).path("authorAnswers").path("tool:question1").asText());
        assertEquals(f.document().sourceHtml(),jsonReadSection());
    }
    @Test void currentReadArbitratesStaleSnapshotDecisionsAndReadOnlyHistoryUsesNoWriteLock() throws Exception {
        UUID run=run();var snapshotReady=new CountDownLatch(2);var proceed=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<String> decision=()->new org.springframework.transaction.support.TransactionTemplate(manager).execute(status->{
                long expected=jdbc.queryForObject("select revision from writing_agent_runs where id=?",Long.class,bytes(run));
                snapshotReady.countDown();try{if(!proceed.await(15,TimeUnit.SECONDS))throw new IllegalStateException("barrier timeout");}catch(InterruptedException e){throw new IllegalStateException(e);}
                store.lock(run);var current=store.run(run);
                if(current.revision()!=expected)return "CONFLICT";
                store.state(run,"PAUSED_USER",Map.of("authorAnswers",Map.of("question","已确认")));return "SAVED";
            });
            var one=pool.submit(decision);var two=pool.submit(decision);assertTrue(snapshotReady.await(15,TimeUnit.SECONDS));proceed.countDown();
            assertEquals(Set.of("SAVED","CONFLICT"),Set.of(one.get(30,TimeUnit.SECONDS),two.get(30,TimeUnit.SECONDS)));
        }
        var read=new org.springframework.transaction.support.TransactionTemplate(manager);read.setReadOnly(true);
        read.executeWithoutResult(status->{
            try{assertTrue(org.springframework.jdbc.datasource.DataSourceUtils.getConnection(data).isReadOnly());}catch(java.sql.SQLException e){throw new IllegalStateException(e);}
            assertEquals("PAUSED_USER",store.run(run).status());assertEquals("已确认",store.tree(store.run(run).state()).path("authorAnswers").path("question").asText());
        });
    }
    String jsonReadSection(){try{return json.readTree(jdbc.queryForObject("select sections_json from manuscripts where id=?",String.class,bytes(fixture.manuscript().getId()))).path(fixture.scene().toString()).asText();}catch(Exception e){throw new IllegalStateException(e);}}
}
