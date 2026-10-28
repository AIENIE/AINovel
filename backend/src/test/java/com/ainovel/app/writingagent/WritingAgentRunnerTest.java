package com.ainovel.app.writingagent;

import com.ainovel.app.ai.dto.*;
import com.ainovel.app.aioperation.*;
import com.ainovel.app.assistant.AssistantContext;
import com.ainovel.app.assistant.AssistantDocumentProjection;
import com.ainovel.app.assistant.AssistantDtos.Source;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.modelconfig.AiSelectionContext;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.user.User;
import com.ainovel.app.world.WorldBindingService;
import com.ainovel.app.world.WorldDraftDtos.*;
import com.ainovel.app.worldcontext.WorldContextDtos.Preferences;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.ainovel.app.writingagent.WritingAgentDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Scripted inference exercises the real loop and pure tools; no gateway, database or paid calls. */
class WritingAgentRunnerTest {
    private static final UUID USER=UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID MANUSCRIPT=UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID SCENE=UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID RUN=UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID OPERATION=UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID ENTRY=UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final String SECRET="未读取的世界秘密正文与别名";
    private static final String RULE="盐港夜间航行必须在两天内返航。";
    private static final String PRIVATE="private-provider-thinking-must-not-be-public";
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();

    @Test void dynamicallyReadsThenDraftsChecksAndRefinesWithoutSavingFormalProse() {
        var f=fixture(false,"EDIT",Limits.defaults());String run=f.frozen.document().runs().getFirst().runId();
        f.service.script.addAll(List.of(
                call("search","world_search",Map.of("query","港则")),call("read","world_read",Map.of("sourceId","entry:"+ENTRY)),
                call("draft","candidate_draft",Map.of()),candidate(run,"盐港入夜了。"),
                call("check","candidate_check_language",Map.of()),plain("{\"complete\":true,\"summary\":\"发现表达疑点\",\"issues\":[{\"kind\":\"LANGUAGE\",\"category\":\"AWKWARD\",\"quote\":\"盐港入夜了。\",\"impact\":\"请核对表达\",\"direction\":\"改为自然完整句子\"}]}"),
                call("refine","candidate_refine",Map.of("instruction","改为自然完整句子，保留事件")),candidate(run,"夜色落在盐港的水面上。"),call("finish","finish",Map.of("answer","已根据航行规则完成候选，并保留作者审阅"))));
        String originalFrozen=f.store.current.frozen();new WritingAgentRunner(f.service,f.store).execute(f.operation(),RUN);
        assertThat(f.store.current.status()).isEqualTo("COMPLETED");assertThat(f.service.sent).hasSize(9);
        assertThat(f.service.sent.getFirst().messages().toString()).doesNotContain(SECRET,RULE);
        assertThat(f.service.sent.stream().filter(r->"scene.fast.draft".equals(r.featureKey())).findFirst().orElseThrow().messages().toString()).contains(RULE).doesNotContain(SECRET);
        assertThat(f.service.sent.stream().filter(r->"writing_agent.refine".equals(r.featureKey())).findFirst().orElseThrow().messages().toString()).contains("盐港入夜了。","改为自然完整句子",RULE);
        assertThat(f.service.sent.getFirst().context().toString()).doesNotContain(RULE,SECRET);
        var draft=f.service.sent.stream().filter(r->"scene.fast.draft".equals(r.featureKey())).findFirst().orElseThrow();
        assertThat(draft.context().path("writingAgentSources").toString()).contains(RULE).doesNotContain(SECRET);
        var language=f.service.sent.stream().filter(r->"language.diagnose".equals(r.featureKey())).findFirst().orElseThrow();
        assertThat(language.context().path("writingAgentSources").toString()).contains("盐港入夜了。").doesNotContain(RULE,SECRET);
        var checkpoints=f.store.journal.values().stream().filter(s->s.kind().equals("TOOL")).map(s->f.store.read(s.result(),WritingAgentRunner.ToolCheckpoint.class)).toList();
        assertThat(checkpoints.stream().map(c->c.output().path("candidateId").asText()).filter(s->!s.isEmpty()).distinct()).hasSize(2);
        assertThat(checkpoints.getLast().candidate().parentId()).isNotNull();
        assertThat(checkpoints.getLast().candidate().html()).contains("夜色落在盐港的水面上。");
        assertThat(f.store.current.frozen()).isEqualTo(originalFrozen);
        assertThat(f.store.sql).noneMatch(sql->sql.startsWith("update manuscript")||sql.startsWith("insert into manuscript"));
        assertThat(f.store.write(f.service.get(f.user,MANUSCRIPT,SCENE,RUN))).doesNotContain(PRIVATE,"opaque-provider-state");
    }

