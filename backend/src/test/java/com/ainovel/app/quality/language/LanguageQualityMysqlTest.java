package com.ainovel.app.quality.language;
import com.ainovel.app.aioperation.*;
import com.ainovel.app.common.JsonColumnCodec;
import com.ainovel.app.manuscript.ManuscriptContentService;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.story.model.*;
import com.ainovel.app.user.User;
import com.ainovel.app.v2.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.ainovel.app.quality.language.LanguageDtos.*;

@EnabledIfEnvironmentVariable(named="AIENIE_AUDIT_MYSQL_URL",matches="jdbc:mysql:.*")
@DataJpaTest(showSql=false,properties={"spring.flyway.enabled=true","spring.jpa.hibernate.ddl-auto=none","spring.jpa.open-in-view=false","app.language.enabled=true"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
@Import({LanguageQualityService.class,LanguageFeature.class,ManuscriptContentService.class,V2VersionPersistenceService.class,JsonColumnCodec.class,LanguageQualityMysqlTest.Beans.class})
class LanguageQualityMysqlTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        String url=System.getenv("AIENIE_AUDIT_MYSQL_URL");
        if(url==null || !url.matches("jdbc:mysql://[^/]+/aienie_novel_audit_test_[A-Za-z0-9_]+(?:\\?.*)?")) throw new IllegalArgumentException("Isolated schema required");
        r.add("spring.datasource.url",()->url);r.add("spring.datasource.username",()->System.getenv("AIENIE_AUDIT_MYSQL_USERNAME"));r.add("spring.datasource.password",()->System.getenv("AIENIE_AUDIT_MYSQL_PASSWORD"));
        r.add("spring.datasource.driver-class-name",()->"com.mysql.cj.jdbc.Driver");r.add("spring.jpa.properties.hibernate.dialect",()->"org.hibernate.dialect.MySQLDialect");
    }
    @TestConfiguration static class Beans {
        @Bean static org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor versionJson() {
            return new org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor() {
                @Override public void postProcessBeanDefinitionRegistry(org.springframework.beans.factory.support.BeanDefinitionRegistry registry) {
                    var definition=new org.springframework.beans.factory.support.GenericBeanDefinition();
                    definition.setBeanClassName("com.ainovel.app.v2.V2Json"); registry.registerBeanDefinition("v2Json",definition);
                }
            };
        }
        @Bean ObjectMapper mapper(){return new ObjectMapper().findAndRegisterModules();}
        @Bean TransactionTemplate transactions(PlatformTransactionManager manager){return new TransactionTemplate(manager);}
    }
    @MockitoBean AiOperationService operations;
    @MockitoBean ResourceAccessGuard access;
    @Autowired LanguageQualityService quality;
    @Autowired V2VersionPersistenceService versions;
    @Autowired ManuscriptContentService contents;
    @Autowired ManuscriptRepository manuscripts;
    @Autowired LanguageReportRepository reports;
    @Autowired LanguagePatchRepository patches;
    @Autowired LanguageDecisionRepository decisions;
    @Autowired TransactionTemplate transactions;
    @Autowired ObjectMapper json;
    @Autowired javax.sql.DataSource dataSource;
    @PersistenceContext EntityManager em;
    @BeforeEach void tasks() {
        when(operations.submit(any(),anyString(),anyString(),any(),any(),anyInt(),anyString(),anyString())).thenAnswer(i->new AiOperationDtos.Accepted(UUID.randomUUID()));
        when(operations.get(any(),any())).thenAnswer(i->new AiOperationDtos.Progress(i.getArgument(1),"LANGUAGE_DIAGNOSIS","LANGUAGE_REPORT",null,AiOperationStatus.SUCCEEDED,"完成",1,1,0,0,false,1,null,null,null,null,null));
    }
    record Fixture(User user,UUID manuscript,UUID scene) {}
    Fixture fixture() { return transactions.execute(s->{
        var user=new User();String name="language-"+UUID.randomUUID();user.setUsername(name);user.setEmail(name+"@example.invalid");user.setPasswordHash("test");em.persist(user);
        var story=new Story();story.setTitle("语言验收");story.setUser(user);em.persist(story);
        var outline=new Outline();outline.setStory(story);outline.setTitle("大纲");outline.setContentJson("{\"chapters\":[]}");em.persist(outline);
        var m=new Manuscript();m.setOutline(outline);m.setTitle("语言正文");contents.initialize(m);em.persist(m);em.flush();UUID scene=UUID.randomUUID();
        contents.writeScene(m,scene,"<p>找这个动作还在。</p><p>她把书放回原处。</p>");em.flush();return new Fixture(user,m.getId(),scene);
    }); }
    Manuscript current(Fixture f) { return manuscripts.findById(f.manuscript).orElseThrow(); }
    String html(Fixture f) { return transactions.execute(s->contents.readScene(current(f),f.scene)); }
    UUID prepare(Fixture f) throws Exception {
        var m=current(f);quality.start(f.user,f.manuscript,f.scene,new CheckRequest(m.getCurrentBranchId(),m.getVersion()));
        var r=reports.findTop20ByManuscriptIdAndSceneIdOrderByCreatedAtDesc(f.manuscript,f.scene).getFirst();var input=quality.input(f.user,r.id);
        var result=LanguageAnalysis.parse("{\"complete\":true,\"issues\":[{\"kind\":\"LANGUAGE\",\"category\":\"AWKWARD\",\"quote\":\"找这个动作还在。\",\"impact\":\"抽象表达生硬\",\"direction\":\"交代人物想法\"}]}",input.data().projection,input.data().coverage.getFirst(),json);
        assertEquals(1,result.issues().size());
        quality.checked(f.user,r.id,0,result,null);quality.finish(f.user,r.id);
        assertEquals(Status.ISSUES,quality.get(f.user,f.manuscript,r.id).status());
        assertEquals(1,quality.get(f.user,f.manuscript,r.id).issues().size());
        quality.suggest(f.user,f.manuscript,r.id,"0-0");
        var p=patches.findByReportIdAndIssueId(r.id,"0-0").orElseThrow();
        quality.candidate(f.user,p.id,"{\"outcome\":\"CANDIDATE\",\"replacement\":\"她还是想去找寄信人。\"}");
        quality.reviewed(f.user,p.id,"{\"language\":\"PASS\",\"languageReason\":\"自然\",\"meaning\":\"PASS\",\"meaningReason\":\"保留想法\",\"changes\":[]}",null);return p.id;
    }
    DecisionResult accept(Fixture f,UUID patchId,String key,long version,UUID branch) {
        var p=patches.findById(patchId).orElseThrow();return quality.decide(f.user,f.manuscript,p.reportId,p.id,"accept",new DecisionRequest(branch,version),key);
    }
    @Test void needsContextSurvivesTransactionReloadAndNeverChangesBody() throws Exception {
        var f=fixture();var m=current(f);String body=html(f);
        quality.start(f.user,f.manuscript,f.scene,new CheckRequest(m.getCurrentBranchId(),m.getVersion()));
        var r=reports.findTop20ByManuscriptIdAndSceneIdOrderByCreatedAtDesc(f.manuscript,f.scene).getFirst();var input=quality.input(f.user,r.id);
        var result=LanguageAnalysis.parse("{\"complete\":true,\"issues\":[{\"kind\":\"LANGUAGE\",\"category\":\"OMISSION\",\"quote\":\"找这个动作还在。\",\"impact\":\"所指不明\",\"direction\":\"需要背景信息\"}]}",input.data().projection,input.data().coverage.getFirst(),json);
        quality.checked(f.user,r.id,0,result,null);quality.finish(f.user,r.id);var op=quality.suggest(f.user,f.manuscript,r.id,"0-0");
        var p=patches.findByReportIdAndIssueId(r.id,"0-0").orElseThrow();
        quality.candidate(f.user,p.id,"{\"outcome\":\"NEEDS_CONTEXT\",\"reason\":\"需要作者说明所指\",\"replacement\":null}");
        var restored=quality.patch(f.user,f.manuscript,r.id,p.id);
        assertEquals("NEEDS_CONTEXT",restored.status());assertEquals("需要作者说明所指",restored.reason());assertNull(restored.replacement());assertNull(restored.review());
        assertEquals(body,html(f));assertEquals(op,quality.suggest(f.user,f.manuscript,r.id,"0-0"));
        assertThrows(com.ainovel.app.common.ApiStatusException.class,()->accept(f,p.id,"no-candidate-accept",current(f).getVersion(),current(f).getCurrentBranchId()));
    }
    @Test void migrationPersistenceAtomicRollbackAndReplay() throws Exception {
        var f=fixture();var id=prepare(f);var before=current(f);String body=html(f);long receipts=decisions.count();
        quality.settings(f.user,f.manuscript,new SettingsRequest(true,true));
        assertTrue(quality.settings(f.user,f.manuscript).generationStandard());
        quality.settings(f.user,f.manuscript,new SettingsRequest(false,false));
        assertFalse(quality.settings(f.user,f.manuscript).generationStandard());
        assertThrows(IllegalStateException.class,()->transactions.executeWithoutResult(s->{ accept(f,id,"rollback-transaction",before.getVersion(),before.getCurrentBranchId());throw new IllegalStateException("force rollback"); }));
        assertEquals(body,html(f));assertEquals(before.getVersion(),current(f).getVersion());assertEquals(receipts,decisions.count());
        var accepted=accept(f,id,"atomic-replay",before.getVersion(),before.getCurrentBranchId());
        assertEquals(accepted,accept(f,id,"atomic-replay",before.getVersion(),before.getCurrentBranchId()));assertEquals(before.getVersion()+1,current(f).getVersion());
        assertTrue(html(f).contains("她还是想去找寄信人。"));
        var extra=transactions.execute(s->versions.createLanguageVersion(current(f),f.user,false));
        assertNotEquals(accepted.patch().appliedSnapshotId().toString(),extra.get("id").toString(),"Language decisions must not deduplicate their history entry");
        var p=patches.findById(id).orElseThrow();var current=current(f);quality.decide(f.user,f.manuscript,p.reportId,id,"undo",new DecisionRequest(current.getCurrentBranchId(),current.getVersion()),"undo-persisted");assertEquals(body,html(f));
    }
    @Test void adminReadsExcludeFailedAndIncompleteScoresBeforeFilteringAndPaging() {
        var f=fixture();String marker="admin-quality-"+UUID.randomUUID();
        var invalid=new HashSet<UUID>();
        transactions.executeWithoutResult(s->{
            UUID story=current(f).getOutline().getStory().getId();
            String[] snapshots={null,"{\"coverage\":\"PENDING\"}","{\"coverage\":\"COMPLETE\"","[]","{\"coverage\":\"FAILED\"}","{\"coverage\":\"COMPLETE\"}",null,null};
            for(int i=0;i<snapshots.length;i++) {
                var run=new com.ainovel.app.quality.model.SlopQualityRun();
                run.setStoryId(story);run.setManuscriptId(f.manuscript);run.setSceneId(f.scene);
                run.setStatus(i==0?com.ainovel.app.quality.SlopQualityStatus.DEGRADED:com.ainovel.app.quality.SlopQualityStatus.ACCEPTED);
                run.setMaxSeverity(i==6?com.ainovel.app.quality.SlopSeverity.LOW:com.ainovel.app.quality.SlopSeverity.HIGH);
                run.setOverallRiskScore(i==6?0:i==7?76:95);run.setSummary(marker);run.setReviewContextJson(snapshots[i]);
                em.persist(run);if(i<5)invalid.add(run.getId());
            }
            for(int i=0;i<3;i++) {
                var run=new com.ainovel.app.quality.model.PlotQualityRun();
                run.setStoryId(story);run.setManuscriptId(f.manuscript);run.setSceneId(f.scene);
                run.setStatus(i==0?com.ainovel.app.quality.PlotQualityStatus.DEGRADED:com.ainovel.app.quality.PlotQualityStatus.ACCEPTED);
                run.setMaxSeverity(i==2?com.ainovel.app.quality.PlotQualitySeverity.LOW:com.ainovel.app.quality.PlotQualitySeverity.HIGH);
                run.setOverallRiskScore(i==0?95:i==1?80:0);run.setSummary(marker);
                em.persist(run);if(i==0)invalid.add(run.getId());
            }
        });
        var jdbc=new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(dataSource);
        var read=new com.ainovel.app.admin.AdminOperationsReadRepository(jdbc);
        var all=read.quality(0,20,marker,"all");assertEquals(11,all.total());
        for(var row:all.items()) {
            if(invalid.contains(row.id())) { assertNull(row.overallRiskScore());assertNull(row.maxSeverity()); }
            else assertNotNull(row.overallRiskScore());
        }
        assertEquals(2,all.items().stream().filter(r->Integer.valueOf(0).equals(r.overallRiskScore())).count());
        var high=read.quality(0,2,marker,"high");assertEquals(3,high.total());assertEquals(2,high.items().size());
        assertEquals(1,read.quality(2,2,marker,"high").items().size());
        assertTrue(high.items().stream().noneMatch(r->invalid.contains(r.id())));
        assertEquals(3,read.highRiskQualityRuns());
        assertEquals(7,jdbc.getJdbcTemplate().queryForObject("select count(*) from (select overall_risk_score from slop_quality_runs union all select overall_risk_score from plot_quality_runs) q where overall_risk_score=95",Integer.class));
    }
    @Test void concurrentAcceptHasOneWriterAndSourceVersionRemainsHistorical() throws Exception {
        var f=fixture();var id=prepare(f);var m=current(f);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> task=()->{ start.await();try{accept(f,id,UUID.randomUUID().toString(),m.getVersion(),m.getCurrentBranchId());return true;}catch(com.ainovel.app.common.ApiStatusException e){assertEquals(409,e.getStatus().value());return false;} };
            var a=pool.submit(task);var b=pool.submit(task);start.countDown();assertNotEquals(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
        }
        var r=patches.findById(id).orElseThrow().reportId;assertEquals(Status.STALE,quality.get(f.user,f.manuscript,r).status());assertEquals(m.getVersion(),quality.get(f.user,f.manuscript,r).source().bodyVersion());
    }
}
