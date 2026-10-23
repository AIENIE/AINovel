package com.ainovel.app.worldassistant;

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
import static com.ainovel.app.worldassistant.WorldAssistantDtos.*;

import com.ainovel.app.world.*;
import com.ainovel.app.world.model.World;
import com.ainovel.app.world.repo.WorldRepository;
import com.ainovel.app.settings.SettingsService;
import com.ainovel.app.settings.dto.WorldPromptTemplatesResponse;
/** Opt-in disposable MySQL only. AI calls are mocked; no live model is exercised. */
@EnabledIfEnvironmentVariable(named="AIENIE_AUDIT_MYSQL_URL",matches="jdbc:mysql:.*")
@DataJpaTest(showSql=false,properties={"spring.flyway.enabled=true","spring.jpa.hibernate.ddl-auto=none","spring.jpa.open-in-view=false"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
@Import({WorldAssistantStore.class,WorldAssistantResources.class,WorldAssistantContext.class,WorldAssistantService.class,
        WorldEntryStore.class,WorldPromptRenderer.class,ResourceAccessGuard.class,WorldAssistantMysqlTest.Beans.class})
class WorldAssistantMysqlTest {
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
    @MockitoBean SettingsService settings;
    @Autowired WorldAssistantService service;
    @Autowired WorldAssistantStore store;
    @Autowired WorldAssistantResources resources;
    @Autowired WorldEntryStore entries;
    @Autowired WorldRepository worlds;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @PersistenceContext EntityManager em;
    @BeforeEach void mocks(){
        when(settings.getWorldPromptTemplates(any())).thenReturn(new WorldPromptTemplatesResponse(Map.of(),Map.of(),"{text}\n{instruction}",WorldPromptRenderer.DEFAULTS));
        when(operations.submit(any(),anyString(),anyString(),any(),any(),anyInt(),anyString(),anyString())).thenAnswer(i->new AiOperationDtos.Accepted(UUID.randomUUID()));
        when(operations.get(any(),any())).thenAnswer(i->new AiOperationDtos.Progress(i.getArgument(1),WorldAssistantService.TYPE,"WORLD_ASSISTANT_SESSION",null,AiOperationStatus.SUCCEEDED,"完成",2,2,0,0,false,1,null,null,null,null,null));
    }
    private record Fixture(User user,UUID world,UUID entry,UUID session) {}
    private Fixture fixture(){
        var f=tx.execute(s->{var u=new User();String n="world-w3-"+UUID.randomUUID();u.setUsername(n);u.setEmail(n+"@example.invalid");u.setPasswordHash("test");em.persist(u);
            var w=new World();w.setUser(u);w.setName("潮汐城");w.setTagline("与海共生");w.setThemesJson("[]");w.setModulesJson("{}");w.setStatus("draft");w.setVersion("0.1.0");em.persist(w);em.flush();UUID e=UUID.randomUUID();
            entries.save(w,List.of(new WorldDraftDtos.Entry(e,"LOCATION","退潮港","","白天开放",List.of(),List.of(),null,null)),List.of());worlds.saveAndFlush(w);return new Fixture(u,w.getId(),e,null);});
        var topic=service.createSession(f.user(),f.world(),new SessionRequest("港口规则"),"topic-once-test");return new Fixture(f.user(),f.world(),f.entry(),topic.id());
    }
    private long revision(Fixture f){return tx.execute(s->worlds.findById(f.world()).orElseThrow().getRevision());}
    private Accepted submit(Fixture f,String intent,UUID parent){return service.submit(f.user(),f.world(),new TurnRequest(f.session(),intent,"退潮港 潮汐城",List.of(),null,parent,revision(f)),UUID.randomUUID().toString());}
    private void execute(Fixture f,Accepted a){service.execute(new AiOperationExecution(a.operationId(),f.user(),"{}",json,(l,c,t)->{}),a.turnId());}
    private AiChatResponse response(Object body){return new AiChatResponse("assistant",store.write(body),new AiUsageDto(10,20,0,0,1),99);}
    private Map<String,Object> item(String action,String type,UUID id,Map<String,?> fields){var m=new LinkedHashMap<String,Object>();m.put("action",action);m.put("resourceType",type);if(id!=null)m.put("targetId",id);m.put("fields",fields);m.put("rationale","依作者要求整理");return m;}
    private List<UUID> propose(Fixture f,List<Map<String,Object>> items){var a=submit(f,"PROPOSE",null);when(ai.chatWithIdempotency(any(),any(),anyString())).thenReturn(response(Map.of("kind","PROPOSALS","answer","请审阅","items",items)));execute(f,a);return service.turn(f.user(),f.world(),a.turnId()).proposalIds();}
    private DecisionResult accept(Fixture f,UUID id,boolean ack){var p=service.proposal(f.user(),f.world(),id);return service.decide(f.user(),f.world(),id,"accept",new DecisionRequest(p.revision(),revision(f),ack),UUID.randomUUID().toString());}
    private String text(Fixture f){return tx.execute(s->entries.entries(worlds.findById(f.world()).orElseThrow()).stream().filter(e->e.id().equals(f.entry())).findFirst().orElseThrow().content());}
    @Test void topicsDiscussionAndExplicitProposalStaySeparate(){var f=fixture();assertThat(service.createSession(f.user(),f.world(),new SessionRequest("港口规则"),"topic-once-test").id()).isEqualTo(f.session());
        service.renameSession(f.user(),f.world(),f.session(),new SessionRequest("海潮"),"rename-once-test");assertThat(service.sessions(f.user(),f.world()).getFirst().title()).isEqualTo("海潮");
        var a=submit(f,"DISCUSS",null);when(ai.chatWithIdempotency(any(),any(),anyString())).thenReturn(response(Map.of("kind","ANSWER","answer","可以讨论潮汐表","suggestions",List.of(Map.of("title","整理港口规则","reason","值得考虑")))));execute(f,a);
        var turn=service.turn(f.user(),f.world(),a.turnId());assertThat(turn.proposalIds()).isEmpty();assertThat(turn.suggestions()).hasSize(1);assertThat(text(f)).isEqualTo("白天开放");
        assertThat(turn.sources()).allMatch(source->Set.of("METADATA","ENTRY","AUTHOR_REQUEST").contains(source.type()));
    }
    @Test void editedCandidateAndAcceptReceiptCommitAtomically(){var f=fixture();var id=propose(f,List.of(item("UPDATE","ENTRY",f.entry(),Map.of("content","退潮开放")))).getFirst();var p=service.proposal(f.user(),f.world(),id);
        p=service.edit(f.user(),f.world(),id,new EditRequest(p.revision(),json.valueToTree(Map.of("content","退潮两小时开放"))),"edit-once-test");assertThat(p.modelCandidate().path("content").asText()).isEqualTo("退潮开放");assertThat(p.candidateEdited()).isTrue();
        var req=new DecisionRequest(p.revision(),revision(f),false);assertThatThrownBy(()->tx.executeWithoutResult(s->{service.decide(f.user(),f.world(),id,"accept",req,"rollback");throw new IllegalStateException("rollback");})).hasMessage("rollback");assertThat(text(f)).isEqualTo("白天开放");assertThat(store.receipt(f.world(),"proposal:"+id,"rollback")).isNull();
        var result=service.decide(f.user(),f.world(),id,"accept",req,"save-once-test");assertThat(json.<JsonNode>valueToTree(service.decide(f.user(),f.world(),id,"accept",req,"save-once-test"))).isEqualTo(json.valueToTree(result));assertThat(text(f)).isEqualTo("退潮两小时开放");
        assertThat(jdbc.queryForObject("select count(*) from world_versions where world_id=?",Integer.class,bytes(f.world()))).isZero();
    }
    @Test void backgroundChangeNeedsReviewWhileChangedTargetBlocks(){var f=fixture();var meta=item("UPDATE","METADATA",f.world(),Map.of("value","海潮世界"));meta.put("fieldKey","tagline");var ids=propose(f,List.of(item("UPDATE","ENTRY",f.entry(),Map.of("content","退潮开放")),meta));accept(f,ids.getFirst(),false);
        var p=service.proposal(f.user(),f.world(),ids.get(1));assertThat(p.applicable()).isTrue();assertThat(p.contextChanged()).isTrue();assertThatThrownBy(()->accept(f,p.id(),false)).hasMessage("WORLD_PROPOSAL_CONTEXT_CHANGED");accept(f,p.id(),true);
        var stale=propose(f,List.of(item("UPDATE","ENTRY",f.entry(),Map.of("content","永不开放")))).getFirst();tx.executeWithoutResult(s->{var w=worlds.findById(f.world()).orElseThrow();var e=entries.entries(w).getFirst();entries.save(w,List.of(new WorldDraftDtos.Entry(e.id(),e.type(),e.title(),e.summary(),"作者当前正文",e.aliases(),e.tags(),null,null)),List.of());worlds.saveAndFlush(w);});
        assertThatThrownBy(()->accept(f,stale,true)).hasMessage("WORLD_PROPOSAL_STALE");assertThat(text(f)).isEqualTo("作者当前正文");
    }
    @Test void newRelationWaitsForBothStableEndpoints(){var f=fixture();var a=item("CREATE","ENTRY",null,Map.of("type","LOCATION","title","潮汐塔","content","观察潮位"));a.put("clientKey","tower");var b=item("CREATE","RELATION",null,Map.of("sourceId",f.entry().toString(),"targetId","tower","type","受指引","description","旗语通信"));
        var ids=propose(f,List.of(a,b));var link=service.proposal(f.user(),f.world(),ids.get(1));assertThat(link.staleReason()).isEqualTo("WORLD_PROPOSAL_ENDPOINT_MISSING");assertThatThrownBy(()->accept(f,link.id(),true)).hasMessage("WORLD_PROPOSAL_ENDPOINT_MISSING");
        var entry=accept(f,ids.getFirst(),false);assertThat(link.candidate().path("targetId").asText()).isEqualTo(entry.proposal().targetId().toString());accept(f,link.id(),true);
    }
    @Test void overviewReadsEverythingAndExpiresAfterSourceChange(){var f=fixture();var a=service.single(f.user(),f.world(),null,new SingleRequest(revision(f),"整合概览"),"overview");when(ai.chatWithIdempotency(any(),any(),anyString())).thenReturn(response(Map.of("kind","PROPOSALS","answer","概览候选","items",List.of(item("UPDATE","OVERVIEW",f.world(),Map.of("content","海潮统治这座城"))))));execute(f,a);
        var turn=service.turn(f.user(),f.world(),a.turnId());assertThat(turn.sources()).hasSize(8);var id=turn.proposalIds().getFirst();tx.executeWithoutResult(s->{var w=worlds.findById(f.world()).orElseThrow();w.setNotes("补充代价");worlds.saveAndFlush(w);});assertThatThrownBy(()->accept(f,id,true)).hasMessage("WORLD_OVERVIEW_STALE");
    }
    @Test void refineFreezesFeatureAndKeepsOriginalCandidate(){var f=fixture();var a=service.single(f.user(),f.world(),f.entry(),new SingleRequest(revision(f),"细化退潮港"),"refine-once-test");when(ai.chatWithIdempotency(any(),any(),anyString())).thenReturn(response(Map.of("kind","PROPOSALS","answer","候选","items",List.of(item("UPDATE","ENTRY",f.entry(),Map.of("content","白天以旗号开放"))))));execute(f,a);verify(ai).chatWithIdempotency(any(),argThat(r->r.featureKey().equals("world.entry.refine")),anyString());
        clearInvocations(ai);execute(f,a);verifyNoInteractions(ai);var id=service.turn(f.user(),f.world(),a.turnId()).proposalIds().getFirst();assertThat(service.proposal(f.user(),f.world(),id).candidate().path("content").asText()).isEqualTo("白天以旗号开放");assertThat(text(f)).isEqualTo("白天开放");
    }
    @Test void conflictEvidenceMustActuallyHaveBeenRead(){var f=fixture();var a=submit(f,"DISCUSS",null);when(ai.chatWithIdempotency(any(),any(),anyString())).thenReturn(response(Map.of("kind","ANSWER","answer","可能冲突","conflicts",List.of(Map.of("title","时间冲突","explanation","规则不同","sourceIds",List.of("entry:"+f.entry(),"foreign-world"))))));assertThatThrownBy(()->execute(f,a)).hasMessage("WORLD_CONFLICT_SOURCE_INVALID");assertThat(store.turn(a.turnId()).planResultJson()).isNotNull();assertThat(text(f)).isEqualTo("白天开放");}
    @Test void concurrentAcceptUsesDraftRevision(){var f=fixture();var ids=propose(f,List.of(item("UPDATE","ENTRY",f.entry(),Map.of("content","退潮开放"))));var p=service.proposal(f.user(),f.world(),ids.getFirst());var req=new DecisionRequest(p.revision(),revision(f),false);
        try(var executor=Executors.newFixedThreadPool(2)){var gate=new CountDownLatch(1);var results=new ArrayList<Future<Boolean>>();for(int i=0;i<2;i++)results.add(executor.submit(()->{gate.await();try{service.decide(f.user(),f.world(),p.id(),"accept",req,UUID.randomUUID().toString());return true;}catch(ApiStatusException e){return false;}}));gate.countDown();assertThat(results.stream().filter(r->{try{return r.get(15,TimeUnit.SECONDS);}catch(Exception e){throw new RuntimeException(e);}}).count()).isEqualTo(1);}
        assertThat(text(f)).isEqualTo("退潮开放");
    }
    @Test void continuationCreatesChildAndPreservesParentCandidate(){
        var f=fixture();var parent=propose(f,List.of(item("UPDATE","ENTRY",f.entry(),Map.of("content","白天以旗号开放")))).getFirst();
        var a=submit(f,"REFINE",parent);when(ai.chatWithIdempotency(any(),any(),anyString())).thenReturn(response(Map.of("kind","PROPOSALS","answer","细化候选","items",List.of(item("UPDATE","ENTRY",f.entry(),Map.of("content","白天升起蓝旗才开放"))))));execute(f,a);
        var child=service.proposal(f.user(),f.world(),service.turn(f.user(),f.world(),a.turnId()).proposalIds().getFirst());assertThat(child.parentProposalId()).isEqualTo(parent);assertThat(child.targetId()).isEqualTo(f.entry());assertThat(service.proposal(f.user(),f.world(),parent).candidate().path("content").asText()).isEqualTo("白天以旗号开放");assertThat(text(f)).isEqualTo("白天开放");
    }
    @Test void secondStageRecoveryUsesFrozenSourcesAndOriginalCallIdentity(){
        var f=fixture();var a=submit(f,"DISCUSS",null);var identities=new ArrayList<String>();
        when(ai.chatWithIdempotency(any(),any(),anyString())).thenAnswer(i->{identities.add(i.getArgument(2));if(identities.size()==1)return response(Map.of("kind","READ_BATCH","reads",List.of(Map.of("tool","read_source","sourceId","entry:"+f.entry()))));if(identities.size()==2)throw new IllegalStateException("temporary");return response(Map.of("kind","ANSWER","answer","按照白天开放的规则讨论"));});
        assertThatThrownBy(()->execute(f,a)).hasMessage("temporary");tx.executeWithoutResult(s->{var w=worlds.findById(f.world()).orElseThrow();w.setNotes("调用后新增备注");worlds.saveAndFlush(w);});execute(f,a);
        assertThat(identities).hasSize(3);assertThat(identities.get(1)).isEqualTo(identities.get(2));var turn=service.turn(f.user(),f.world(),a.turnId());assertThat(turn.usage().modelCalls()).isEqualTo(2);assertThat(turn.sources()).noneMatch(source->source.content().contains("调用后新增备注"));
    }
    @Test void acceptedQuoteSuppliesOriginalTemplatesAtTaskCreation(){
        var f=fixture();when(settings.getWorldPromptTemplates(any())).thenReturn(new WorldPromptTemplatesResponse(Map.of(),Map.of(),"{text}",Map.of("discuss","原模板：只讨论")));
        var request=new TurnRequest(f.session(),"DISCUSS","退潮港",List.of(),null,null,revision(f));var frozen=service.validateQuote(f.user(),f.world(),null,json.valueToTree(request),false);
        when(settings.getWorldPromptTemplates(any())).thenReturn(new WorldPromptTemplatesResponse(Map.of(),Map.of(),"{text}",Map.of("discuss","后来改的模板")));
        Accepted a;try(var ignored=com.ainovel.app.modelconfig.AiSelectionContext.install(new com.ainovel.app.modelconfig.AiSelectionContext.Snapshot("quote",Map.of(),null,Map.of(),null,frozen))){a=service.submit(f.user(),f.world(),request,"frozen-quote");}
        when(ai.chatWithIdempotency(any(),any(),anyString())).thenAnswer(i->{AiChatRequest r=i.getArgument(1);String prompts=r.messages().toString();assertThat(prompts).contains("原模板：只讨论").doesNotContain("后来改的模板");return response(Map.of("kind","ANSWER","answer","讨论"));});execute(f,a);
    }

}