    @Test void replaysToolCheckpointAfterWorkerModelWithoutReusingItsSequenceOrCallingAgain() {
        recoveryAt("tool:draft");
    }

    @Test void cglibServiceProxyRunsWorldAndCandidateToolsThroughTargetAccessors() throws Exception {
        var f=fixture(false,"EDIT",Limits.defaults());String run=f.frozen.document().runs().getFirst().runId();
        f.service.script.addAll(List.of(call("read","world_read",Map.of("sourceId","entry:"+ENTRY)),
                call("draft","candidate_draft",Map.of()),candidate(run,"经代理读取航行规则后的候选。"),call("finish","finish",Map.of("answer","交付作者"))));
        var invoked=new ArrayList<String>();var factory=new ProxyFactory(f.service);factory.setProxyTargetClass(true);
        factory.addAdvice((org.aopalliance.intercept.MethodInterceptor)invocation->{invoked.add(invocation.getMethod().getName());return invocation.proceed();});
        var proxied=(WritingAgentService)factory.getProxy();
        assertThat(AopUtils.isCglibProxy(proxied)).isTrue();
        var proxyField=WritingAgentService.class.getDeclaredField("candidates");proxyField.setAccessible(true);
        assertThat(proxyField.get(proxied)).isNull();

        new WritingAgentRunner(proxied,f.store).execute(f.operation(),RUN);

        assertThat(f.store.current.status()).isEqualTo("COMPLETED");
        assertThat(f.service.sent).hasSize(4);
        assertThat(invoked).contains("worlds","candidates","model");
        assertThat(f.service.sent.stream().filter(r->"scene.fast.draft".equals(r.featureKey())).findFirst().orElseThrow().messages().toString()).contains(RULE);
    }
    @Test void replaysAStoredWorkerResponseWhenBusinessCheckpointWasNeverWritten() {
        recoveryAt("worker:1");
    }
    private void recoveryAt(String crashKey) {
        var f=fixture(false,"EDIT",Limits.defaults());String run=f.frozen.document().runs().getFirst().runId();
        f.service.script.addAll(List.of(call("draft","candidate_draft",Map.of()),candidate(run,"已保存的模型候选。"),call("finish","finish",Map.of("answer","交付作者"))));
        f.store.crashAfterResult=crashKey;
        assertThatThrownBy(()->new WritingAgentRunner(f.service,f.store).execute(f.operation(),RUN)).isInstanceOf(SimulatedRestart.class);
        assertThat(f.service.sent).hasSize(2);
        new WritingAgentRunner(f.service,f.store).execute(f.operation(),RUN);
        assertThat(f.store.current.status()).isEqualTo("COMPLETED");assertThat(f.service.sent).hasSize(3);
        assertThat(f.store.step(RUN,"control:1").sequence()).isEqualTo(1);
        assertThat(f.store.step(RUN,"tool:draft").sequence()).isEqualTo(2);
        assertThat(f.store.step(RUN,"worker:1").sequence()).isEqualTo(3);
        assertThat(f.store.step(RUN,"control:2").sequence()).isEqualTo(4);
        assertThat(f.store.journal.values().stream().map(WritingAgentStore.Step::sequence)).doesNotHaveDuplicates();
        assertThat(f.store.tree(f.store.current.state()).path("modelCalls").asInt()).isEqualTo(3);
    }

