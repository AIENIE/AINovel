package com.ainovel.app.writingagent;

import com.ainovel.app.ai.*;
import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.aioperation.AiOperationService;
import com.ainovel.app.assistant.*;
import com.ainovel.app.common.JsonColumnCodec;
import com.ainovel.app.economy.EconomyService;
import com.ainovel.app.integration.AiGatewayGrpcClient;
import com.ainovel.app.manuscript.*;
import com.ainovel.app.manuscript.dto.ManuscriptDto;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.modelconfig.*;
import com.ainovel.app.narrative.NarrativeContextService;
import com.ainovel.app.prompt.*;
import com.ainovel.app.quality.SlopPatternSamplingService;
import com.ainovel.app.quality.language.*;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.story.model.*;
import com.ainovel.app.user.User;
import com.ainovel.app.v2.V2VersionPersistenceService;
import com.ainovel.app.v2.model.V2ManuscriptBranch;
import com.ainovel.app.world.*;
import com.ainovel.app.world.model.*;
import com.ainovel.app.worldcontext.*;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static com.ainovel.app.modelconfig.AiModelConfigurationService.bytes;
import static com.ainovel.app.writingagent.WritingAgentDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real JPA refreshes and InnoDB competition against the same story/outline/manuscript lock hierarchy. */
@EnabledIfEnvironmentVariable(named="AIENIE_AUDIT_MYSQL_URL",matches="jdbc:mysql:.*")
@DataJpaTest(showSql=false,properties={"spring.flyway.enabled=true","spring.jpa.hibernate.ddl-auto=none","spring.jpa.open-in-view=false","app.manuscript.scene-storage.enabled=false"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
@Import({WritingAgentService.class,WritingAgentStore.class,WritingAgentMoney.class,WorldAgentTools.class,CandidateAgentTools.class,WritingAgentPrompts.class,
        WorldBindingService.class,WorldEntryStore.class,WorldContextSelector.class,WorldContextPreferences.class,ManuscriptContentService.class,JsonColumnCodec.class,WritingAgentAdoptionMysqlTest.Beans.class})
class WritingAgentAdoptionMysqlTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry p){String url=System.getenv("AIENIE_AUDIT_MYSQL_URL");if(url==null||!url.matches("jdbc:mysql://[^/]+/aienie_novel_audit_test_[A-Za-z0-9_]+(?:\\?.*)?"))throw new IllegalArgumentException("Isolated schema required");p.add("spring.datasource.url",()->url);p.add("spring.datasource.username",()->System.getenv("AIENIE_AUDIT_MYSQL_USERNAME"));p.add("spring.datasource.password",()->System.getenv("AIENIE_AUDIT_MYSQL_PASSWORD"));p.add("spring.datasource.driver-class-name",()->"com.mysql.cj.jdbc.Driver");p.add("spring.jpa.properties.hibernate.dialect",()->"org.hibernate.dialect.MySQLDialect");}
    @TestConfiguration static class Beans {
        @Bean ObjectMapper json(){return new ObjectMapper().findAndRegisterModules();}
        @Bean static org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor repositoryProxies(){
            var processor=new org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor();processor.setProxyTargetClass(true);return processor;
        }
    }
    @MockitoBean ResourceAccessGuard access;@MockitoBean WorldVersionService versionService;@MockitoBean AssistantContext contexts;
    @MockitoBean NarrativeContextService narrative;@MockitoBean SceneGenerationService generation;@MockitoBean PromptAssemblyService assembly;
    @MockitoBean SlopPatternSamplingService slop;@MockitoBean AiService ai;@MockitoBean AiGatewayGrpcClient gateway;@MockitoBean EconomyService economy;
    @MockitoBean AiModelConfigurationService configuration;@MockitoBean AiOperationService operations;@MockitoBean ManuscriptService manuscriptService;@MockitoBean V2VersionPersistenceService versions;
    @Autowired WritingAgentService service;@Autowired WritingAgentStore store;@Autowired WorldBindingService bindings;@Autowired ManuscriptContentService contents;
    @Autowired ManuscriptRepository manuscripts;@Autowired PlatformTransactionManager manager;@Autowired JdbcTemplate jdbc;@PersistenceContext EntityManager em;
    record Fixture(User user,UUID manuscript,UUID story,UUID scene,UUID branch,UUID v1,UUID v2,UUID run,UUID candidate,long version) {}
    Fixture fixture;
    @BeforeEach void seed(){
        when(access.requireOwnedManuscript(any(),any())).thenAnswer(i->manuscripts.findWithStoryById(i.getArgument(0)).orElseThrow());
        when(contexts.freeze(any(),any(),any(),anyString(),anyString())).thenReturn(new AssistantContext.Snapshot(false,null,List.of(),List.of(),List.of(),1,500));
        when(generation.languageSelection(any())).thenReturn(new LanguageStandard.Selection(LanguageStandard.VERSION,false,false));
        when(generation.planningHash(any())).thenAnswer(i->WritingAgentStore.hash(((Manuscript)i.getArgument(0)).getOutline().getContentJson()));
        when(assembly.assembleSceneDraft(any())).thenReturn(new AssembledPrompt(List.of(new AiChatRequest.Message("system","固定规则")),24000));
        fixture=new TransactionTemplate(manager).execute(status->{
            var u=new User();String name="adopt-"+UUID.randomUUID();u.setUsername(name);u.setEmail(name+"@example.invalid");u.setPasswordHash("x");em.persist(u);
            var world=new World();world.setUser(u);world.setName("世界");world.setStatus("draft");em.persist(world);em.flush();
            var content=new WorldDraftDtos.Content("世界","",List.of(),"","",List.of(),List.of(),Map.of(),"{}");
            UUID v1=version(world,1,content),v2=version(world,2,new WorldDraftDtos.Content("世界新版","",List.of(),"","",List.of(),List.of(),Map.of(),"{}"));
            var story=new Story();story.setUser(u);story.setTitle("故事");story.setStatus("draft");story.setWorldId(world.getId().toString());story.setWorldVersionId(v1);story.setWorldBindingRevision(1);em.persist(story);
            UUID scene=UUID.randomUUID();var outline=new Outline();outline.setStory(story);outline.setTitle("大纲");outline.setContentJson(store.write(Map.of("chapters",List.of(Map.of("id",UUID.randomUUID(),"title","第一章","scenes",List.of(Map.of("id",scene,"title","场景")))))));em.persist(outline);
            var m=new Manuscript();m.setOutline(outline);m.setTitle("稿件");contents.initialize(m);em.persist(m);em.flush();
            var branch=new V2ManuscriptBranch();branch.setManuscript(m);branch.setName("main");branch.setStatus("active");branch.setMain(true);em.persist(branch);em.flush();m.setCurrentBranchId(branch.getId());contents.writeScene(m,scene,"<p>原稿</p>");em.flush();
            return new Fixture(u,m.getId(),story.getId(),scene,branch.getId(),v1,v2,null,null,m.getVersion());
        });
        var request=new Request(fixture.branch(),fixture.version(),"<p>原稿</p>",null,"完善动作","EDIT","fast",false,false,new BigDecimal("5"),100L,Limits.defaults(),null,null);
        var frozen=service.prepare(fixture.user(),fixture.manuscript(),fixture.scene(),request);UUID run=store.isolated(()->store.create(fixture.user().getId(),frozen,"adopt-"+UUID.randomUUID(),"hash"));
        var replacements=List.of(new AssistantDocumentProjection.Replacement(frozen.document().runs().getFirst().runId(),List.of("新稿")));
        String html=AssistantDocumentProjection.apply(frozen.document(),replacements);UUID id=UUID.randomUUID();var candidate=new Candidate(id,1,null,html,WritingAgentStore.hash(html),2,replacements,List.of());
        jdbc.update("insert into narrative_generation_candidates(id,manuscript_id,branch_id,scene_id,content,stamp_json,status,created_at,operation_id,generation_sequence,source_kind,metadata_json) values(?,?,?,?,?,'{}','READY',UTC_TIMESTAMP(6),?,1,'AGENT',?)",bytes(id),bytes(fixture.manuscript()),bytes(fixture.branch()),bytes(fixture.scene()),html,bytes(run),store.write(candidate));
        store.state(run,"COMPLETED",Map.of());fixture=new Fixture(fixture.user(),fixture.manuscript(),fixture.story(),fixture.scene(),fixture.branch(),fixture.v1(),fixture.v2(),run,id,fixture.version());
        when(manuscriptService.get(fixture.manuscript())).thenAnswer(i->{var m=manuscripts.findWithStoryById(fixture.manuscript()).orElseThrow();return new ManuscriptDto(m.getId(),m.getOutline().getId(),m.getTitle(),m.getWorldId(),contents.readAll(m),null,m.getVersion(),m.getUpdatedAt(),m.getCurrentBranchId());});
    }
    UUID version(World world,int seq,WorldDraftDtos.Content content){var v=new WorldVersion();v.setId(UUID.randomUUID());v.setWorldId(world.getId());v.setSequenceNumber(seq);v.setOrigin("PUBLISHED");v.setDraftRevision(seq);v.setSnapshotJson(store.write(content));v.setContentHash(WritingAgentStore.hash(v.getSnapshotJson()));v.setCreatedAt(Instant.now());em.persist(v);return v.getId();}
    Decision decision(){return new Decision(store.run(fixture.run()).revision(),fixture.version(),fixture.branch(),fixture.candidate());}
    void await(CountDownLatch latch){try{if(!latch.await(20,TimeUnit.SECONDS))throw new IllegalStateException("test barrier timeout");}catch(InterruptedException e){throw new IllegalStateException(e);}}
    @Test void springProxiesCanPreviewAndReloadCandidateDetailsWithoutReadingProxyFields() {
        assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(service));
        assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(store));
        assertNotNull(store.mapper());assertNotNull(store.jdbc());
        assertNotNull(service.worlds());assertNotNull(service.candidates());
        var request=new Request(fixture.branch(),fixture.version(),"<p>原稿</p>",null,"完善动作","EDIT","fast",false,false,new BigDecimal("5"),100L,Limits.defaults(),null,null);

        var preview=service.preview(fixture.user(),fixture.manuscript(),fixture.scene(),request);
        assertNotNull(preview.contextHash());assertFalse(preview.contextHash().isBlank());
        var detail=service.get(fixture.user(),fixture.manuscript(),fixture.scene(),fixture.run());
        assertEquals(fixture.run(),detail.id());assertEquals(fixture.candidate(),detail.candidates().getFirst().id());
        assertEquals(fixture.run(),service.list(fixture.user(),fixture.manuscript(),fixture.scene(),fixture.branch()).getFirst().id());
        verifyNoInteractions(ai,gateway,economy);
    }
    @Test void worldUpgradeThatCommitsFirstMakesWaitingOldCandidateInapplicable() throws Exception {
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var upgrade=pool.submit(()->new TransactionTemplate(manager).execute(status->{bindings.change("STORY",fixture.story(),new WorldBindingService.Change(fixture.v1(),fixture.v2(),1L));locked.countDown();await(release);return true;}));
            await(locked);var request=decision();var adoption=pool.submit(()->{try{service.accept(fixture.user(),fixture.manuscript(),fixture.scene(),fixture.run(),request,"accept-stale");return "unexpected";}catch(com.ainovel.app.common.ApiStatusException stale){return stale.getMessage();}});
            Thread.sleep(100);assertFalse(adoption.isDone());release.countDown();assertTrue(upgrade.get(30,TimeUnit.SECONDS));assertEquals("WRITING_AGENT_CONTEXT_CHANGED",adoption.get(30,TimeUnit.SECONDS));
        }
        assertEquals("<p>原稿</p>",new TransactionTemplate(manager).execute(s->contents.readScene(manuscripts.findWithStoryById(fixture.manuscript()).orElseThrow(),fixture.scene())));
        assertEquals("READY",jdbc.queryForObject("select status from narrative_generation_candidates where id=?",String.class,bytes(fixture.candidate())));verifyNoInteractions(ai,versions);
    }
    @Test void worldUpgradeWaitsUntilVerifiedAdoptionCommitsAndDuplicateAdoptionReplays() throws Exception {
        var verified=new CountDownLatch(1);var release=new CountDownLatch(1);AtomicInteger snapshots=new AtomicInteger();
        when(versions.createVersion(any(),any(),anyMap())).thenAnswer(i->{if(snapshots.incrementAndGet()==1){verified.countDown();await(release);}return Map.of("id",UUID.randomUUID());});
        var request=decision();
        try(var pool=Executors.newFixedThreadPool(2)){
            var adoption=pool.submit(()->service.accept(fixture.user(),fixture.manuscript(),fixture.scene(),fixture.run(),request,"accept-once"));await(verified);
            var upgrade=pool.submit(()->bindings.change("STORY",fixture.story(),new WorldBindingService.Change(fixture.v1(),fixture.v2(),1L)));
            Thread.sleep(100);assertFalse(upgrade.isDone());release.countDown();var accepted=adoption.get(30,TimeUnit.SECONDS);assertTrue(accepted.sections().get(fixture.scene().toString()).contains("新稿"));upgrade.get(30,TimeUnit.SECONDS);
            assertEquals(accepted,service.accept(fixture.user(),fixture.manuscript(),fixture.scene(),fixture.run(),request,"accept-once"));
        }
        assertEquals("APPLIED",jdbc.queryForObject("select status from narrative_generation_candidates where id=?",String.class,bytes(fixture.candidate())));assertEquals(2,snapshots.get());verifyNoInteractions(ai);
    }
}
