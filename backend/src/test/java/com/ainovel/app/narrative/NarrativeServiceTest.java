package com.ainovel.app.narrative;

import com.ainovel.app.aioperation.*;
import com.ainovel.app.common.*;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.story.model.*;
import com.ainovel.app.user.User;
import com.ainovel.app.v2.*;
import com.ainovel.app.v2.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import java.util.*;
import static com.ainovel.app.narrative.NarrativeDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@DataJpaTest(showSql = false)
@Import({NarrativeService.class, NarrativeInvalidationListener.class, ResourceAccessGuard.class, V2VersionPersistenceService.class,
        JsonColumnCodec.class, NarrativeServiceTest.Beans.class})
class NarrativeServiceTest {
    @TestConfiguration
    @ComponentScan(basePackages="com.ainovel.app.v2", useDefaultFilters=false,
            includeFilters=@ComponentScan.Filter(type=FilterType.REGEX, pattern="com.ainovel.app.v2.V2Json"))
    static class Beans {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean Validator validator() { return new LocalValidatorFactoryBean(); }
        @Bean CurrentUserResolver currentUserResolver() { return mock(CurrentUserResolver.class); }
    }
    @Autowired TestEntityManager em;
    @Autowired NarrativeService service;
    @Autowired V2VersionPersistenceService versions;
    @Autowired ObjectMapper json;
    User user;
    Manuscript manuscript;
    CharacterCard character;
    UUID branch, scene1 = UUID.randomUUID(), scene2 = UUID.randomUUID(), chapter = UUID.randomUUID();
    static final String TEXT = "<p>𠮷😀林青说：桥断了。他准备去偷钥匙。</p><p>林青误以为同伴背叛，昨夜梦见大火。</p>";