    @Test void authorQuestionResumesFromItsOriginalJournalAndRetainsAnswerAcrossStateUpdates() {
        var f=fixture(false,"AUTHOR_QA",Limits.defaults());f.service.script.addAll(List.of(call("ask","ask_author",Map.of("question","希望解释哪条航行限制？")),call("finish","finish",Map.of("answer","仅向作者解释，不修改正文"))));
        new WritingAgentRunner(f.service,f.store).execute(f.operation(),RUN);
        assertThat(f.store.current.status()).isEqualTo("WAITING_AUTHOR");assertThat(f.service.sent).hasSize(1);
        f.service.control(f.user,MANUSCRIPT,SCENE,RUN,"resume",new Revision(f.store.current.revision(),"解释返航两天的规则"),"resume-original-question");
        new WritingAgentRunner(f.service,f.store).execute(f.operation(),RUN);
        assertThat(f.store.current.status()).isEqualTo("COMPLETED");assertThat(f.service.sent).hasSize(2);
        assertThat(f.service.sent.getLast().messages().toString()).contains("解释返航两天的规则");
        assertThat(f.store.tree(f.store.current.state()).path("authorAnswers").path("tool:ask").asText()).isEqualTo("解释返航两天的规则");
        assertThat(f.store.journal.values()).noneMatch(s->s.key().startsWith("worker:"));
    }

    @Test void h2WorldToolViolationPausesWithoutFeedingForbiddenToolHistoryIntoAnotherCall() {
        var f=fixture(true,"EDIT",Limits.defaults());
        f.service.script.add(call("world","world_read",Map.of("sourceId","entry:"+ENTRY)));
        new WritingAgentRunner(f.service,f.store).execute(f.operation(),RUN);
        assertThat(f.store.current.status()).isEqualTo("PAUSED_INPUT");assertThat(f.service.sent).hasSize(1);
        assertThat(f.service.sent).allSatisfy(request->{assertThat(request.messages().toString()).doesNotContain(SECRET,RULE,"未分类世界名称","作者专用秘密资料");});
        assertThat(f.service.sent.getFirst().toolsJson()).doesNotContain("world_read","world_search","world_related");
        assertThat(f.store.tree(f.store.current.state()).path("answer").asText()).isEqualTo("H2_WORLD_TOOL_UNAVAILABLE");
        assertThat(f.store.journal.values()).noneMatch(step->step.kind().equals("TOOL"));
    }

    @Test void aDirectClarifyingReplyCanResumeWithoutReissuingTheOriginalRequest() {
        var f=fixture(false,"EDIT",Limits.defaults());String run=f.frozen.document().runs().getFirst().runId();
        var envelope=json.createObjectNode();envelope.set("message",json.createObjectNode().put("role","assistant").put("content","希望这一场以谁的视角展开？"));envelope.put("finish_reason","stop");
        f.service.script.add(new AiChatResponse("assistant","希望这一场以谁的视角展开？",new AiUsageDto(10,5,0,0,1),100,null,"fixture",null,write(envelope)));
        f.service.script.addAll(List.of(call("draft","candidate_draft",Map.of()),candidate(run,"码头灯下，他握住了船票。"),call("finish","finish",Map.of("answer","完成候选"))));
        new WritingAgentRunner(f.service,f.store).execute(f.operation(),RUN);
        assertThat(f.store.current.status()).isEqualTo("WAITING_AUTHOR");assertThat(f.service.sent).hasSize(1);
        f.service.control(f.user,MANUSCRIPT,SCENE,RUN,"resume",new Revision(f.store.current.revision(),"沿用当前人物的有限视角"),"resume-direct-reply");
        new WritingAgentRunner(f.service,f.store).execute(f.operation(),RUN);
        assertThat(f.store.current.status()).isEqualTo("COMPLETED");assertThat(f.service.sent).hasSize(4);
        assertThat(f.service.sent.get(1).messages().toString()).contains("沿用当前人物的有限视角");
        assertThat(f.store.tree(f.store.current.state()).path("authorAnswers").path("control:1").asText()).isEqualTo("沿用当前人物的有限视角");
    }

