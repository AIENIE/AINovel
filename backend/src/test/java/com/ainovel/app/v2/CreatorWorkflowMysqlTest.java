package com.ainovel.app.v2;

import com.ainovel.app.common.*;
import com.ainovel.app.manuscript.dto.*;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.story.model.*;
import com.ainovel.app.user.User;
import com.ainovel.app.manuscript.*;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static com.ainovel.app.material.MaterialFingerprintService.bytes;

/** Uses only an explicitly provisioned disposable schema, real enhancement and no test transaction. */
@EnabledIfEnvironmentVariable(named="AIENIE_AUDIT_MYSQL_URL", matches="jdbc:mysql:.*")
@DataJpaTest(showSql=false, properties={"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none", "spring.jpa.open-in-view=false"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
@Import({ManuscriptService.class, ManuscriptContentService.class, OwnedManuscriptTransactions.class,
        ResourceAccessGuard.class, JsonColumnCodec.class, com.ainovel.app.narrative.NarrativeGenerationCandidateStore.class, V2VersionPersistenceService.class,
        V2ExportPersistenceService.class, V2ExportJobService.class, V2ExportRenderer.class, V2Json.class, CreatorWorkflowMysqlTest.Beans.class})
class CreatorWorkflowMysqlTest {
    @DynamicPropertySource static void mysql(DynamicPropertyRegistry r) {
        String url=System.getenv("AIENIE_AUDIT_MYSQL_URL");
        if (url==null || !url.matches("jdbc:mysql://[^/]+/aienie_novel_audit_test_[A-Za-z0-9_]+(?:\\?.*)?")) throw new IllegalArgumentException("Isolated schema required");
        r.add("spring.datasource.url",()->url);
        r.add("spring.datasource.username",()->System.getenv("AIENIE_AUDIT_MYSQL_USERNAME"));
        r.add("spring.datasource.password",()->System.getenv("AIENIE_AUDIT_MYSQL_PASSWORD"));
        r.add("spring.datasource.driver-class-name",()->"com.mysql.cj.jdbc.Driver");
        r.add("spring.jpa.properties.hibernate.dialect",()->"org.hibernate.dialect.MySQLDialect");
    }
    @TestConfiguration static class Beans {
        @Bean ObjectMapper objectMapper(){return new ObjectMapper().findAndRegisterModules();}
        @Bean(name="v2ExportExecutor",destroyMethod="shutdown") ExecutorService exportExecutor(){return Executors.newSingleThreadExecutor();}
        @Bean com.ainovel.app.ai.AiValidationCallBudget validationBudget(
                com.ainovel.app.ai.AiValidationBudgetRepository budgets,
                com.ainovel.app.ai.AiValidationCallRepository calls, ObjectMapper mapper) {
            var environment = new org.springframework.mock.env.MockEnvironment(); environment.setActiveProfiles("local");
            var budget = new com.ainovel.app.ai.AiValidationCallBudget(budgets, calls, mapper, environment);
            return budget;
        }
        @Bean TransactionTemplate transactionTemplate(PlatformTransactionManager tm){return new TransactionTemplate(tm);}
    }
    @MockitoBean com.ainovel.app.world.WorldBindingService worldBindings;
    @MockitoBean com.ainovel.app.worldcontext.WorldContextPreferences worldPreferences;
    @MockitoBean com.ainovel.app.quality.language.LanguageQualityService languageQuality;
    @MockitoBean com.ainovel.app.material.evidence.MaterialEvidenceService materialEvidence;
    @BeforeEach void noWorldBindingFixture() {
        when(worldBindings.input(any())).thenReturn(new com.ainovel.app.world.WorldBindingService.Frozen(null));
        when(worldBindings.freeze(any())).thenReturn(new com.ainovel.app.world.WorldBindingService.Frozen(null));
        when(worldBindings.same(any(), any())).thenCallRealMethod();
        when(worldPreferences.read(anyString(), any())).thenReturn(com.ainovel.app.worldcontext.WorldContextDtos.Preferences.defaults(null));
    }
    @MockitoBean CurrentUserResolver resolver;
    @MockitoBean SceneGenerationService generator;
    @MockitoBean com.ainovel.app.manuscript.attribution.SceneGenerationAttributionService attribution;
    @MockitoBean com.ainovel.app.narrative.NarrativeContextService narrative;
    @Autowired com.ainovel.app.narrative.NarrativeGenerationCandidateStore candidates;
    @Autowired com.ainovel.app.ai.AiValidationCallBudget validationBudget;
    @Autowired ManuscriptService manuscripts;
    @Autowired OwnedManuscriptTransactions owned;
    @Autowired V2VersionPersistenceService versions;
    @Autowired V2ExportPersistenceService exports;
    @Autowired V2ExportJobService exportJobs;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;
    @PersistenceContext EntityManager em;
    @AfterEach void logout(){SecurityContextHolder.clearContext();}

    @Test void flushedCreationAndConcurrentReceiptReplayConflictAndDeletedResult() throws Exception {
        Fixture f=fixture();String key=UUID.randomUUID().toString();
        var request=new ManuscriptCreateRequest("并发创建",null);
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<ManuscriptDto> task=()->{login(f.user);start.await();try{return manuscripts.create(f.outline,request,key);}finally{logout();}};
            var a=pool.submit(task);var b=pool.submit(task);start.countDown();
            var first=a.get(20,TimeUnit.SECONDS);var second=b.get(20,TimeUnit.SECONDS);
            assertEquals(first,second);assertNotNull(first.updatedAt());assertEquals(0,first.version());
            assertEquals(1,jdbc.queryForObject("select count(*) from manuscripts where outline_id=?",Integer.class,bytes(f.outline)));
            login(f.user);
            assertEquals(409,assertThrows(ApiStatusException.class,()->manuscripts.create(f.outline,new ManuscriptCreateRequest("不同内容",null),key)).getStatus().value());
            manuscripts.delete(first.id());
            assertEquals(410,assertThrows(ApiStatusException.class,()->manuscripts.create(f.outline,request,key)).getStatus().value());
            assertNotEquals(manuscripts.create(f.outline,request).id(),manuscripts.create(f.outline,request).id());
        }
    }

    @Test void managedBodySnapshotVersionAndRollbackAreAtomicAndRejectOtherOwner() {
        Fixture f=fixture();login(f.user);var created=manuscripts.create(f.outline,new ManuscriptCreateRequest("事务稿件",null));
        String body="{\""+f.scene+"\":\"<p>保留正文</p>\"}";
        owned.write(created.id(),f.user,m->{m.setSectionsJson(body);return null;});
        Manuscript detached=transactions.execute(s->em.find(Manuscript.class,created.id()));
        assertInstanceOf(org.hibernate.engine.spi.PersistentAttributeInterceptable.class,detached);
        assertThrows(org.hibernate.LazyInitializationException.class,detached::getSectionsJson);
        assertEquals(body,owned.read(created.id(),f.user,Manuscript::getSectionsJson));
        var snapshot=owned.write(created.id(),f.user,m->versions.createVersion(m,f.user,Map.of("label","检查点")));
        assertNotNull(snapshot.get("id"));
        var job=owned.write(created.id(),f.user,m->exports.createJob(f.user,m,Map.of("format","txt"),"test.txt","text/plain"));
        String frozen=jdbc.queryForObject("select snapshot_json from export_jobs where id=?",String.class,bytes((UUID)job.get("id")));
        assertTrue(frozen.contains("保留正文"));
        long before=manuscripts.get(created.id()).version();
        assertThrows(IllegalStateException.class,()->owned.write(created.id(),f.user,m->{m.setSectionsJson("{}");em.flush();throw new IllegalStateException("rollback");}));
        assertEquals(body,owned.read(created.id(),f.user,Manuscript::getSectionsJson));assertEquals(before,manuscripts.get(created.id()).version());
        User other=new User();other.setId(UUID.randomUUID());
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->owned.read(created.id(),other,Manuscript::getSectionsJson));
    }

    @Test void asyncExportsCompleteAndDownloadFrozenBodyAcrossDatabaseTimeZones() throws Exception {
        Fixture f=fixture();login(f.user);var created=manuscripts.create(f.outline,new ManuscriptCreateRequest("下载测试",null));
        owned.write(created.id(),f.user,m->{m.setSectionsJson("{\""+f.scene+"\":\"<p>冻结正文甲乙丙</p>\"}");return null;});
        for(String format:List.of("txt","docx","epub","pdf")) {
            var job=owned.write(created.id(),f.user,m->exportJobs.create(f.user,m,Map.of("format",format)));
            UUID id=(UUID)job.get("id");String status="queued";
            for(int i=0;i<100;i++) {
                status=jdbc.queryForObject("select status from export_jobs where id=?",String.class,bytes(id));
                if(List.of("completed","failed").contains(status))break;
                Thread.sleep(100);
            }
            assertEquals("completed",status,format);
            var download=exportJobs.download(created.id(),id,f.user);
            var output=new java.io.ByteArrayOutputStream();download.body().writeTo(output);
            byte[] data=output.toByteArray();assertTrue(data.length>0,format);assertEquals(download.size(),(long)data.length);
            assertEquals(download.checksum(),java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(data)));
            if(format.equals("txt"))assertTrue(output.toString(java.nio.charset.StandardCharsets.UTF_8).contains("冻结正文甲乙丙"));
        }
    }

    @Test void concurrentProviderReservationsSurviveBusinessRollbackAndRejectFourthRpc() throws Exception {
        String run = "mysql-budget-" + UUID.randomUUID();
        org.springframework.test.util.ReflectionTestUtils.setField(validationBudget, "runId", run);
        org.springframework.test.util.ReflectionTestUtils.setField(validationBudget, "maximumCalls", 10);
        org.springframework.test.util.ReflectionTestUtils.setField(validationBudget, "providerAttemptsPerRpc", 3);
        jdbc.update("insert into ai_validation_budgets(id,used,call_limit,provider_attempt_limit,reserved_provider_attempts) values(?,0,10,10,0)",run);
        try(var pool=Executors.newFixedThreadPool(3)) {
            var start=new CountDownLatch(1); var futures=new ArrayList<Future<?>>();
            for(String kind:List.of("CHAT","CHAT_STREAM","EMBEDDINGS")) futures.add(pool.submit(()->{
                start.await();
                assertThrows(IllegalStateException.class,()->transactions.execute(s->{
                    validationBudget.claimGateway(kind,List.of(),UUID.randomUUID().toString(),"test");
                    throw new IllegalStateException("business rollback");
                })); return null;
            }));
            start.countDown(); for(var future:futures)future.get(20,TimeUnit.SECONDS);
        }
        assertEquals(3,jdbc.queryForObject("select used from ai_validation_budgets where id=?",Integer.class,run));
        assertEquals(9,jdbc.queryForObject("select reserved_provider_attempts from ai_validation_budgets where id=?",Integer.class,run));
        assertEquals(3,jdbc.queryForObject("select count(*) from ai_validation_calls where run_id=?",Integer.class,run));
        assertEquals(429,assertThrows(ApiStatusException.class,()->validationBudget.claimGateway("CHAT",List.of(),"fourth","test")).getStatus().value());
    }

    @Test void migratedRequestColumnsAccept160WithoutTruncation() {
        Fixture f=fixture();UUID run=UUID.randomUUID();String request="r".repeat(160);
        jdbc.update("insert into ai_operation_runs(id,user_id,operation_type,status,total_steps,completed_steps,output_tokens,output_tokens_estimated,attempt_count,stream_started,request_id) values(?,?,'TEST','FAILED',1,0,0,0,1,0,?)",bytes(run),bytes(f.user.getId()),request);
        assertEquals(request,jdbc.queryForObject("select request_id from ai_operation_runs where id=?",String.class,bytes(run)));
        assertEquals(List.of(160L,160L),jdbc.queryForList("select character_maximum_length from information_schema.columns where table_schema=database() and table_name in ('ai_operation_runs','ai_operation_steps') and column_name='request_id' order by table_name",Long.class));
    }

    @Test void sceneCandidateDuplicateWriteAndAdoptionAreIdempotentOnMysql() throws Exception {
        var f=fixture();login(f.user);
        var created=manuscripts.create(f.outline,new ManuscriptCreateRequest("候选验收",null));
        Manuscript source=transactions.execute(s->{var m=em.find(Manuscript.class,created.id());versions.ensureGenerationBaseline(m,f.user);em.flush();return m;});
        var metadata=new SceneGenerationService.GenerationMetadata("test","scene-draft-v2","a".repeat(64),1,null);
        var language=new com.ainovel.app.quality.language.LanguageStandard.Selection("zh-naturalness-v3",false,false);
        UUID operation=UUID.randomUUID();
        UUID id=candidates.retainDraft(operation,source,f.scene,"<p>保留结尾</p>",1,2800,3200,4,new com.ainovel.app.narrative.NarrativeGenerationCandidateStore.Source(GenerationMode.FAST,metadata,language));
        assertEquals(id,candidates.retainDraft(operation,source,f.scene,"<p>不能覆盖</p>",1,2800,3200,4,new com.ainovel.app.narrative.NarrativeGenerationCandidateStore.Source(GenerationMode.FAST,metadata,language)));
        assertEquals(1,manuscripts.sceneCandidates(source.getId(),f.scene,source.getCurrentBranchId()).size());
        assertEquals("<p>保留结尾</p>",manuscripts.sceneCandidate(source.getId(),f.scene,source.getCurrentBranchId(),id).content());
        var run=new SceneGenerationRunDto(UUID.randomUUID(),source.getId(),f.scene,f.user.getId(),"FAST",com.ainovel.app.manuscript.attribution.SceneGenerationRunStatus.GENERATED,"test","scene-draft-v2",1,null,Map.of(),UUID.randomUUID(),null,null,null,0,0,1.0,false,List.of(),null,false,java.time.Instant.now(),java.time.Instant.now());
        org.mockito.Mockito.when(attribution.recordGeneration(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any())).thenReturn(run);
        var request=new ManuscriptService.AdoptCandidateRequest(source.getVersion(),source.getCurrentBranchId());
        var first=manuscripts.adoptSceneCandidate(source.getId(),f.scene,id,request);
        var again=manuscripts.adoptSceneCandidate(source.getId(),f.scene,id,request);
        assertEquals(first.version(),again.version());assertEquals("<p>保留结尾</p>",again.sections().get(f.scene.toString()));
        assertEquals(1,jdbc.queryForObject("select count(*) from manuscript_versions where manuscript_id=? and snapshot_type='generation'",Integer.class,bytes(source.getId())));
        org.mockito.Mockito.verify(attribution,org.mockito.Mockito.times(1)).recordGeneration(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq(f.scene),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.argThat(m->Boolean.TRUE.equals(m.get("lengthOutOfRange"))),org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verifyNoInteractions(generator);
    }
    private Fixture fixture(){return transactions.execute(s->{
        String name="creator-"+UUID.randomUUID();var user=new User();user.setUsername(name);user.setEmail(name+"@example.invalid");user.setPasswordHash("test");em.persist(user);
        var story=new Story();story.setUser(user);story.setTitle("修复验收");story.setStatus("draft");em.persist(story);
        UUID scene=UUID.randomUUID();var outline=new Outline();outline.setStory(story);outline.setTitle("大纲");outline.setContentJson("{\"chapters\":[]}");em.persist(outline);em.flush();return new Fixture(user,outline.getId(),scene);
    });}
    private void login(User user){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user.getUsername(),"",List.of()));}
    private record Fixture(User user,UUID outline,UUID scene){}
}

