package com.ainovel.app.material.evidence;

import static com.ainovel.app.material.MaterialFingerprintService.bytes;

import static org.junit.jupiter.api.Assertions.*;

import com.ainovel.app.common.*;
import com.ainovel.app.manuscript.ManuscriptContentService;
import com.ainovel.app.material.model.Material;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.user.User;
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
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.concurrent.*;

@EnabledIfEnvironmentVariable(named = "AIENIE_AUDIT_MYSQL_URL", matches = "jdbc:mysql:.*")
@DataJpaTest(
        showSql = false,
        properties = {
            "spring.flyway.enabled=true",
            "spring.jpa.hibernate.ddl-auto=none",
            "spring.jpa.open-in-view=false",
            "spring.sql.init.mode=never",
            "app.material-evidence.verification-enabled=true",
            "app.material-evidence.semantic-enabled=true",
            "app.material-evidence.task-dispatch-ms=3600000"
        })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
    MaterialEvidenceService.class,
    MaterialConfirmationService.class,
    com.ainovel.app.material.MaterialService.class,
    com.ainovel.app.material.MaterialFingerprintService.class,
    MaterialMutationService.class,
    MaterialHintService.class,
    MaterialSemanticService.class,
    EvidenceTaskService.class,
    ManuscriptContentService.class,
    ResourceAccessGuard.class,
    JsonColumnCodec.class,
    MaterialEvidenceMysqlTest.Beans.class
})
class MaterialEvidenceMysqlTest {
    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry r) {
        String url = System.getenv("AIENIE_AUDIT_MYSQL_URL");
        if (url == null
                || !url.matches(
                        "jdbc:mysql://[^/]+/aienie_novel_audit_test_[A-Za-z0-9_]+(?:\\?.*)?"))
            throw new IllegalArgumentException("Isolated schema required");
        r.add("spring.datasource.url", () -> url);
        r.add("spring.datasource.username", () -> System.getenv("AIENIE_AUDIT_MYSQL_USERNAME"));
        r.add("spring.datasource.password", () -> System.getenv("AIENIE_AUDIT_MYSQL_PASSWORD"));
        r.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        r.add(
                "spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.MySQLDialect");
    }

    @TestConfiguration
    static class Beans {
        @Bean
        ObjectMapper json() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean
        TransactionTemplate transactions(PlatformTransactionManager manager) {
            return new TransactionTemplate(manager);
        }
    }

    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired MaterialEvidenceService service;
    @Autowired MaterialConfirmationService confirmations;
    @Autowired EvidenceTaskService tasks;
    @Autowired ManuscriptContentService contents;
    @Autowired MaterialMutationService mutations;
    @Autowired MaterialHintService hints;
    @Autowired MaterialSemanticService semantics;
    @Autowired ObjectMapper json;
    @Autowired JsonColumnCodec codec;
    @MockitoBean MaterialSearchService search;
    @Autowired com.ainovel.app.material.MaterialService legacy;
    @MockitoBean com.ainovel.app.material.MaterialRetrievalService retrieval;
    @MockitoBean CurrentUserResolver resolver;
    @MockitoBean MaterialNativeClient nativeClient;
    @MockitoBean com.ainovel.app.economy.EconomyService economy;
    @MockitoBean com.ainovel.app.narrative.NarrativeService narrative;

    record Fixture(User user, UUID story, UUID material) {}

    @Test
    void provenBudgetBusyWaitsArePersistentBoundedAndKeepOriginalCreditHold() {
        var f = fixture("日落后通行。");
        String revision = service.statuses(f.user).getFirst().revisionId();
        var request =
                new EvidenceTaskDtos.Request(
                        "EXTRACT",
                        null,
                        null,
                        null,
                        null,
                        revision,
                        "提取条件",
                        1,
                        UUID.randomUUID().toString());
        var preview = tasks.preview(f.user, request);
        var task =
                tasks.submit(
                        f.user, new EvidenceTaskDtos.Submit(request, preview.fingerprint(), 11));
        org.mockito.Mockito.when(
                        nativeClient.structured(
                                org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.eq(
                                        "evidence:" + task.id() + ":report"),
                                org.mockito.ArgumentMatchers.anyList(),
                                org.mockito.ArgumentMatchers.anyString(),
                                org.mockito.ArgumentMatchers.eq(4096),
                                org.mockito.ArgumentMatchers.eq("")))
                .thenThrow(
                        io.grpc.Status.FAILED_PRECONDITION
                                .withDescription("RETRIEVAL_BUDGET_IN_FLIGHT")
                                .asRuntimeException());
        for (int attempt = 0; attempt < 4; attempt++) {
            runClaimed(task.id());
            var state =
                    jdbc.queryForMap(
                            "select"
                                + " status,budget_waits,calls_reserved,gateway_user_id,evaluation_run_id,coalesce(not_before>CURRENT_TIMESTAMP(6),false)"
                                + " as future from material_processing_jobs where id=?",
                            task.id());
            assertEquals(attempt < 3 ? "QUEUED" : "RECONCILIATION_REQUIRED", state.get("status"));
            assertEquals(attempt + 1, ((Number) state.get("budget_waits")).intValue());
            assertEquals(0, ((Number) state.get("calls_reserved")).intValue());
            assertEquals(
                    f.user.getRemoteUid(), ((Number) state.get("gateway_user_id")).longValue());
            assertEquals("", state.get("evaluation_run_id"));
            assertNotEquals(0, ((Number) state.get("future")).intValue());
        }
        org.mockito.Mockito.verify(economy, org.mockito.Mockito.never())
                .markAiResultUncertain(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString());
        org.mockito.Mockito.verify(economy, org.mockito.Mockito.never())
                .releaseAiReservation(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString());
        org.mockito.Mockito.verify(nativeClient, org.mockito.Mockito.times(4))
                .structured(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq("evidence:" + task.id() + ":report"),
                        org.mockito.ArgumentMatchers.anyList(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq(4096),
                        org.mockito.ArgumentMatchers.eq(""));
    }

    @Test
    void reportCompletionAtomicallyStoresMachineAssociationsAndRejectsLateSource()
            throws Exception {
        var f = fixture("😀日落后通行。");
        UUID scene = UUID.randomUUID();
        UUID[] ids = bodyFixture(f, scene);
        String revision = service.statuses(f.user).getFirst().revisionId();
        String bodyId = "body:" + scene + ":b1";
        var opened =
                List.of(
                        new EvidenceTaskDtos.Opened(
                                bodyId,
                                "BODY",
                                "😀日落后通行。",
                                null,
                                ids[2],
                                scene.toString(),
                                0,
                                "evidence-v1"),
                        new EvidenceTaskDtos.Opened(
                                "reference:fixture",
                                "REFERENCE",
                                "😀日落后通行。",
                                revision,
                                null,
                                null,
                                0,
                                "raw-codepoint-v1"));
        var frozen =
                new EvidenceTaskDtos.Frozen(
                        f.story,
                        ids[0],
                        ids[1],
                        ids[2],
                        service.citationBody(f.user, ids[0], scene.toString()).manuscriptVersion(),
                        "日落后是否能通行",
                        opened,
                        List.of(),
                        "fixture",
                        0,
                        0,
                        0,
                        ids[1],
                        List.of());
        var report =
                json.readTree(
                        "{\"coverage\":\"配对逐字证据\",\"findings\":[{\"kind\":\"FACT\",\"claim\":\"日落后通行\",\"decision\":\"SUPPORTED\",\"condition\":\"日落后\",\"evidence\":[{\"sourceId\":\""
                                + bodyId
                                + "\",\"quote\":\"日落后\",\"start\":1,\"end\":4},{\"sourceId\":\"reference:fixture\",\"quote\":\"日落后\",\"start\":1,\"end\":4}]}]}");
        String task = insertClaimedReport(f, frozen, "token-first");
        var bad = report.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode)
                        bad.path("findings").path(0).path("evidence").path(0))
                .put("start", 2);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        tasks.completeReport(
                                f.user,
                                task,
                                "token-first",
                                frozen,
                                bad,
                                reportResult(frozen, bad),
                                false));
        assertEquals(
                "PROCESSING",
                jdbc.queryForObject(
                        "select status from material_processing_jobs where id=?",
                        String.class,
                        task));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "select count(*) from material_source_links where report_task_id=?",
                        Integer.class,
                        task));
        tasks.completeReport(
                f.user, task, "token-first", frozen, report, reportResult(frozen, report), false);
        var link = service.citations(f.user, ids[0]).getFirst();
        assertEquals("POSSIBLE", link.get("relation_type"));
        assertEquals("CURRENT", link.get("state"));
        assertEquals(task, link.get("report_task_id"));
        assertEquals("b1", link.get("body_block_id"));
        assertEquals(1, ((Number) link.get("start_cp")).intValue());
        assertEquals(4, ((Number) link.get("body_end_cp")).intValue());
        assertEquals(
                "COMPLETED",
                jdbc.queryForObject(
                        "select status from material_processing_jobs where id=?",
                        String.class,
                        task));
        tasks.completeReport(
                f.user, task, "token-first", frozen, report, reportResult(frozen, report), false);
        assertEquals(
                1,
                jdbc.queryForObject(
                        "select count(*) from material_source_links where report_task_id=?",
                        Integer.class,
                        task));
        tx.executeWithoutResult(
                s -> {
                    var material = em.find(Material.class, f.material);
                    material.setContentVersion(2);
                    material.setContent("白天已关闭。");
                    em.flush();
                    service.capture(material);
                });
        String late = insertClaimedReport(f, frozen, "token-late");
        tasks.completeReport(
                f.user, late, "token-late", frozen, report, reportResult(frozen, report), false);
        assertEquals(
                "STALE",
                jdbc.queryForObject(
                        "select status from material_processing_jobs where id=?",
                        String.class,
                        late));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "select count(*) from material_source_links where report_task_id=?",
                        Integer.class,
                        late));
        assertEquals("REVIEW_REQUIRED", service.citations(f.user, ids[0]).getFirst().get("state"));
        assertEquals(
                "<p>😀日落后通行。</p>",
                tx.execute(
                        s ->
                                contents.readScene(
                                        em.find(
                                                com.ainovel.app.manuscript.model.Manuscript.class,
                                                ids[0]),
                                        scene)));
        assertEquals(
                0L,
                jdbc.queryForObject(
                        "select revision from narrative_ledgers where branch_id=?",
                        Long.class,
                        bytes(ids[1])));
        org.mockito.Mockito.verifyNoInteractions(nativeClient, economy, narrative);
    }

    private com.fasterxml.jackson.databind.node.ObjectNode reportResult(
            EvidenceTaskDtos.Frozen frozen, com.fasterxml.jackson.databind.JsonNode report) {
        var result = json.createObjectNode();
        result.set("report", report);
        result.set("opened", json.valueToTree(frozen.opened()));
        result.set("exclusions", json.valueToTree(frozen.exclusions()));
        return result;
    }

    private String insertClaimedReport(Fixture f, EvidenceTaskDtos.Frozen frozen, String token) {
        String id = UUID.randomUUID().toString();
        jdbc.update(
                "insert into"
                    + " material_processing_jobs(id,owner_id,kind,request_key,request_hash,input_json,call_limit,status,lease_token)"
                    + " values(?,?,'AUTO_CHECK',?, ?,?,8,'PROCESSING',?)",
                id,
                bytes(f.user.getId()),
                id,
                "fixture",
                codec.writeRequired(frozen),
                token);
        return id;
    }

    private UUID[] bodyFixture(Fixture f, UUID scene) {
        return tx.execute(
                s -> {
                    var outline = new com.ainovel.app.story.model.Outline();
                    outline.setStory(em.find(Story.class, f.story));
                    outline.setTitle("引用大纲");
                    outline.setContentJson("{\"chapters\":[]}");
                    em.persist(outline);
                    var manuscript = new com.ainovel.app.manuscript.model.Manuscript();
                    manuscript.setOutline(outline);
                    manuscript.setTitle("引用稿件");
                    manuscript.setSectionsJson(
                            codec.writeRequired(Map.of(scene.toString(), "<p>😀日落后通行。</p>")));
                    em.persist(manuscript);
                    em.flush();
                    var branch = new com.ainovel.app.v2.model.V2ManuscriptBranch();
                    branch.setManuscript(manuscript);
                    branch.setName("主线");
                    branch.setStatus("active");
                    branch.setMain(true);
                    em.persist(branch);
                    em.flush();
                    manuscript.setCurrentBranchId(branch.getId());
                    var version = new com.ainovel.app.v2.model.V2ManuscriptVersion();
                    version.setManuscript(manuscript);
                    version.setBranch(branch);
                    version.setCreatedBy(em.find(User.class, f.user.getId()));
                    version.setSnapshotType("manual");
                    version.setContentHash("hash");
                    version.setVersionNumber(1);
                    version.setSectionsJson(manuscript.getSectionsJson());
                    em.persist(version);
                    em.flush();
                    jdbc.update(
                            "insert into narrative_ledgers(branch_id) values(?)",
                            bytes(branch.getId()));
                    return new UUID[] {manuscript.getId(), branch.getId(), version.getId()};
                });
    }

    @Test
    void semanticRecoveryPreservesOriginalIdentityAndConcurrentIntentWithoutInference()
            throws Exception {
        var f = fixture("索引恢复原文。");
        String revision = service.statuses(f.user).getFirst().revisionId();
        String profile = "qwen-standard-1024-cp-v1";
        Long originalUid = f.user.getRemoteUid();
        jdbc.update(
                "insert into"
                    + " material_semantic_jobs(revision_id,profile,model,dimensions,template_version,chunk_version,status,request_owner_id,gateway_user_id,evaluation_run_id)"
                    + " values(?,?,?,1024,'document-v1','cp900-120-v1','RECONCILIATION_REQUIRED',?,?,?)",
                revision,
                profile,
                "qwen3.7-text-embedding",
                bytes(f.user.getId()),
                originalUid,
                "original-run");
        var request = new EvidenceDtos.SemanticResumeWrite(1, UUID.randomUUID().toString());
        tx.executeWithoutResult(
                s -> em.find(User.class, f.user.getId()).setRemoteUid(originalUid + 1));
        assertEquals(
                409,
                assertThrows(
                                ApiStatusException.class,
                                () -> semantics.resume(f.user, revision, profile, request))
                        .getStatus()
                        .value());
        assertEquals(
                "RECONCILIATION_REQUIRED",
                jdbc.queryForObject(
                        "select status from material_semantic_jobs where revision_id=? and"
                                + " profile=?",
                        String.class,
                        revision,
                        profile));
        tx.executeWithoutResult(s -> em.find(User.class, f.user.getId()).setRemoteUid(originalUid));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> semantics.resume(f.user, revision, profile, request));
            var b = pool.submit(() -> semantics.resume(f.user, revision, profile, request));
            assertEquals(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        }
        var row =
                jdbc.queryForMap(
                        "select gateway_user_id,evaluation_run_id,status from"
                                + " material_semantic_jobs where revision_id=? and profile=?",
                        revision,
                        profile);
        assertEquals(originalUid, ((Number) row.get("gateway_user_id")).longValue());
        assertEquals("original-run", row.get("evaluation_run_id"));
        assertEquals("QUEUED", row.get("status"));
        assertEquals(
                409,
                assertThrows(
                                ApiStatusException.class,
                                () ->
                                        semantics.resume(
                                                f.user,
                                                revision,
                                                profile,
                                                new EvidenceDtos.SemanticResumeWrite(
                                                        2, request.requestKey())))
                        .getStatus()
                        .value());
        var stranger = fixture("无权限资料。");
        assertEquals(
                404,
                assertThrows(
                                ApiStatusException.class,
                                () -> semantics.resume(stranger.user, revision, profile, request))
                        .getStatus()
                        .value());
        String legacyProfile = "qwen-flash-1024-cp-v1";
        jdbc.update(
                "insert into"
                    + " material_semantic_jobs(revision_id,profile,model,dimensions,template_version,chunk_version,status,request_owner_id)"
                    + " values(?,?,?,1024,'document-v1','cp900-120-v1','RECONCILIATION_REQUIRED',?)",
                revision,
                legacyProfile,
                "qwen3.7-text-embedding-flash",
                bytes(f.user.getId()));
        assertEquals(
                409,
                assertThrows(
                                ApiStatusException.class,
                                () ->
                                        semantics.resume(
                                                f.user,
                                                revision,
                                                legacyProfile,
                                                new EvidenceDtos.SemanticResumeWrite(
                                                        1, UUID.randomUUID().toString())))
                        .getStatus()
                        .value());
        org.mockito.Mockito.verifyNoInteractions(nativeClient);
    }

    @Test
    void hintClaimIsSharedAcrossInstancesAndCachedResultsAreRecheckedAfterRevocation()
            throws Exception {
        var f = fixture("日落后才准许通行。");
        UUID scene = UUID.randomUUID();
        UUID manuscript =
                tx.execute(
                        s -> {
                            var outline = new com.ainovel.app.story.model.Outline();
                            outline.setStory(em.find(Story.class, f.story));
                            outline.setTitle("自动提示大纲");
                            outline.setContentJson(
                                    "{\"chapters\":[{\"scenes\":[{\"id\":\"" + scene + "\"}]}]}");
                            em.persist(outline);
                            var m = new com.ainovel.app.manuscript.model.Manuscript();
                            m.setOutline(outline);
                            m.setTitle("提示稿件");
                            m.setSectionsJson("{}");
                            em.persist(m);
                            em.flush();
                            return m.getId();
                        });
        service.saveSettings(
                f.user,
                f.story,
                new EvidenceDtos.SettingsWrite(
                        0,
                        UUID.randomUUID().toString(),
                        "basic",
                        false,
                        true,
                        false,
                        List.of(f.material)));
        var query = new EvidenceDtos.Search("通行", f.story, "fact", "bound", 8);
        var result = service.search(f.user, query);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        org.mockito.Mockito.when(search.search(f.user, query))
                .thenAnswer(
                        call -> {
                            entered.countDown();
                            assertTrue(release.await(20, TimeUnit.SECONDS));
                            return result;
                        });
        var request = new EvidenceDtos.Hint(manuscript, scene, "通行");
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> hints.hints(f.user, request));
            assertTrue(entered.await(20, TimeUnit.SECONDS));
            assertTrue(hints.hints(f.user, request).items().isEmpty());
            release.countDown();
            assertFalse(first.get(20, TimeUnit.SECONDS).items().isEmpty());
        } finally {
            release.countDown();
        }
        assertFalse(hints.hints(f.user, request).items().isEmpty());
        org.mockito.Mockito.verify(search, org.mockito.Mockito.times(1)).search(f.user, query);
        jdbc.update("update materials set status='pending' where id=?", bytes(f.material));
        assertTrue(hints.hints(f.user, request).items().isEmpty());
        org.mockito.Mockito.verify(search, org.mockito.Mockito.times(1)).search(f.user, query);
        jdbc.update(
                "update material_hint_slots set observed_at=DATE_SUB(CURRENT_TIMESTAMP(6),interval"
                        + " 31 second) where manuscript_id=?",
                bytes(manuscript));
        org.mockito.Mockito.when(search.search(f.user, query))
                .thenReturn(new EvidenceDtos.Results("fact", "bound", List.of(), List.of()));
        assertTrue(hints.hints(f.user, request).items().isEmpty());
        org.mockito.Mockito.verify(search, org.mockito.Mockito.times(2)).search(f.user, query);
    }

    private void authenticate(User user) {
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(
                        new org.springframework.security.authentication
                                .UsernamePasswordAuthenticationToken(
                                user.getUsername(), "unused", List.of()));
    }

    @AfterEach
    void clearAuthentication() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void concurrentTxtUploadFreezesOneRawRevisionAndDeletedReceiptCannotRecreateIt()
            throws Exception {
        var f = fixture("原资料。");
        String key = UUID.randomUUID().toString();
        String text = "😀雨桥只在日落后开放。\n第二段原文。";
        var start = new CountDownLatch(1);
        com.ainovel.app.material.dto.FileImportJobDto result;
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<com.ainovel.app.material.dto.FileImportJobDto> submit =
                    () -> {
                        authenticate(f.user);
                        try {
                            start.await();
                            return mutations.upload(f.user, "本轮导入.txt", text, key);
                        } finally {
                            org.springframework.security.core.context.SecurityContextHolder
                                    .clearContext();
                        }
                    };
            var a = pool.submit(submit);
            var b = pool.submit(submit);
            start.countDown();
            result = a.get(20, TimeUnit.SECONDS);
            assertEquals(result, b.get(20, TimeUnit.SECONDS));
        }
        assertNotNull(result.materialId());
        assertEquals(
                1,
                jdbc.queryForObject(
                        "select count(*) from material_revisions where material_id=?",
                        Integer.class,
                        bytes(result.materialId())));
        assertEquals(
                text,
                jdbc.queryForObject(
                        "select content from material_revisions where material_id=?",
                        String.class,
                        bytes(result.materialId())));
        var state =
                service.statuses(f.user).stream()
                        .filter(s -> s.materialId().equals(result.materialId()))
                        .findFirst()
                        .orElseThrow();
        assertEquals("pending", state.review());
        assertEquals("WITHHELD", state.basic());
        authenticate(f.user);
        assertEquals(
                409,
                assertThrows(
                                ApiStatusException.class,
                                () -> mutations.upload(f.user, "本轮导入.txt", "修改的原文", key))
                        .getStatus()
                        .value());
        legacy.delete(result.materialId());
        assertTrue(legacy.list(f.user).stream().noneMatch(m -> m.id().equals(result.materialId())));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "select count(*) from material_revisions where material_id=?",
                        Integer.class,
                        bytes(result.materialId())));
        assertEquals(
                410,
                assertThrows(
                                ApiStatusException.class,
                                () -> mutations.upload(f.user, "本轮导入.txt", text, key))
                        .getStatus()
                        .value());
        org.mockito.Mockito.verifyNoInteractions(nativeClient);
    }

    private Fixture fixture(String text) {
        var result =
                tx.execute(
                        s -> {
                            var user = new User();
                            String name = "evidence-" + UUID.randomUUID();
                            user.setUsername(name);
                            user.setEmail(name + "@example.invalid");
                            user.setPasswordHash("test");
                            user.setRemoteUid(
                                    ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));
                            em.persist(user);
                            var story = new Story();
                            story.setUser(user);
                            story.setTitle("证据测试");
                            story.setStatus("draft");
                            em.persist(story);
                            var material = new Material();
                            material.setUser(user);
                            material.setTitle("雨桥通行条件");
                            material.setContent(text);
                            material.setStatus("approved");
                            material.setTagsJson("[]");
                            em.persist(material);
                            em.flush();
                            service.capture(material);
                            return new Fixture(user, story.getId(), material.getId());
                        });
        project(result.material);
        return result;
    }

    private void project(UUID material) {
        for (int i = 0; i < 200; i++) {
            String status =
                    jdbc.queryForObject(
                            "select j.status from material_basic_jobs j join material_revisions r"
                                + " on r.id=j.revision_id join materials m on m.id=r.material_id"
                                + " and m.content_version=r.content_version where m.id=?",
                            String.class,
                            bytes(material));
            if (status.equals("COMPLETED")) return;
            service.backfill(2);
        }
        fail("Basic projection did not complete");
    }

    @Test
    void baseIndexExistsBeforeSemanticAndVisibilityHonorsBindingVersionRevocation() {
        var owner = fixture("😀日落后才准许通行。尚未获知暗号。");
        var stranger = fixture("其他作品。");
        assertTrue(
                service.search(
                                owner.user,
                                new EvidenceDtos.Search("通行", owner.story, "fact", "bound", 8))
                        .items()
                        .isEmpty());
        var settings =
                service.saveSettings(
                        owner.user,
                        owner.story,
                        new EvidenceDtos.SettingsWrite(
                                0,
                                UUID.randomUUID().toString(),
                                "basic",
                                false,
                                false,
                                false,
                                List.of(owner.material)));
        var found =
                service.search(
                        owner.user, new EvidenceDtos.Search("通行", owner.story, "fact", "bound", 8));
        assertFalse(found.items().isEmpty());
        var old = found.items().getFirst();
        assertEquals(old.text(), EvidenceText.slice("😀日落后才准许通行。尚未获知暗号。", old.start(), old.end()));
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () ->
                        service.search(
                                stranger.user,
                                new EvidenceDtos.Search("通行", owner.story, "fact", "personal", 8)));
        assertThrows(ApiStatusException.class, () -> service.open(stranger.user, old.chunkId()));
        tx.executeWithoutResult(
                s -> {
                    var m = em.find(Material.class, owner.material);
                    m.setContentVersion(2);
                    m.setContent("桥已关闭。");
                    em.flush();
                    service.capture(m);
                });
        assertTrue(
                service.visibleHits(
                                owner.user,
                                new EvidenceDtos.Search("通行", owner.story, "fact", "bound", 8),
                                List.of(old.chunkId()))
                        .isEmpty());
        assertEquals(
                2,
                jdbc.queryForObject(
                        "select count(*) from material_revisions where material_id=?",
                        Integer.class,
                        bytes(owner.material)));
        assertEquals(
                "😀日落后才准许通行。尚未获知暗号。", service.rawRevision(owner.user, old.revisionId()).content());
        assertEquals(
                List.of(2L, 1L),
                service.revisions(owner.user, owner.material, 0).stream()
                        .map(EvidenceDtos.SourceRevision::version)
                        .toList());
        assertTrue(service.revisions(stranger.user, owner.material, 0).isEmpty());
        assertThrows(
                ApiStatusException.class,
                () -> service.rawRevision(stranger.user, old.revisionId()));
        jdbc.update("update materials set status='pending' where id=?", bytes(owner.material));
        assertTrue(
                service.search(
                                owner.user,
                                new EvidenceDtos.Search("关闭", owner.story, "fact", "bound", 8))
                        .items()
                        .isEmpty());
        assertEquals(1, settings.version());
    }

    @Test
    void concurrentSameIntentReturnsOneSettingsRevisionAndChangedRequestConflicts()
            throws Exception {
        var f = fixture("并发绑定");
        var request =
                new EvidenceDtos.SettingsWrite(
                        0,
                        UUID.randomUUID().toString(),
                        "basic",
                        false,
                        false,
                        false,
                        List.of(f.material));
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<EvidenceDtos.Settings> submit =
                    () -> {
                        start.await();
                        return service.saveSettings(f.user, f.story, request);
                    };
            var a = pool.submit(submit);
            var b = pool.submit(submit);
            start.countDown();
            assertEquals(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        }
        assertEquals(1, service.settings(f.user, f.story).version());
        assertEquals(
                409,
                assertThrows(
                                ApiStatusException.class,
                                () ->
                                        service.saveSettings(
                                                f.user,
                                                f.story,
                                                new EvidenceDtos.SettingsWrite(
                                                        0,
                                                        request.requestKey(),
                                                        "basic",
                                                        false,
                                                        true,
                                                        false,
                                                        List.of(f.material))))
                        .getStatus()
                        .value());
        assertEquals(
                409,
                assertThrows(
                                ApiStatusException.class,
                                () ->
                                        service.saveSettings(
                                                f.user,
                                                f.story,
                                                new EvidenceDtos.SettingsWrite(
                                                        0,
                                                        UUID.randomUUID().toString(),
                                                        "basic",
                                                        false,
                                                        false,
                                                        false,
                                                        List.of(f.material))))
                        .getStatus()
                        .value());
    }

    @Test
    void confirmedAliasesAreVersionSpecificAndEntityListEnumeratesEveryChunk() {
        var f = fixture("桥梁的设定。".repeat(220));
        var input =
                new EvidenceDtos.EntityWrite(
                        "雨桥", List.of("青梧渡"), Map.of(f.material, 1L), UUID.randomUUID().toString());
        var entity = service.createEntity(f.user, input);
        assertEquals(entity, service.createEntity(f.user, input));
        var results = service.entitySources(f.user, f.story, entity.id(), 0);
        assertTrue(results.size() > 1);
        var request = new EvidenceDtos.Search("青梧渡", f.story, "fact", "personal", 40);
        assertFalse(service.search(f.user, request).items().isEmpty());
        tx.executeWithoutResult(
                s -> {
                    var m = em.find(Material.class, f.material);
                    m.setContentVersion(2);
                    m.setContent("与旧实体无关的新资料。");
                    em.flush();
                    service.capture(m);
                });
        assertTrue(service.entitySources(f.user, f.story, entity.id(), 0).isEmpty());
        assertTrue(service.search(f.user, request).items().isEmpty());
    }

    @Test
    void authorConfirmationIsIdempotentAndCannotSurviveSourceRevision() {
        var f = fixture("日落后才准许通行。");
        String revision = service.statuses(f.user).getFirst().revisionId();
        String task = UUID.randomUUID().toString();
        String analysis =
                "{\"analysis\":{\"coverage\":\"单条资料\",\"candidates\":[{\"type\":\"TAG\",\"name\":\"桥梁规约\",\"aliases\":[],\"statement\":\"\",\"condition\":\"日落后\",\"evidence\":[{\"sourceId\":\"source:"
                        + revision
                        + "\",\"quote\":\"日落后\",\"start\":0,\"end\":3}]}]}}";
        jdbc.update(
                "insert into"
                    + " material_processing_jobs(id,owner_id,kind,request_key,request_hash,source_revision,input_json,result_json,status,call_limit)"
                    + " values(?,?,'EXTRACT',?,?,?,'{}',?,'COMPLETED',8)",
                task,
                bytes(f.user.getId()),
                UUID.randomUUID().toString(),
                "hash",
                revision,
                analysis);
        var input = new EvidenceDtos.ConfirmCandidate(0, 1, UUID.randomUUID().toString());
        var confirmed = confirmations.confirm(f.user, task, input);
        assertEquals(confirmed.id(), confirmations.confirm(f.user, task, input).id());
        assertEquals(1, confirmations.list(f.user, revision).size());
        assertFalse(
                service.search(
                                f.user,
                                new EvidenceDtos.Search("桥梁规约", f.story, "fact", "personal", 8))
                        .items()
                        .isEmpty());
        var stranger = fixture("其他资料");
        assertThrows(
                ApiStatusException.class,
                () ->
                        confirmations.confirm(
                                stranger.user,
                                task,
                                new EvidenceDtos.ConfirmCandidate(
                                        0, 1, UUID.randomUUID().toString())));
        tx.executeWithoutResult(
                s -> {
                    var m = em.find(Material.class, f.material);
                    m.setContentVersion(2);
                    m.setContent("新版本。");
                    em.flush();
                    service.capture(m);
                });
        assertThrows(
                ApiStatusException.class,
                () ->
                        confirmations.confirm(
                                f.user,
                                task,
                                new EvidenceDtos.ConfirmCandidate(
                                        0, 2, UUID.randomUUID().toString())));
        assertTrue(
                service.search(
                                f.user,
                                new EvidenceDtos.Search("桥梁规约", f.story, "fact", "personal", 8))
                        .items()
                        .isEmpty());
    }

    @Test
    void concurrentTaskSubmissionReservesCreditsOnceAndCancellationDuringReservationReleasesHold()
            throws Exception {
        var f = fixture("日落后通行。");
        String revision = service.statuses(f.user).getFirst().revisionId();
        var request =
                new EvidenceTaskDtos.Request(
                        "EXTRACT",
                        null,
                        null,
                        null,
                        null,
                        revision,
                        "提取实体和条件",
                        1,
                        UUID.randomUUID().toString());
        var preview = tasks.preview(f.user, request);
        var submit = new EvidenceTaskDtos.Submit(request, preview.fingerprint(), 11);
        var reserving = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        org.mockito.Mockito.when(
                        economy.reserveAiUsage(
                                org.mockito.ArgumentMatchers.eq(f.user),
                                org.mockito.ArgumentMatchers.eq(11L),
                                org.mockito.ArgumentMatchers.eq("EVIDENCE_TASK"),
                                org.mockito.ArgumentMatchers.anyString(),
                                org.mockito.ArgumentMatchers.anyString(),
                                org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(
                        call -> {
                            reserving.countDown();
                            assertTrue(release.await(20, TimeUnit.SECONDS));
                            return com.ainovel.app.economy.EconomyService.AiReservationStart
                                    .reserved();
                        });
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> tasks.submit(f.user, submit));
            assertTrue(reserving.await(20, TimeUnit.SECONDS));
            var replay = tasks.submit(f.user, submit);
            assertEquals("RESERVING_CREDIT", replay.status());
            assertEquals("CANCELLED", tasks.cancel(f.user, replay.id()).status());
            release.countDown();
            assertEquals("CANCELLED", first.get(20, TimeUnit.SECONDS).status());
            org.mockito.Mockito.verify(economy, org.mockito.Mockito.times(1))
                    .releaseAiReservation(f.user, "evidence:" + replay.id());
        } finally {
            release.countDown();
        }
        org.mockito.Mockito.verify(economy, org.mockito.Mockito.times(1))
                .reserveAiUsage(
                        org.mockito.ArgumentMatchers.eq(f.user),
                        org.mockito.ArgumentMatchers.eq(11L),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString());
        org.mockito.Mockito.verifyNoInteractions(nativeClient);
    }

    @Test
    void knownProviderResultSurvivesSettlementFailureAndRecoveryNeverCallsModelAgain() {
        var f = fixture("日落后通行。");
        String revision = service.statuses(f.user).getFirst().revisionId();
        var request =
                new EvidenceTaskDtos.Request(
                        "EXTRACT",
                        null,
                        null,
                        null,
                        null,
                        revision,
                        "提取条件",
                        1,
                        UUID.randomUUID().toString());
        var preview = tasks.preview(f.user, request);
        var task =
                tasks.submit(
                        f.user, new EvidenceTaskDtos.Submit(request, preview.fingerprint(), 11));
        String raw =
                "{\"coverage\":\"单条资料\",\"candidates\":[{\"type\":\"TAG\",\"name\":\"通行\",\"aliases\":[],\"statement\":\"\",\"condition\":\"日落后\",\"evidence\":[{\"sourceId\":\"source:"
                        + revision
                        + "\",\"quote\":\"日落后\",\"start\":0,\"end\":3}]}]}";
        org.mockito.Mockito.when(
                        nativeClient.structured(
                                org.mockito.ArgumentMatchers.argThat(
                                        user -> user.getId().equals(f.user.getId())),
                                org.mockito.ArgumentMatchers.eq(
                                        "evidence:" + task.id() + ":report"),
                                org.mockito.ArgumentMatchers.anyList(),
                                org.mockito.ArgumentMatchers.anyString(),
                                org.mockito.ArgumentMatchers.eq(4096),
                                org.mockito.ArgumentMatchers.eq("")))
                .thenReturn(
                        new com.ainovel.app.integration.AiGatewayGrpcClient.ChatResult(
                                raw, "qwen", 100, 30, 0));
        org.mockito.Mockito.when(
                        economy.settleAiUsage(
                                org.mockito.ArgumentMatchers.argThat(
                                        user -> user.getId().equals(f.user.getId())),
                                org.mockito.ArgumentMatchers.eq("evidence:" + task.id()),
                                org.mockito.ArgumentMatchers.eq(raw),
                                org.mockito.ArgumentMatchers.eq(100L),
                                org.mockito.ArgumentMatchers.eq(30L),
                                org.mockito.ArgumentMatchers.eq(0L)))
                .thenThrow(new IllegalStateException("temporary settlement failure"))
                .thenReturn(new com.ainovel.app.economy.EconomyService.AiChargeResult(1, 99));
        runClaimed(task.id());
        assertEquals("RECONCILIATION_REQUIRED", tasks.task(f.user, task.id()).status());
        assertNotNull(
                jdbc.queryForObject(
                        "select provider_response_json from material_processing_jobs where id=?",
                        String.class,
                        task.id()));
        jdbc.update(
                "update material_processing_jobs set calls_reserved=call_limit where id=?",
                task.id());
        assertEquals("RECOVERY_QUEUED", tasks.resume(f.user, task.id()).status());
        assertEquals("RECOVERY_QUEUED", tasks.resume(f.user, task.id()).status());
        runClaimed(task.id());
        var recovered = tasks.task(f.user, task.id());
        assertEquals("COMPLETED", recovered.status());
        assertNotNull(recovered.result());
        org.mockito.Mockito.verify(nativeClient, org.mockito.Mockito.times(1))
                .structured(
                        org.mockito.ArgumentMatchers.argThat(
                                user -> user.getId().equals(f.user.getId())),
                        org.mockito.ArgumentMatchers.eq("evidence:" + task.id() + ":report"),
                        org.mockito.ArgumentMatchers.anyList(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq(4096),
                        org.mockito.ArgumentMatchers.eq(""));
        org.mockito.Mockito.verify(economy, org.mockito.Mockito.times(1))
                .reserveAiUsage(
                        org.mockito.ArgumentMatchers.eq(f.user),
                        org.mockito.ArgumentMatchers.eq(11L),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString());
    }

    private void runClaimed(String id) {
        var row = jdbc.queryForMap("select * from material_processing_jobs where id=?", id);
        if (row.get("gateway_user_id") == null
                && ((Number) row.get("calls_reserved")).intValue() == 0) {
            row.put(
                    "gateway_user_id",
                    jdbc.queryForObject(
                            "select remote_uid from users where id=?",
                            Long.class,
                            row.get("owner_id")));
            row.put("evaluation_run_id", "");
        }
        String token = UUID.randomUUID().toString();
        assertEquals(
                1,
                jdbc.update(
                        "update material_processing_jobs set"
                            + " gateway_user_id=?,evaluation_run_id=?,status='PROCESSING',lease_token=?,calls_reserved=calls_reserved+case"
                            + " when provider_response_json is null then 1 else 0"
                            + " end,lease_until=DATE_ADD(CURRENT_TIMESTAMP(6),interval 10 minute)"
                            + " where id=? and status in ('QUEUED','RECOVERY_QUEUED')",
                        row.get("gateway_user_id"),
                        row.get("evaluation_run_id"),
                        token,
                        id));
        tasks.processClaimed(row, token);
    }

    @Test
    void expiredCreditLeaseRecoversExistingHoldWithoutAnotherDebit() {
        var f = fixture("恢复积分预留。");
        String revision = service.statuses(f.user).getFirst().revisionId();
        var request =
                new EvidenceTaskDtos.Request(
                        "EXTRACT",
                        null,
                        null,
                        null,
                        null,
                        revision,
                        "提取条件",
                        1,
                        UUID.randomUUID().toString());
        var preview = tasks.preview(f.user, request);
        var task =
                tasks.submit(
                        f.user, new EvidenceTaskDtos.Submit(request, preview.fingerprint(), 11));
        jdbc.update(
                "update material_processing_jobs set"
                    + " status='RESERVING_CREDIT',lease_until=DATE_SUB(CURRENT_TIMESTAMP(6),interval"
                    + " 1 minute) where id=?",
                task.id());
        org.mockito.Mockito.when(economy.evidenceReservation(f.user, "evidence:" + task.id()))
                .thenReturn(
                        new com.ainovel.app.economy.EconomyService.EvidenceReservation(
                                "RESERVED", null, 0, 0, 0));
        assertEquals("QUEUED", tasks.resume(f.user, task.id()).status());
        var stranger = fixture("其他作者。");
        assertThrows(ApiStatusException.class, () -> tasks.resume(stranger.user, task.id()));
        org.mockito.Mockito.verify(economy, org.mockito.Mockito.times(1))
                .reserveAiUsage(
                        org.mockito.ArgumentMatchers.eq(f.user),
                        org.mockito.ArgumentMatchers.eq(11L),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString());
        assertEquals("CANCELLED", tasks.cancel(f.user, task.id()).status());
        org.mockito.Mockito.verifyNoInteractions(nativeClient);
    }

    @Test
    void confirmedCitationStoresCodePointPositionsAndNeverReconfirmsRestoredBody() {
        var f = fixture("😀日落后通行。");
        UUID scene = UUID.randomUUID();
        UUID[] ids = bodyFixture(f, scene);
        var body = service.citationBody(f.user, ids[0], scene.toString());
        String revision = service.statuses(f.user).getFirst().revisionId();
        var input =
                new EvidenceDtos.CitationWrite(
                        revision,
                        ids[1].toString(),
                        ids[2].toString(),
                        "CONFIRMED",
                        1,
                        4,
                        "日落后",
                        "日落后",
                        UUID.randomUUID().toString(),
                        "b1",
                        body.manuscriptVersion());
        String id = service.citation(f.user, ids[0], scene.toString(), input);
        assertEquals(id, service.citation(f.user, ids[0], scene.toString(), input));
        var row = service.citations(f.user, ids[0]).getFirst();
        assertEquals(1, ((Number) row.get("body_start_cp")).intValue());
        assertEquals(4, ((Number) row.get("body_end_cp")).intValue());
        assertEquals("CURRENT", row.get("state"));
        tx.executeWithoutResult(
                s -> {
                    var manuscript =
                            em.find(com.ainovel.app.manuscript.model.Manuscript.class, ids[0]);
                    contents.writeScene(manuscript, scene, "<p>正文已改。</p>");
                    em.flush();
                });
        assertEquals("REVIEW_REQUIRED", service.citations(f.user, ids[0]).getFirst().get("state"));
        tx.executeWithoutResult(
                s -> {
                    var manuscript =
                            em.find(com.ainovel.app.manuscript.model.Manuscript.class, ids[0]);
                    contents.writeScene(manuscript, scene, "<p>😀日落后通行。</p>");
                    em.flush();
                });
        assertEquals("REVIEW_REQUIRED", service.citations(f.user, ids[0]).getFirst().get("state"));
        jdbc.update("update materials set status='pending' where id=?", bytes(f.material));
        assertNull(service.citations(f.user, ids[0]).getFirst().get("quote"));
    }

    @Test
    void saveCommitsRawRevisionAndQueuedProjectionBeforeBasicWorkerRuns() {
        var f = fixture("初版原文。");
        tx.executeWithoutResult(
                s -> {
                    var m = em.find(Material.class, f.material);
                    m.setContentVersion(2);
                    m.setContent("新版😀原文。");
                    em.flush();
                    service.capture(m);
                });
        var state = service.statuses(f.user).getFirst();
        assertEquals("SAVED", state.saved());
        assertEquals("QUEUED", state.basic());
        assertEquals(
                0,
                jdbc.queryForObject(
                        "select count(*) from material_evidence_chunks where revision_id=?",
                        Integer.class,
                        state.revisionId()));
        project(f.material);
        assertEquals("SEARCHABLE", service.statuses(f.user).getFirst().basic());
        assertEquals(
                "新版😀原文。",
                service.search(
                                f.user,
                                new EvidenceDtos.Search("新版", f.story, "fact", "personal", 8))
                        .items()
                        .getFirst()
                        .text());
    }
}