    @Test void h2ContextReadsCannotReachTheAuthorOnlyPool() {
        var f=fixture(true,"EDIT",Limits.defaults());String run=f.frozen.document().runs().getFirst().runId();
        f.service.script.addAll(List.of(call("author","context_read",Map.of("sourceId","author-secret")),call("draft","candidate_draft",Map.of()),candidate(run,"仅按受控资料写出的候选。"),call("finish","finish",Map.of("answer","完成受控候选"))));
        new WritingAgentRunner(f.service,f.store).execute(f.operation(),RUN);
        assertThat(f.store.current.status()).isEqualTo("COMPLETED");
        assertThat(f.service.sent).allSatisfy(request->assertThat(request.messages().toString()).doesNotContain(SECRET,RULE,"未分类世界名称","作者专用秘密资料"));
        var first=f.store.read(f.store.step(RUN,"tool:author").result(),WritingAgentRunner.ToolCheckpoint.class);
        assertThat(first.output().path("error").asText()).isEqualTo("WRITING_AGENT_SOURCE_UNAVAILABLE");
    }

    @Test void authorQaCandidateToolViolationPausesWithoutStartingAWorker() {
        var f=fixture(false,"AUTHOR_QA",Limits.defaults());f.service.script.add(call("draft","candidate_draft",Map.of()));
        new WritingAgentRunner(f.service,f.store).execute(f.operation(),RUN);
        assertThat(f.store.current.status()).isEqualTo("PAUSED_INPUT");assertThat(f.service.sent).hasSize(1);
        assertThat(f.store.tree(f.store.current.state()).path("answer").asText()).isEqualTo("AUTHOR_QA_READ_ONLY");
        assertThat(f.store.journal.values()).noneMatch(step->step.kind().equals("TOOL"));
    }

    @Test void globalModelLimitPausesBeforeStartingAnotherPaidTool() {
        var f=fixture(false,"EDIT",new Limits(1,1,1,3,32));f.service.script.add(call("draft","candidate_draft",Map.of()));
        new WritingAgentRunner(f.service,f.store).execute(f.operation(),RUN);
        assertThat(f.store.current.status()).isEqualTo("PAUSED_BUDGET");assertThat(f.service.sent).hasSize(1);
        assertThat(f.store.journal.values()).noneMatch(s->s.key().startsWith("worker:"));
    }

