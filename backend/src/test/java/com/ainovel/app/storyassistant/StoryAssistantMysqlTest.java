package com.ainovel.app.storyassistant;

import com.ainovel.app.ai.*;
import com.ainovel.app.ai.dto.*;
import com.ainovel.app.aioperation.*;
import com.ainovel.app.common.*;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.story.model.*;
import com.ainovel.app.story.repo.*;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.*;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.ainovel.app.material.MaterialFingerprintService.bytes;
import static com.ainovel.app.storyassistant.StoryAssistantDtos.*;

/** Opt-in disposable MySQL only. AI calls are mocked; no live model is exercised. */
@EnabledIfEnvironmentVariable(named="AIENIE_AUDIT_MYSQL_URL",matches="jdbc:mysql:.*")
@DataJpaTest(showSql=false,properties={"spring.flyway.enabled=true","spring.jpa.hibernate.ddl-auto=none","spring.jpa.open-in-view=false"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
@Import({StoryAssistantStore.class,StoryAssistantResources.class,StoryAssistantContext.class,StoryAssistantService.class,
        ResourceAccessGuard.class,StoryAssistantMysqlTest.Beans.class})
class StoryAssistantMysqlTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties){
        String url=System.getenv("AIENIE_AUDIT_MYSQL_URL");
        if(url==null||!url.matches("jdbc:mysql://[^/]+/aienie_novel_audit_test_[A-Za-z0-9_]+(?:\\?.*)?"))throw new IllegalArgumentException("Isolated schema required");
        properties.add("spring.datasource.url",()->url);properties.add("spring.datasource.username",()->System.getenv("AIENIE_AUDIT_MYSQL_USERNAME"));
        properties.add("spring.datasource.password",()->System.getenv("AIENIE_AUDIT_MYSQL_PASSWORD"));properties.add("spring.datasource.driver-class-name",()->"com.mysql.cj.jdbc.Driver");
        properties.add("spring.jpa.properties.hibernate.dialect",()->"org.hibernate.dialect.MySQLDialect");
    }
    @TestConfiguration static class Beans {
        @Bean ObjectMapper mapper(){return new ObjectMapper().findAndRegisterModules();}
        @Bean TransactionTemplate transactions(PlatformTransactionManager manager){return new TransactionTemplate(manager);}
    }
    @MockitoBean AiService ai;
    @MockitoBean AiAdmissionGuard admission;
    @MockitoBean AiOperationService operations;
    @MockitoBean CurrentUserResolver currentUser;
    @Autowired StoryAssistantService service;
    @Autowired StoryAssistantStore store;
    @Autowired StoryAssistantResources resources;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired OutlineRepository outlines;
    @Autowired CharacterCardRepository characters;
    @PersistenceContext EntityManager em;

    @BeforeEach void mockedOperations(){
        when(operations.submit(any(),anyString(),anyString(),any(),any(),anyInt(),anyString(),anyString())).thenAnswer(i->new AiOperationDtos.Accepted(UUID.randomUUID()));
        when(operations.get(any(),any())).thenAnswer(i->new AiOperationDtos.Progress(i.getArgument(1),StoryAssistantService.TYPE,"STORY_ASSISTANT_SESSION",null,AiOperationStatus.SUCCEEDED,"完成",2,2,0,0,false,1,null,null,null,null,null));
    }
    private record Fixture(User user,UUID story,UUID outline,UUID chapter,UUID scene,UUID scene2,UUID character,UUID session) {}
    private Fixture fixture(){
        var f=tx.execute(s->{
            var user=new User();String name="story-assistant-"+UUID.randomUUID();user.setUsername(name);user.setEmail(name+"@example.invalid");user.setPasswordHash("test");em.persist(user);
            var story=new Story();story.setUser(user);story.setTitle("夜访故事");story.setSynopsis("林青追寻戏票的来源");story.setStatus("draft");em.persist(story);
            var card=new CharacterCard();card.setStory(story);card.setName("林青");card.setDetails("旧动机");card.setRelationships("保留关系");em.persist(card);
            UUID chapter=UUID.randomUUID(),scene=UUID.randomUUID(),scene2=UUID.randomUUID();
            var outline=new Outline();outline.setStory(story);outline.setTitle("夜访大纲");outline.setContentJson(store.write(Map.of("planning",Map.of("corePromise","真相"),"chapters",List.of(Map.of("id",chapter,"title","旧章名","summary","章摘要","order",1,"planning",Map.of(),"scenes",List.of(
                    Map.of("id",scene,"title","敲门","summary","第一场","order",1,"content","旧大纲content保留","planning",Map.of()),
                    Map.of("id",scene2,"title","询问","summary","第二场","order",2,"content","另一场保留","planning",Map.of())))))));em.persist(outline);em.flush();
            return new Fixture(user,story.getId(),outline.getId(),chapter,scene,scene2,card.getId(),null);
        });
        var session=service.createSession(f.user(),f.story(),new SessionRequest("人物动机"),"session-create");return new Fixture(f.user(),f.story(),f.outline(),f.chapter(),f.scene(),f.scene2(),f.character(),session.id());
    }
    private Accepted submit(Fixture f,String intent,String instruction,List<UUID> sources){return service.submit(f.user(),f.story(),new TurnRequest(f.session(),intent,instruction,f.outline(),sources,null,null),UUID.randomUUID().toString());}
    private AiChatResponse response(Object body){return new AiChatResponse("assistant",store.write(body),new AiUsageDto(10,20,0,0,1),99);}
    private void execute(Fixture f,Accepted a){service.execute(new AiOperationExecution(a.operationId(),f.user(),"{}",json,(l,c,t)->{}),a.turnId());}
    private Map<String,Object> item(String action,String type,UUID target,UUID parent,Object fields){var m=new LinkedHashMap<String,Object>();m.put("action",action);m.put("resourceType",type);if(target!=null)m.put("targetId",target);if(parent!=null)m.put("parentId",parent);m.put("fields",fields);m.put("rationale","根据作者当前决定整理");return m;}
    private List<UUID> propose(Fixture f,List<Map<String,Object>> items){
        var a=submit(f,"PROPOSE","林青 敲门 询问 旧章名",List.of());when(ai.chatWithIdempotency(any(),any(),anyString())).thenReturn(response(Map.of("kind","PROPOSALS","answer","整理提案","items",items)));execute(f,a);return service.turn(f.user(),f.story(),a.turnId()).proposalIds();
    }
    private DecisionResult decide(Fixture f,UUID id,String action,String key){var p=service.proposal(f.user(),f.story(),id);return service.decide(f.user(),f.story(),id,action,new DecisionRequest(p.revision(),p.resourceRevision()),key);}
    private JsonNode outline(Fixture f){return tx.execute(s->{try{return json.readTree(outlines.findById(f.outline()).orElseThrow().getContentJson());}catch(Exception e){throw new IllegalStateException(e);}});}

    @Test void sessionsSuggestionsAndExplicitProposalStaySeparateAndReplay(){
        var f=fixture();var same=service.createSession(f.user(),f.story(),new SessionRequest("人物动机"),"session-create");assertThat(same.id()).isEqualTo(f.session());
        var other=service.createSession(f.user(),f.story(),new SessionRequest("章节方向"),"session-second");assertThat(service.session(f.user(),f.story(),null,null,20).id()).isEqualTo(other.id());
        service.renameSession(f.user(),f.story(),f.session(),new SessionRequest("动机定稿"),"rename-session");assertThat(service.sessions(f.user(),f.story()).getFirst().title()).isEqualTo("动机定稿");
        var req=new TurnRequest(f.session(),"DISCUSS","林青为什么去敲门",f.outline(),List.of(),null,null);
        var a=service.submit(f.user(),f.story(),req,"discuss-once");assertThat(service.submit(f.user(),f.story(),req,"discuss-once")).isEqualTo(a);
        when(ai.chatWithIdempotency(any(),any(),anyString())).thenReturn(response(Map.of("kind","ANSWER","answer","可以先讨论她的承诺。","suggestions",List.of(Map.of("title","整理角色动机","reason","作者已经讨论清楚")))));execute(f,a);
        var turn=service.turn(f.user(),f.story(),a.turnId());assertThat(turn.suggestions()).hasSize(1);assertThat(turn.proposalIds()).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from story_assistant_proposals where turn_id=?",Integer.class,bytes(a.turnId()))).isZero();
        assertThat(tx.<String>execute(s->characters.findById(f.character()).orElseThrow().getDetails())).isEqualTo("旧动机");
        assertThatThrownBy(()->service.submit(f.user(),f.story(),new TurnRequest(other.id(),"PROPOSE","整理",f.outline(),List.of(a.turnId()),turn.suggestions().getFirst().id(),null),"cross-session")).isInstanceOf(ApiStatusException.class).hasMessage("STORY_ASSISTANT_SOURCES_INVALID");
    }
    @Test void resourceCandidateEditAcceptUndoAndReceiptsAreAtomic(){
        var f=fixture();UUID id=propose(f,List.of(item("UPDATE","CHARACTER",f.character(),null,Map.of("details","模型新动机")))).getFirst();
        var p=service.proposal(f.user(),f.story(),id);var edited=service.edit(f.user(),f.story(),id,new EditRequest(p.revision(),json.valueToTree(Map.of("details","作者修订动机"))),"edit-once");
        assertThat(edited.modelCandidate().path("details").asText()).isEqualTo("模型新动机");assertThat(edited.candidate().path("details").asText()).isEqualTo("作者修订动机");
        var req=new DecisionRequest(edited.revision(),edited.resourceRevision());
        assertThatThrownBy(()->tx.executeWithoutResult(s->{service.decide(f.user(),f.story(),id,"accept",req,"rollback-accept");throw new IllegalStateException("rollback");})).hasMessage("rollback");
        assertThat(service.proposal(f.user(),f.story(),id).status()).isEqualTo("READY");assertThat(tx.<String>execute(s->characters.findById(f.character()).orElseThrow().getDetails())).isEqualTo("旧动机");
        assertThat(store.receipt(f.story(),"proposal:"+id,"rollback-accept")).isNull();
        var saved=service.decide(f.user(),f.story(),id,"accept",req,"accept-once");assertThat(service.decide(f.user(),f.story(),id,"accept",req,"accept-once")).isEqualTo(saved);
        assertThat(saved.proposal().undoAllowed()).isTrue();var undoReq=new DecisionRequest(saved.proposal().revision(),saved.resourceRevision());
        var undone=service.decide(f.user(),f.story(),id,"undo",undoReq,"undo-once");assertThat(service.decide(f.user(),f.story(),id,"undo",undoReq,"undo-once")).isEqualTo(undone);
        assertThat(tx.<String>execute(s->characters.findById(f.character()).orElseThrow().getDetails())).isEqualTo("旧动机");
    }
    @Test void laterManualEditBlocksAcceptAndUndoWithoutOverwritingIt(){
        var f=fixture();UUID id=propose(f,List.of(item("UPDATE","CHARACTER",f.character(),null,Map.of("details","新动机")))).getFirst();
        decide(f,id,"accept","accept-before-manual");
        tx.executeWithoutResult(s->{var c=characters.findById(f.character()).orElseThrow();c.setDetails("作者后来改动");characters.saveAndFlush(c);});
        var p=service.proposal(f.user(),f.story(),id);assertThat(p.undoAllowed()).isFalse();assertThat(p.staleReason()).isEqualTo("STORY_PROPOSAL_STALE");
        assertThatThrownBy(()->decide(f,id,"undo","blocked-undo")).isInstanceOf(ApiStatusException.class).hasMessage("STORY_PROPOSAL_STALE");
        assertThat(tx.<String>execute(s->characters.findById(f.character()).orElseThrow().getDetails())).isEqualTo("作者后来改动");
    }
    @Test void sameOutlineBatchAppliesIndependentlyAndPreservesBodyFields(){
        var f=fixture();String body="{\n  \""+f.scene()+"\":\"稿件正文。\\n作者刻意保留空格。  \"\n}";
        UUID manuscriptId=tx.execute(s->{var manuscript=new Manuscript();manuscript.setOutline(outlines.findById(f.outline()).orElseThrow());manuscript.setTitle("已有稿件");manuscript.setSectionsJson(body);manuscript.setCharacterLogsJson("[]");em.persist(manuscript);em.flush();return manuscript.getId();});
        var ids=propose(f,List.of(item("UPDATE","SCENE",f.scene(),f.chapter(),Map.of("summary","第一场新摘要")),item("UPDATE","SCENE",f.scene2(),f.chapter(),Map.of("summary","第二场新摘要"))));
        var oldSecond=service.proposal(f.user(),f.story(),ids.get(1));decide(f,ids.getFirst(),"accept","scene-first");
        var current=service.proposal(f.user(),f.story(),ids.get(1));assertThat(current.applicable()).isTrue();assertThat(current.resourceRevision()).isGreaterThan(oldSecond.resourceRevision());
        decide(f,ids.get(1),"accept","scene-second");var scenes=outline(f).path("chapters").get(0).path("scenes");
        assertThat(scenes.get(0).path("summary").asText()).isEqualTo("第一场新摘要");assertThat(scenes.get(1).path("summary").asText()).isEqualTo("第二场新摘要");
        assertThat(scenes.get(0).path("content").asText()).isEqualTo("旧大纲content保留");assertThat(scenes.get(1).path("content").asText()).isEqualTo("另一场保留");
        assertThat(jdbc.queryForObject("select sections_json from manuscripts where id=?",String.class,bytes(manuscriptId))).isEqualTo(body);
        assertThat(jdbc.queryForObject("select version from manuscripts where id=?",Long.class,bytes(manuscriptId))).isZero();
    }
    @Test void stableNewChapterAndChildrenAreOneItemAndCannotUndo(){
        var f=fixture();UUID id=propose(f,List.of(item("CREATE","CHAPTER",null,null,Map.of("title","新增章","scenes",List.of(Map.of("title","新场一"),Map.of("title","新场二")))))).getFirst();
        var p=service.proposal(f.user(),f.story(),id);var req=new DecisionRequest(p.revision(),p.resourceRevision());
        var saved=service.decide(f.user(),f.story(),id,"accept",req,"create-chapter");service.decide(f.user(),f.story(),id,"accept",req,"create-chapter");
        var chapters=outline(f).path("chapters");assertThat(chapters.size()).isEqualTo(2);assertThat(chapters.get(1).path("id").asText()).isEqualTo(p.targetId().toString());
        assertThat(chapters.get(1).path("scenes").get(0).path("id")).isEqualTo(p.candidate().path("scenes").get(0).path("id"));
        assertThat(chapters.get(1).path("scenes").get(0).path("content").asText()).isEmpty();assertThat(saved.proposal().undoAllowed()).isFalse();
        assertThatThrownBy(()->decide(f,id,"undo","create-undo-blocked")).isInstanceOf(ApiStatusException.class).hasMessage("STORY_CREATE_UNDO_UNSUPPORTED");
    }
    @Test void refineKeepsParentStableTargetAndCreateCharacterIsIdempotent(){
        var f=fixture();var first=service.submit(f.user(),f.story(),new TurnRequest(f.session(),"PROPOSE","新建周远角色卡",null,List.of(),null,null),"propose-no-outline");
        when(ai.chatWithIdempotency(any(),any(),anyString())).thenReturn(response(Map.of("kind","PROPOSALS","answer","角色候选","items",List.of(item("CREATE","CHARACTER",null,null,Map.of("name","周远","details","保管戏票"))))));
        execute(f,first);UUID parentId=service.turn(f.user(),f.story(),first.turnId()).proposalIds().getFirst();
        var parent=service.proposal(f.user(),f.story(),parentId);
        var accepted=service.submit(f.user(),f.story(),new TurnRequest(f.session(),"REFINE","保留姓名，明确他为何保管戏票",null,List.of(),null,parentId),"refine-create-character");
        when(ai.chatWithIdempotency(any(),any(),anyString())).thenReturn(response(Map.of("kind","PROPOSALS","answer","补充动机","items",List.of(item("CREATE","CHARACTER",null,null,Map.of("details","为了替朋友保存证据"))))));
        execute(f,accepted);UUID childId=service.turn(f.user(),f.story(),accepted.turnId()).proposalIds().getFirst();
        var child=service.proposal(f.user(),f.story(),childId);assertThat(child.parentProposalId()).isEqualTo(parentId);assertThat(child.targetId()).isEqualTo(parent.targetId());
        assertThat(child.candidate().path("name").asText()).isEqualTo("周远");assertThat(parent.candidate().path("details").asText()).isEqualTo("保管戏票");
        var request=new DecisionRequest(child.revision(),child.resourceRevision());var saved=service.decide(f.user(),f.story(),childId,"accept",request,"create-character-once");
        assertThat(service.decide(f.user(),f.story(),childId,"accept",request,"create-character-once")).isEqualTo(saved);
        assertThat(jdbc.queryForObject("select count(*) from character_cards where id=?",Integer.class,bytes(child.targetId()))).isEqualTo(1);
        assertThat(service.proposal(f.user(),f.story(),parentId).applicable()).isFalse();
        assertThatThrownBy(()->decide(f,childId,"undo","create-character-undo")).hasMessage("STORY_CREATE_UNDO_UNSUPPORTED");
        verify(ai,times(2)).chatWithIdempotency(any(),any(),anyString());
    }
    @Test void refineInheritsManualChapterAndChildEditsWithoutChangingParent(){
        var f=fixture();UUID parentId=propose(f,List.of(item("CREATE","CHAPTER",null,null,Map.of("title","模型章名","summary","模型章摘要","scenes",List.of(Map.of("title","模型场名","summary","模型场摘要")))))).getFirst();
        var parent=service.proposal(f.user(),f.story(),parentId);JsonNode candidate=parent.candidate().deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode)candidate).put("title","作者章名");
        ((com.fasterxml.jackson.databind.node.ObjectNode)candidate.path("scenes").get(0)).put("summary","作者明确的场景决定");
        var edited=service.edit(f.user(),f.story(),parentId,new EditRequest(parent.revision(),candidate),"edit-chapter-before-refine");
        var a=service.submit(f.user(),f.story(),new TurnRequest(f.session(),"REFINE","保留我的修改，只细化摘要和场名",f.outline(),List.of(),null,parentId),"refine-chapter");
        when(ai.chatWithIdempotency(any(),any(),anyString())).thenReturn(response(Map.of("kind","PROPOSALS","answer","细化候选","items",List.of(item("CREATE","CHAPTER",null,null,Map.of("summary","新的章摘要","scenes",List.of(Map.of("title","细化场名"))))))));
        execute(f,a);var child=service.proposal(f.user(),f.story(),service.turn(f.user(),f.story(),a.turnId()).proposalIds().getFirst());
        assertThat(child.parentProposalId()).isEqualTo(parentId);assertThat(child.targetId()).isEqualTo(edited.targetId());
        assertThat(child.sources().stream().map(Source::id)).contains("proposal:"+parentId);
        assertThat(child.candidate().path("title").asText()).isEqualTo("作者章名");
        assertThat(child.candidate().path("scenes").get(0).path("summary").asText()).isEqualTo("作者明确的场景决定");
        assertThat(child.candidate().path("scenes").get(0).path("id")).isEqualTo(edited.candidate().path("scenes").get(0).path("id"));
        var unchanged=service.proposal(f.user(),f.story(),parentId);assertThat(unchanged.revision()).isEqualTo(edited.revision());assertThat(unchanged.status()).isEqualTo("READY");
        assertThat(unchanged.candidate()).isEqualTo(edited.candidate());assertThat(unchanged.modelCandidate()).isEqualTo(parent.modelCandidate());
    }
    @Test void suggestionsKeepFrozenDiscussionHistoryAndExcludeLaterTurns(){
        var f=fixture();var earlier=submit(f,"DISCUSS","确定林青曾答应替姐姐保存戏票",List.of());
        when(ai.chatWithIdempotency(any(),any(),anyString())).thenReturn(response(Map.of("kind","ANSWER","answer","已明确这份承诺是她的动机。")));execute(f,earlier);
        var clicked=submit(f,"DISCUSS","按刚才方向整理吧",List.of());
        when(ai.chatWithIdempotency(any(),any(),anyString())).thenReturn(response(Map.of("kind","ANSWER","answer","可以整理角色卡。","suggestions",List.of(Map.of("title","整理动机","reason","已有作者决定")))));execute(f,clicked);
        var suggestion=service.turn(f.user(),f.story(),clicked.turnId()).suggestions().getFirst();assertThat(suggestion.sourceTurnIds()).containsExactly(earlier.turnId(),clicked.turnId());
        var later=submit(f,"DISCUSS","之后再谈完全无关的港口风光",List.of());
        when(ai.chatWithIdempotency(any(),any(),anyString())).thenReturn(response(Map.of("kind","ANSWER","answer","后续另开方向。")));execute(f,later);
        var proposal=service.submit(f.user(),f.story(),new TurnRequest(f.session(),"PROPOSE","整理所选讨论",f.outline(),suggestion.sourceTurnIds(),suggestion.id(),null),"propose-frozen-history");
        var frozen=store.read(store.turn(proposal.turnId()).frozenJson(),StoryAssistantService.Frozen.class);
        assertThat(frozen.history().stream().map(StoryAssistantService.History::turnId)).containsExactly(earlier.turnId(),clicked.turnId()).doesNotContain(later.turnId());
        assertThat(frozen.history().getFirst().instruction()).contains("替姐姐保存戏票");
    }
    @Test void discussionCannotSmuggleFormalProposalAndRejectNeverWritesResource(){
        var f=fixture();var a=submit(f,"DISCUSS","讨论林青",List.of());
        when(ai.chatWithIdempotency(any(),any(),anyString())).thenReturn(response(Map.of("kind","PROPOSALS","answer","不允许直接提案","items",List.of(item("UPDATE","CHARACTER",f.character(),null,Map.of("details","越过点击"))))));
        assertThatThrownBy(()->execute(f,a)).hasMessage("STORY_ASSISTANT_RESULT_INVALID");
        assertThatThrownBy(()->execute(f,a)).hasMessage("STORY_ASSISTANT_RESULT_INVALID");
        verify(ai,times(1)).chatWithIdempotency(any(),any(),anyString());
        assertThat(jdbc.queryForObject("select count(*) from story_assistant_proposals where turn_id=?",Integer.class,bytes(a.turnId()))).isZero();
        UUID id=propose(f,List.of(item("UPDATE","CHARACTER",f.character(),null,Map.of("details","候选")))).getFirst();
        assertThat(decide(f,id,"reject","reject-candidate").proposal().status()).isEqualTo("REJECTED");
        assertThat(tx.<String>execute(s->characters.findById(f.character()).orElseThrow().getDetails())).isEqualTo("旧动机");
        verify(ai,times(2)).chatWithIdempotency(any(),any(),anyString());
    }
    @Test void concurrentAppendsConflictThenRetryWithoutLostNodes()throws Exception{
        var f=fixture();var ids=propose(f,List.of(item("CREATE","SCENE",null,f.chapter(),Map.of("title","新增甲")),item("CREATE","SCENE",null,f.chapter(),Map.of("title","新增乙"))));
        var first=service.proposal(f.user(),f.story(),ids.get(0));var second=service.proposal(f.user(),f.story(),ids.get(1));var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)){
            var futures=new ArrayList<Future<Boolean>>();
            for(var p:List.of(first,second))futures.add(executor.submit(()->{start.await();try{service.decide(f.user(),f.story(),p.id(),"accept",new DecisionRequest(p.revision(),p.resourceRevision()),"concurrent-"+p.id());return true;}catch(ApiStatusException ex){if(!ex.getMessage().equals("STORY_RESOURCE_CONFLICT"))throw ex;return false;}}));
            start.countDown();int successes=0;for(var future:futures)if(future.get(20,TimeUnit.SECONDS))successes++;assertThat(successes).isEqualTo(1);
        }
        for(UUID id:ids)if(service.proposal(f.user(),f.story(),id).status().equals("READY"))decide(f,id,"accept","retry-"+id);
        var scenes=outline(f).path("chapters").get(0).path("scenes");assertThat(scenes.size()).isEqualTo(4);assertThat(scenes.get(2).path("order").asInt()).isEqualTo(3);assertThat(scenes.get(3).path("order").asInt()).isEqualTo(4);
    }
    @Test void concurrentSessionAndTurnRetriesReturnSameReceipt()throws Exception{
        var f=fixture();
        try(var executor=Executors.newFixedThreadPool(2)){
            var start=new CountDownLatch(1);Callable<SessionSummary> create=()->{start.await();return service.createSession(f.user(),f.story(),new SessionRequest("重试话题"),"concurrent-session-create");};
            var first=executor.submit(create);var second=executor.submit(create);start.countDown();assertThat(first.get(20,TimeUnit.SECONDS)).isEqualTo(second.get(20,TimeUnit.SECONDS));
            var turnStart=new CountDownLatch(1);var request=new TurnRequest(f.session(),"DISCUSS","并发重试同一请求",null,List.of(),null,null);
            Callable<Accepted> submit=()->{turnStart.await();return service.submit(f.user(),f.story(),request,"concurrent-turn-submit");};
            var firstTurn=executor.submit(submit);var secondTurn=executor.submit(submit);turnStart.countDown();assertThat(firstTurn.get(20,TimeUnit.SECONDS)).isEqualTo(secondTurn.get(20,TimeUnit.SECONDS));
            assertThat(jdbc.queryForObject("select count(*) from story_assistant_turns where session_id=? and request_key=?",Integer.class,bytes(f.session()),"concurrent-turn-submit")).isEqualTo(1);
        }
    }
    @Test void interruptedSecondCallReplaysFrozenRequestAndAllActualSources(){
        var f=fixture();var a=submit(f,"DISCUSS","林青的动机",List.of());AtomicInteger finals=new AtomicInteger();var requests=new ArrayList<String>();
        when(ai.chatWithIdempotency(any(),any(),anyString())).thenAnswer(i->{String key=i.getArgument(2);if(key.endsWith(":plan"))return response(Map.of("kind","READ_BATCH","reads",List.of(Map.of("tool","read_source","sourceId","scene:"+f.scene2()))));requests.add(store.write(i.getArgument(1)));if(finals.getAndIncrement()==0)throw new IllegalStateException("interrupted");return response(Map.of("kind","ANSWER","answer","只依据提供资料讨论"));});
        assertThatThrownBy(()->execute(f,a)).hasMessage("interrupted");String frozen=store.turn(a.turnId()).finalRequestJson();
        tx.executeWithoutResult(s->{var c=characters.findById(f.character()).orElseThrow();c.setDetails("任务开始后新资料");characters.saveAndFlush(c);});
        execute(f,a);assertThat(requests).hasSize(2);assertThat(requests.get(1)).isEqualTo(frozen).isEqualTo(requests.getFirst()).doesNotContain("任务开始后新资料");
        var result=service.turn(f.user(),f.story(),a.turnId());assertThat(result.sources().stream().map(Source::id)).contains("character:"+f.character(),"scene:"+f.scene2());
        execute(f,a);verify(ai,times(1)).chatWithIdempotency(any(),any(),eq(a.operationId()+":story-assistant:plan"));verify(ai,times(2)).chatWithIdempotency(any(),any(),eq(a.operationId()+":story-assistant:final"));
    }
}