    @BeforeEach void setup() {
        user = new User(); user.setUsername("narrative-" + UUID.randomUUID());
        user.setEmail(user.getUsername() + "@example.com"); user.setPasswordHash("test"); em.persist(user);
        Story story = new Story(); story.setUser(user); story.setTitle("证据测试"); story.setSynopsis("秘密结局不得入输入"); em.persist(story);
        Outline outline = new Outline(); outline.setStory(story); outline.setTitle("大纲");
        outline.setContentJson(topology(List.of(scene1, scene2))); em.persist(outline);
        character = new CharacterCard(); character.setStory(story); character.setName("林青");
        character.setDetails("未来秘密不得入输入"); em.persist(character);
        manuscript = new Manuscript(); manuscript.setOutline(outline); manuscript.setTitle("正文");
        manuscript.setSectionsJson(write(Map.of(scene1, TEXT, scene2, "<p>林青仍在等消息。</p>"))); em.persist(manuscript);
        versions.listVersions(manuscript, user); em.flush(); branch = manuscript.getCurrentBranchId();
    }
    @Test void authorGateEvidenceAndIdempotentReceiptsSurviveReload() {
        ApprovalRequest request = new ApprovalRequest(scene1, manuscript.getVersion(), 0L);
        Approved approved = service.approve(user, manuscript.getId(), branch, request, "approval");
        assertEquals(approved, service.approve(user, manuscript.getId(), branch, request, "approval"));
        UUID operation = operation(approved);
        assertFalse(write(service.input(user, approved.extractionId(), operation)).contains("秘密"));
        store(approved, operation, assertion(Kind.FACT, "同伴背叛", "林青误以为同伴背叛", "b2", null));
        assertTrue(state().records().isEmpty());
        Assertion edited = assertion(Kind.BELIEF, "林青误以为同伴背叛", "林青误以为同伴背叛", "b2", null);
        ReviewRequest review = reviewRequest(edited);
        ReviewResult receipt = service.review(user, manuscript.getId(), branch, approved.extractionId(), review, "review");
        assertEquals(receipt, service.review(user, manuscript.getId(), branch, approved.extractionId(), review, "review"));
        em.flush(); em.clear();
        var record = state().records().get(0);
        assertEquals(Kind.BELIEF, record.assertion().kind());
        assertEquals(character.getId(), record.assertion().holderCharacterId());
        var source = service.evidence(user, manuscript.getId(), branch, approved.approvalId());
        var evidence = record.assertion().evidence().get(0);
        String block = source.blocks().get(1).text();
        assertEquals(evidence.quote(), new String(block.codePoints().skip(evidence.start()).limit(evidence.end() - evidence.start()).toArray(), 0, evidence.end() - evidence.start()));
        assertTrue(em.find(V2ManuscriptVersion.class, source.versionId()).isNarrativeProtected());
        assertEquals(1, state().records().size());
    }
    @ParameterizedTest(name="author correction: {0}")
    @MethodSource("semanticFixtures")
    void evidenceValidationNeverSubstitutesForAuthorSemanticReview(String id, com.fasterxml.jackson.databind.JsonNode fixture) {
        manuscript.setSectionsJson(write(Map.of(scene1, "<p>" + fixture.path("text").asText() + "</p>"))); em.flush();
        var approved = approve(scene1);
        store(approved, operation(approved), assertion(Kind.FACT, fixture.path("modelStatement").asText(), fixture.path("quote").asText(), "b1", null));
        assertTrue(state().records().isEmpty());
        var kind = Kind.valueOf(fixture.path("kind").asText());
        var edited = new Assertion("林青", character.getId(), fixture.path("statement").asText(), kind,
                kind == Kind.BELIEF ? character.getId() : null, null, fixture.path("uncertainty").asText(),
                List.of(new Evidence("b1", fixture.path("quote").asText(), null, null)), null);
        submit(approved, edited);
        assertEquals(kind, state().records().get(0).assertion().kind());
        assertEquals(edited.statement(), state().records().get(0).assertion().statement());
        assertNull(state().records().get(0).assertion().worldTime());
    }
    static java.util.stream.Stream<Arguments> semanticFixtures() throws Exception {
        try (var source = NarrativeServiceTest.class.getResourceAsStream("/narrative/h1-semantic-fixtures.json")) {
            var fixtures = new ObjectMapper().readTree(source);
            return java.util.stream.StreamSupport.stream(fixtures.spliterator(), false)
                    .map(fixture -> Arguments.of(fixture.path("id").asText(), fixture)).toList().stream();
        }
    }
    @Test void sourceRevisionInvalidatesDependentRecordsAndPreservesHistory() {
        Approved first = complete(scene1, assertion(Kind.UTTERANCE, "林青声称桥断", "桥断了", "b1", null));
        var second = approve(scene2); UUID operation = operation(second);
        assertEquals(1, service.input(user, second.extractionId(), operation).previousRecords().size());
        store(second, operation, assertion(Kind.FACT, "林青等待消息", "林青仍在等消息", "b1", null));
        submit(second, null);
        long previous = state().canonRevision();
        manuscript.setSectionsJson(write(Map.of(scene1, "<p>桥仍完好。</p>", scene2, "<p>林青仍在等消息。</p>"))); em.flush();
        service.reconcileManuscript(manuscript.getId());
        assertEquals(List.of("STALE", "STALE"), state().records().stream().map(RecordView::status).toList());
        assertEquals(List.of("CONFIRMED", "CONFIRMED"), service.state(user, manuscript.getId(), branch, null, null, null, null, previous).records().stream().map(RecordView::status).toList());
        assertEquals(TEXT.replace("<p>", "").split("</p>")[0], service.evidence(user, manuscript.getId(), branch, first.approvalId()).blocks().get(0).text());
        manuscript.setSectionsJson(write(Map.of(scene1, TEXT, scene2, "<p>林青仍在等消息。</p>"))); em.flush();
        assertEquals("STALE", state().records().get(0).status());
    }
    @Test void formatChangesKeepEvidenceButReorderAndDeleteInvalidate() {
        complete(scene1, assertion(Kind.UTTERANCE, "林青声称桥断", "桥断了", "b1", null));
        manuscript.setSectionsJson(write(Map.of(scene1, TEXT.replace("桥断了", "<strong>桥断了</strong>"), scene2, "<p>林青仍在等消息。</p>"))); em.flush();
        assertEquals("CONFIRMED", state().records().get(0).status());
        manuscript.getOutline().setContentJson(topology(List.of(scene2, scene1))); em.flush();
        assertEquals("STALE", state().records().get(0).status());
        manuscript.getOutline().setContentJson(topology(List.of(scene2))); em.flush();
        assertTrue(state().extractions().get(0).stale());
    }
    @Test void cancelledMalformedAndEmptyBatchesCannotCreatePartialState() {
        var first = approve(scene1); UUID operation = operation(first);
        service.storeResult(user, first.extractionId(), operation, "broken JSON", Map.of("cost", 1), "test");
        assertEquals("INVALID_OUTPUT", state().extractions().get(0).status());
        service.storeResult(user, first.extractionId(), operation, "{\"candidates\":[]}", Map.of("cost", 2), "test");
        assertTrue(state().records().isEmpty());
        var empty = approve(scene1); UUID emptyOperation = operation(empty);
        service.storeResult(user, empty.extractionId(), emptyOperation, "{\"candidates\":[]}", Map.of(), "test");
        assertTrue(service.extraction(user, manuscript.getId(), branch, empty.extractionId()).candidates().isEmpty());
        var cancelled = approve(scene1); UUID cancelledOperation = operation(cancelled);
        store(cancelled, cancelledOperation, assertion(Kind.FACT, "不应入账", "桥断了", "b1", null));
        em.find(AiOperationRun.class, cancelledOperation).setStatus(AiOperationStatus.CANCELLED); em.flush();
        assertEquals("CANCELLED", service.extraction(user, manuscript.getId(), branch, cancelled.extractionId()).status());
        assertThrows(ApiStatusException.class, () -> submit(cancelled, null));
        assertTrue(state().records().isEmpty());
    }
    @Test void rejectsWrongEvidenceAndVersionsWithoutAcceptingAnyRecord() {
        var approved = approve(scene1); UUID operation = operation(approved);
        store(approved, operation, assertion(Kind.FACT, "拿到钥匙", "已经拿到钥匙", "b1", UUID.randomUUID()));
        assertNotNull(state().extractions().get(0).candidates().get(0).validationError());
        assertNull(state().extractions().get(0).candidates().get(0).assertion().supersedesId());
        assertThrows(ApiStatusException.class, () -> submit(approved, null));
        assertTrue(state().records().isEmpty());
        assertThrows(ApiStatusException.class, () -> service.approve(user, manuscript.getId(), branch, new ApprovalRequest(scene1, -1L, 0L), "bad-version"));
        manuscript.setSectionsJson("{}"); em.flush();
        assertTrue(service.extraction(user, manuscript.getId(), branch, approved.extractionId()).stale());
    }
    @Test void branchAndManuscriptAndOwnerIsolation() {
        var approved = complete(scene1, assertion(Kind.UTTERANCE, "林青声称桥断", "桥断了", "b1", null));
        User other = new User(); other.setId(UUID.randomUUID());
        assertThrows(BusinessException.class, () -> service.state(other, manuscript.getId(), branch, null, null, null, null, null));
        Manuscript second = new Manuscript(); second.setTitle("另一稿件"); second.setOutline(manuscript.getOutline()); second.setSectionsJson(manuscript.getSectionsJson()); em.persistAndFlush(second);
        versions.listVersions(second, user); em.flush();
        assertTrue(service.state(user, second.getId(), second.getCurrentBranchId(), null, null, null, null, null).records().isEmpty());
        assertThrows(ApiStatusException.class, () -> service.evidence(user, second.getId(), second.getCurrentBranchId(), approved.approvalId()));
        assertThrows(ApiStatusException.class, () -> service.state(user, second.getId(), branch, null, null, null, null, null));
        V2ManuscriptBranch fork = new V2ManuscriptBranch(); fork.setManuscript(manuscript); fork.setName("新分支"); fork.setStatus("active"); em.persistAndFlush(fork);
        assertTrue(service.state(user, manuscript.getId(), fork.getId(), null, null, null, null, null).records().isEmpty());
    }
    @Test void explicitReplacementDoesNotInvalidateItselfAndAbsenceNeverDeletes() {
        complete(scene1, assertion(Kind.UTTERANCE, "林青声称桥断", "桥断了", "b1", null));
        UUID original = state().records().get(0).id();
        complete(scene2, assertion(Kind.FACT, "林青仍等待", "林青仍在等消息", "b1", original));
        assertEquals(List.of("SUPERSEDED", "CONFIRMED"), state().records().stream().map(RecordView::status).toList());
        var empty = approve(scene2); var operation = operation(empty);
        service.storeResult(user, empty.extractionId(), operation, "{\"candidates\":[]}", Map.of(), "test");
        service.review(user, manuscript.getId(), branch, empty.extractionId(), new ReviewRequest(manuscript.getVersion(), state().canonRevision(), List.of(), List.of()), "empty");
        assertEquals("CONFIRMED", state().records().get(1).status());
    }
    @Test void protectedAutoSnapshotSurvivesRetentionLimit() {
        var approved = complete(scene1, assertion(Kind.UTTERANCE, "声称桥断", "桥断了", "b1", null));
        UUID protectedId = service.evidence(user, manuscript.getId(), branch, approved.approvalId()).versionId();
        assertEquals("auto", em.find(V2ManuscriptVersion.class, protectedId).getSnapshotType());
        versions.updateAutoSave(user, Map.of("maxAutoVersions", 10));
        for (int i = 0; i < 10; i++) {
            manuscript.setSectionsJson(write(Map.of(scene1, TEXT, scene2, "<p>第" + i + "天等消息。</p>")));
            versions.createVersion(manuscript, user, Map.of("snapshotType", "auto"));
        }
        em.flush(); em.clear();
        assertNotNull(em.find(V2ManuscriptVersion.class, protectedId));
        assertEquals(11, versions.listVersions(manuscript.getId()).size());
        assertEquals("CONFIRMED", state().records().get(0).status());
    }
    @Test void checkoutMergeAndRollbackKeepSeparateLedgersAndInvalidateTarget() {
        var mainApproval = complete(scene1, assertion(Kind.UTTERANCE, "林青声称桥断", "桥断了", "b1", null));
        UUID main = branch, baseline = service.evidence(user, manuscript.getId(), branch, mainApproval.approvalId()).versionId();
        UUID fork = (UUID) versions.createBranch(manuscript, user, Map.of("name", "替代剧情", "sourceVersionId", baseline)).get("id");
        versions.checkoutBranch(manuscript, user, fork); em.flush(); branch = fork;
        assertTrue(state().records().isEmpty());
        manuscript.setSectionsJson(write(Map.of(scene1, "<p>林青发现桥完好。</p>", scene2, "<p>林青仍在等消息。</p>"))); em.flush();
        complete(scene1, assertion(Kind.FACT, "桥完好", "桥完好", "b1", null));
        assertEquals("CONFIRMED", service.state(user, manuscript.getId(), main, null, null, null, null, null).records().get(0).status());
        versions.mergeBranch(manuscript, user, fork, Map.of()); em.flush(); branch = main;
        assertEquals(1, state().records().size());
        assertEquals("STALE", state().records().get(0).status());
        assertEquals("CONFIRMED", service.state(user, manuscript.getId(), fork, null, null, null, null, null).records().get(0).status());
        versions.rollback(manuscript, user, baseline); em.flush();
        assertEquals("STALE", state().records().get(0).status());
        assertEquals(TEXT, json.valueToTree(readSections()).path(scene1.toString()).asText());
    }
    Map<?, ?> readSections() { try { return json.readValue(manuscript.getSectionsJson(), Map.class); } catch (Exception e) { throw new RuntimeException(e); } }
    String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new RuntimeException(e); } }
    String topology(List<UUID> scenes) { return write(Map.of("chapters", List.of(Map.of("id", chapter, "title", "一", "scenes", scenes.stream().map(id -> Map.of("id", id, "title", "场景")).toList())))); }
    StateView state() { return service.state(user, manuscript.getId(), branch, null, null, null, null, null); }
    Approved approve(UUID scene) { return service.approve(user, manuscript.getId(), branch, new ApprovalRequest(scene, manuscript.getVersion(), state().canonRevision()), UUID.randomUUID().toString()); }
    UUID operation(Approved approved) { AiOperationRun run = new AiOperationRun(); run.setUser(user); run.setOperationType(NarrativeExtractionHandler.TYPE); run.setStatus(AiOperationStatus.RUNNING); em.persistAndFlush(run); service.attachOperation(approved.extractionId(), run.getId()); return run.getId(); }
    Assertion assertion(Kind kind, String statement, String quote, String block, UUID replaces) { return new Assertion("林青", character.getId(), statement, kind, kind == Kind.BELIEF ? character.getId() : null, null, "由作者核对", List.of(new Evidence(block, quote, 999, 1000)), replaces); }
    void store(Approved approved, UUID operation, Assertion assertion) { service.storeResult(user, approved.extractionId(), operation, write(Map.of("candidates", List.of(assertion))), Map.of("cost", 1), "test"); }
    ReviewRequest reviewRequest(Assertion edited) { return new ReviewRequest(manuscript.getVersion(), state().canonRevision(), List.of(new ReviewItem("c1", Decision.ACCEPT, edited)), List.of()); }
    void submit(Approved approved, Assertion edited) { service.review(user, manuscript.getId(), branch, approved.extractionId(), reviewRequest(edited), UUID.randomUUID().toString()); }
    Approved complete(UUID scene, Assertion assertion) { var approved = approve(scene); store(approved, operation(approved), assertion); submit(approved, assertion); return approved; }
}