    @Test void pauseAtWorkerPreflightResumesTheStoredRequestWithoutReissuingControlOrDuplicatingSteps() {
        var f=fixture(false,"EDIT",Limits.defaults());String run=f.frozen.document().runs().getFirst().runId();
        f.service.script.addAll(List.of(call("draft","candidate_draft",Map.of()),candidate(run,"暂停恢复后的首次候选。"),call("finish","finish",Map.of("answer","交付作者"))));
        var pauseOnce=new java.util.concurrent.atomic.AtomicBoolean(true);
        f.service.beforeSend=request->{
            if("scene.fast.draft".equals(request.featureKey())&&pauseOnce.getAndSet(false)){
                f.store.state(RUN,"PAUSE_REQUESTED",Map.of("message","作者在工具受理前暂停"));
                throw new com.ainovel.app.ai.AiRequestNotSentException(org.springframework.http.HttpStatus.PRECONDITION_FAILED,"WRITING_AGENT_PAUSED");
            }
        };

        new WritingAgentRunner(f.service,f.store).execute(f.operation(),RUN);
        assertThat(f.store.current.status()).isEqualTo("PAUSED_USER");
        assertThat(f.service.sent).hasSize(1);
        assertThat(f.service.script).hasSize(2);
        assertThat(f.store.step(RUN,"control:1").result()).isNotNull();
        assertThat(f.store.step(RUN,"tool:draft").result()).isNull();
        var originalWorker=f.store.step(RUN,"worker:1");
        assertThat(originalWorker.result()).isNull();
        assertThat(f.store.sql).noneMatch(sql->sql.startsWith("insert into narrative_generation_candidates"));

        f.service.control(f.user,MANUSCRIPT,SCENE,RUN,"resume",new Revision(f.store.current.revision(),null),"resume-preflight-pause");
        new WritingAgentRunner(f.service,f.store).execute(f.operation(),RUN);

        assertThat(f.store.current.status()).isEqualTo("COMPLETED");
        assertThat(f.service.sent).hasSize(3);
        assertThat(f.service.sent.stream().filter(request->"scene.fast.draft".equals(request.featureKey()))).hasSize(1);
        var replayedWorker=f.store.step(RUN,"worker:1");
        assertThat(replayedWorker.id()).isEqualTo(originalWorker.id());
        assertThat(replayedWorker.request()).isEqualTo(originalWorker.request());
        assertThat(replayedWorker.requestHash()).isEqualTo(originalWorker.requestHash());
        assertThat(replayedWorker.result()).isNotNull();
        assertThat(f.store.journal).hasSize(5);
        assertThat(f.store.journal.values().stream().map(WritingAgentStore.Step::sequence)).doesNotHaveDuplicates();
        assertThat(f.store.step(RUN,"control:1").sequence()).isEqualTo(1);
        assertThat(replayedWorker.sequence()).isEqualTo(3);
        assertThat(f.store.step(RUN,"control:2").sequence()).isEqualTo(4);
    }

    @ParameterizedTest
    @CsvSource({"WRITING_AGENT_BUDGET_PREPARE_GRPC_INTERNAL,PAUSED_INPUT","WRITING_AGENT_BUDGET_PREPARE_UNAVAILABLE,PAUSED_INPUT","WRITING_AGENT_BUDGET_GRANT_INVALID,PAUSED_INPUT","WRITING_AGENT_REQUEST_CHANGED,PAUSED_INPUT","WRITING_AGENT_BUDGET_EXHAUSTED,PAUSED_BUDGET","AI_VALIDATION_PHASE_BUDGET_EXHAUSTED,PAUSED_BUDGET","WRITING_AGENT_CALL_LIMIT,PAUSED_BUDGET"})
    void aPreflightFailureOnlyUsesBudgetPauseForAnActualBound(String code,String expectedStatus) {
        var f=fixture(false,"AUTHOR_QA",Limits.defaults());
        f.service.beforeSend=request->{throw new com.ainovel.app.ai.AiRequestNotSentException(org.springframework.http.HttpStatus.PRECONDITION_FAILED,code);};
        new WritingAgentRunner(f.service,f.store).execute(f.operation(),RUN);
        assertThat(f.store.current.status()).isEqualTo(expectedStatus);assertThat(f.service.sent).isEmpty();
        assertThat(f.store.tree(f.store.current.state()).path("answer").asText()).isEqualTo(code);
        assertThat(f.store.step(RUN,"control:1").result()).isNull();
    }

    @Test void cancellationDuringAModelCallPreventsFurtherToolOrModelDispatch() {
        var f=fixture(false,"EDIT",Limits.defaults());f.service.script.add(call("search","world_search",Map.of("query","港则")));
        f.service.afterSend=ignored->f.store.state(RUN,"CANCELLED",Map.of("message","作者结束任务"));
        Object result=new WritingAgentRunner(f.service,f.store).execute(f.operation(),RUN);
        assertThat(f.store.current.status()).isEqualTo("CANCELLED");assertThat(f.service.sent).hasSize(1);
        assertThat(f.store.write(result)).contains("CANCELLED");assertThat(f.store.journal.values()).noneMatch(s->s.kind().equals("TOOL"));
    }

    @Test void anUnavailableHistoricalCandidateTemplateStopsWithoutRebuildingOrCalling() {
        var f=fixture(false,"EDIT",Limits.defaults());
        var old=(ObjectNode)f.store.tree(f.store.current.frozen());old.put("candidateTemplateHash","unavailable-historical-template");f.store.replaceFrozen(f.store.write(old));
        new WritingAgentRunner(f.service,f.store).execute(f.operation(),RUN);
        assertThat(f.store.current.status()).isEqualTo("PAUSED_INPUT");assertThat(f.service.sent).isEmpty();assertThat(f.store.journal).isEmpty();
        assertThat(f.store.tree(f.store.current.state()).path("answer").asText()).isEqualTo("WRITING_AGENT_PROTOCOL_UNAVAILABLE");
    }

    @Test void reusingAToolCallIdentityForAnotherActionCannotReplayAnUnrelatedReceipt() {
        var f=fixture(false,"AUTHOR_QA",Limits.defaults());
        f.service.script.addAll(List.of(call("reused","world_search",Map.of("query","港则")),call("reused","world_read",Map.of("sourceId","entry:"+ENTRY))));
        new WritingAgentRunner(f.service,f.store).execute(f.operation(),RUN);
        assertThat(f.store.current.status()).isEqualTo("PAUSED_INPUT");assertThat(f.service.sent).hasSize(2);
        assertThat(f.store.tree(f.store.current.state()).path("answer").asText()).isEqualTo("WRITING_AGENT_REQUEST_CHANGED");
        assertThat(f.store.step(RUN,"tool:reused").feature()).isEqualTo("world_search");
    }

    private Fixture fixture(boolean h2,String intent,Limits limits) {
        var store=new MemoryStore(json);var worlds=new WorldAgentTools(json,null,null);var candidates=new CandidateAgentTools(json,20000,100000,24000);var prompts=new WritingAgentPrompts(json);
        var access=mock(ResourceAccessGuard.class);when(access.requireOwnedManuscript(any(),any())).thenReturn(mock(Manuscript.class));
        var operations=mock(AiOperationService.class);when(operations.submit(any(),anyString(),anyString(),any(),any(),anyInt(),anyString(),anyString())).thenReturn(new AiOperationDtos.Accepted(OPERATION));
        var service=new ScriptedService(store,worlds,candidates,prompts,access,operations);
        var user=new User();user.setId(USER);
        var content=new Content(h2?"未分类世界名称":"群岛世界","简介",List.of(),"创作意图","作者备注",List.of(new Entry(ENTRY,"RULE","航行规则","简要规则",RULE,List.of("港则"),List.of("航行"),null,null)),List.of(),Map.of(),null,SECRET);
        var binding=new WorldBindingService.Resolved(ENTRY,MANUSCRIPT,"STORY",MANUSCRIPT,1,content.name(),1,"PUBLISHED","snapshot",null,content);
        var world=worlds.freeze(binding,Preferences.defaults(ENTRY),h2&&intent.equals("EDIT"));
        var base=List.of(new Source(h2?"h2-scene":"outline","H2_SCENE","当前受控场景","码头夜谈，不新增人物知情",null,"base",false,"base"));
        var authorOnly=new Source("author-secret","STORY","作者专用秘密资料",SECRET,null,"secret",false,"author");
        var context=new AssistantContext.Snapshot(h2,null,base,base,List.of(authorOnly),2,500,null);
        var document=AssistantDocumentProjection.project("<p>原稿</p>",null);
        var selections=new AiSelectionContext.Snapshot("fixture",Map.of());
        var node=json.createObjectNode();node.put("protocol",PROTOCOL);node.putPOJO("manuscriptId",MANUSCRIPT);node.putPOJO("branchId",MANUSCRIPT);node.putPOJO("sceneId",SCENE);node.put("sourceVersion",4);node.put("serverHash","original");node.put("instruction","按需查阅规则并完成当前场景候选");node.put("intent",intent);node.put("mode","fast");node.set("document",json.valueToTree(document));node.set("context",json.valueToTree(context));node.set("world",json.valueToTree(world));node.set("base",json.valueToTree(base));node.put("languageRules","");node.set("generationRules",json.valueToTree(List.of(new AiChatRequest.Message("system","冻结场景起草规范：只按实际已读材料写作，保持人物知情。"))));node.put("planningHash","planning");node.put("languageCheck",true);node.put("plotCheck",false);node.put("maxCostCny",5);node.put("maxProjectCredits",100);node.set("limits",json.valueToTree(limits));node.set("selections",json.valueToTree(selections));
        Frozen provisional=json.convertValue(node,Frozen.class);
        node.put("toolsJson",prompts.tools(provisional));node.set("controlMessages",json.valueToTree(prompts.initial(provisional,worlds.requiredReads(world,WorldAgentTools.ReadState.empty()))));
        node.put("candidateTemplateHash",candidates.templateHash());
        var frozen=json.convertValue(node,Frozen.class);
        store.current=new WritingAgentStore.Run(RUN,USER,MANUSCRIPT,MANUSCRIPT,SCENE,"original-key","request-hash","QUEUED",1,OPERATION,store.write(frozen),"{}",new BigDecimal("5.000000"),100,"{}",Instant.parse("2026-10-28T00:00:00Z"));
        return new Fixture(store,service,user,frozen);
    }
    private record Fixture(MemoryStore store,ScriptedService service,User user,Frozen frozen) {
        AiOperationExecution operation(){return new AiOperationExecution(store.current.operationId(),user,"{}",store.mapper(),(label,completed,total)->{});}
    }
    private AiChatResponse call(String id,String name,Map<String,Object> args) {
        var message=json.createObjectNode().put("role","assistant");message.putNull("content");message.put("reasoning_content",PRIVATE);message.put("provider_state","opaque-provider-state");
        message.set("tool_calls",json.valueToTree(List.of(Map.of("id",id,"type","function","function",Map.of("name",name,"arguments",write(args))))));
        var nativeResponse=json.createObjectNode().set("message",message);((ObjectNode)nativeResponse).put("finish_reason","tool_calls");
        return new AiChatResponse("assistant","",new AiUsageDto(10,5,0,0,1),100,null,"fixture",null,write(nativeResponse));
    }
    private AiChatResponse candidate(String run,String paragraph){return plain(write(Map.of("replacements",List.of(Map.of("runId",run,"paragraphs",List.of(paragraph))))));}
    private static AiChatResponse plain(String value){return new AiChatResponse("assistant",value,new AiUsageDto(10,5,0,0,1),100);}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private static final class SimulatedRestart extends Error {}
    static class ScriptedService extends WritingAgentService {
        private final MemoryStore memory;
        final Deque<AiChatResponse> script=new ArrayDeque<>();final List<AiChatRequest> sent=new ArrayList<>();Consumer<AiChatRequest> beforeSend,afterSend;
        ScriptedService(MemoryStore store,WorldAgentTools worlds,CandidateAgentTools candidates,WritingAgentPrompts prompts,ResourceAccessGuard access,AiOperationService operations) {
            super(store,worlds,candidates,prompts,access,null,null,null,null,null,null,null,null,null,null,null,null,operations,null,null,null);
            this.memory=store;
        }
        @Override public AiChatResponse model(User user,UUID runId,int sequence,String key,AiChatRequest request) {
            var step=memory.request(runId,sequence,key,"MODEL",request.featureKey(),new ModelRecord(request,request.featureKey()));
            if(step.result()!=null)return memory.read(step.result(),AiChatResponse.class);
            if(beforeSend!=null)beforeSend.accept(request);
            if(script.isEmpty())throw new AssertionError("Unexpected new model call "+key+" at sequence "+sequence);
            sent.add(request);var result=script.removeFirst();memory.result(runId,key,result);if(afterSend!=null)afterSend.accept(request);return result;
        }
    }
    private static final class MemoryStore extends WritingAgentStore {
        Run current;final Map<String,Step> journal=new LinkedHashMap<>();final List<String> sql=new ArrayList<>();String crashAfterResult;
        MemoryStore(ObjectMapper mapper){super(mock(JdbcTemplate.class),mapper,mock(PlatformTransactionManager.class));
            when(jdbc().update(anyString(),any(Object[].class))).thenAnswer(invocation->{String statement=invocation.getArgument(0);Object[] values=(Object[])invocation.getRawArguments()[1];sql.add(statement);
                if(statement.startsWith("update writing_agent_runs set status=")&&!current.status().equals("CANCELLED"))replace((String)values[0],current.operationId(),(String)values[1],current.decision());
                else if(statement.startsWith("update writing_agent_runs set operation_id="))replace("QUEUED",uuid((byte[])values[0]),current.state(),current.decision());
                else if(statement.startsWith("update writing_agent_runs set decision_json="))replace(current.status(),current.operationId(),current.state(),(String)values[0]);
                return 1;
            });
        }
        private void replace(String status,UUID operation,String state,String decision){current=new Run(current.id(),current.userId(),current.manuscriptId(),current.branchId(),current.sceneId(),current.requestKey(),current.requestHash(),status,current.revision()+1,operation,current.frozen(),state,current.maxCny(),current.maxCredits(),decision,current.createdAt());}
        private void replaceFrozen(String frozen){current=new Run(current.id(),current.userId(),current.manuscriptId(),current.branchId(),current.sceneId(),current.requestKey(),current.requestHash(),current.status(),current.revision(),current.operationId(),frozen,current.state(),current.maxCny(),current.maxCredits(),current.decision(),current.createdAt());}
        @Override public Run run(UUID id){assertThat(id).isEqualTo(RUN);return current;}
        @Override public void lock(UUID id){assertThat(id).isEqualTo(RUN);}
        @Override public <T>T isolated(Supplier<T> work){return work.get();}
        @Override public Step step(UUID run,String key){return journal.get(key);}
        @Override public List<Step> steps(UUID run){return journal.values().stream().sorted(Comparator.comparingInt(Step::sequence)).toList();}
        @Override public Step request(UUID run,int sequence,String key,String kind,String feature,Object request){
            String payload=write(request),fingerprint=hash(payload);var previous=journal.get(key);
            if(previous!=null){assertThat(previous.requestHash()).as("stored request identity for %s",key).isEqualTo(fingerprint);return previous;}
            assertThat(journal.values()).as("journal sequence must not be reused by %s",key).noneMatch(row->row.sequence()==sequence);
            var row=new Step(UUID.randomUUID(),run,sequence,key,kind,feature,fingerprint,payload,null,null,null,null,null,BigDecimal.ZERO,BigDecimal.ZERO,0,0L);journal.put(key,row);return row;
        }
        @Override public void result(UUID run,String key,Object result){
            var old=journal.get(key);if(old.result()==null)journal.put(key,new Step(old.id(),old.runId(),old.sequence(),old.key(),old.kind(),old.feature(),old.requestHash(),old.request(),write(result),old.gatewayRequest(),old.budget(),old.gatewayResult(),old.settlement(),old.reservedCny(),old.settledCny(),old.reservedCredits(),old.settledCredits()));
            if(key.equals(crashAfterResult)){crashAfterResult=null;throw new SimulatedRestart();}
        }
        private static UUID uuid(byte[] value){var bytes=ByteBuffer.wrap(value);return new UUID(bytes.getLong(),bytes.getLong());}
    }
}
