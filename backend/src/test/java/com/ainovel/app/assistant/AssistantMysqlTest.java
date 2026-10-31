package com.ainovel.app.assistant;

import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.AiAdmissionGuard;
import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.ai.dto.AiChatResponse;
import com.ainovel.app.ai.dto.AiUsageDto;
import com.ainovel.app.aioperation.*;
import com.ainovel.app.common.ApiStatusException;
import com.ainovel.app.common.CurrentUserResolver;
import com.ainovel.app.common.JsonColumnCodec;
import com.ainovel.app.manuscript.ManuscriptContentService;
import com.ainovel.app.manuscript.ManuscriptService;
import com.ainovel.app.manuscript.SceneGenerationService;
import com.ainovel.app.manuscript.attribution.SceneGenerationAttributionService;
import com.ainovel.app.narrative.NarrativeContextService;
import com.ainovel.app.narrative.NarrativeContextDtos;
import com.ainovel.app.narrative.NarrativeGenerationCandidateStore;
import com.ainovel.app.narrative.NarrativeService;
import com.ainovel.app.quality.language.LanguageFeature;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.story.model.Outline;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.user.User;
import com.ainovel.app.v2.V2VersionPersistenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.Validator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static com.ainovel.app.material.MaterialFingerprintService.bytes;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.ainovel.app.assistant.AssistantDtos.*;

/** Deliberately requires a disposable MySQL schema; never substitutes the embedded H2 database. */
@EnabledIfEnvironmentVariable(named = "AIENIE_AUDIT_MYSQL_URL", matches = "jdbc:mysql:.*")
@DataJpaTest(showSql = false, properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none", "spring.jpa.open-in-view=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({com.ainovel.app.worldcontext.WorldContextSelector.class, AssistantStore.class, ManuscriptContentService.class, V2VersionPersistenceService.class,
        ManuscriptService.class, NarrativeGenerationCandidateStore.class, WritingAssistantService.class,
        NarrativeService.class, NarrativeContextService.class,
        AssistantContext.class, AssistantPrompts.class, LanguageFeature.class,
        ResourceAccessGuard.class, JsonColumnCodec.class, AssistantMysqlTest.Beans.class})
class AssistantMysqlTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        String url = System.getenv("AIENIE_AUDIT_MYSQL_URL");
        if (url == null || !url.matches("jdbc:mysql://[^/]+/aienie_novel_audit_test_[A-Za-z0-9_]+(?:\\?.*)?"))
            throw new IllegalArgumentException("Isolated schema required");
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> System.getenv("AIENIE_AUDIT_MYSQL_USERNAME"));
        properties.add("spring.datasource.password", () -> System.getenv("AIENIE_AUDIT_MYSQL_PASSWORD"));
        properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        properties.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
    }

    @TestConfiguration static class Beans {
        @Bean static org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor versionJson() {
            return new org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor() {
                @Override public void postProcessBeanDefinitionRegistry(org.springframework.beans.factory.support.BeanDefinitionRegistry registry) {
                    var definition = new org.springframework.beans.factory.support.GenericBeanDefinition();
                    definition.setBeanClassName("com.ainovel.app.v2.V2Json");
                    registry.registerBeanDefinition("v2Json", definition);
                }
            };
        }
        @Bean ObjectMapper mapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean Validator validator() { return new LocalValidatorFactoryBean(); }
        @Bean TransactionTemplate transactions(PlatformTransactionManager manager) { return new TransactionTemplate(manager); }
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
    @MockitoBean CurrentUserResolver currentUser;
    @MockitoBean AiService ai;
    @MockitoBean AiAdmissionGuard admission;
    @MockitoBean AiOperationService operations;
    @Autowired NarrativeContextService narrative;
    @MockitoBean SceneGenerationService generator;
    @MockitoBean SceneGenerationAttributionService attribution;
    @Autowired AssistantStore store;
    @Autowired WritingAssistantService assistant;
    @Autowired ManuscriptRepository manuscripts;
    @Autowired ManuscriptContentService contents;
    @Autowired V2VersionPersistenceService versions;
    @Autowired TransactionTemplate transactions;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @PersistenceContext EntityManager em;

    @BeforeEach void operationReceipts() {
        when(operations.submit(any(), anyString(), anyString(), any(), any(), anyInt(), anyString(), anyString()))
                .thenAnswer(invocation -> new AiOperationDtos.Accepted(UUID.randomUUID()));
        when(operations.get(any(), any())).thenAnswer(invocation -> new AiOperationDtos.Progress(invocation.getArgument(1),
                WritingAssistantService.TYPE, "ASSISTANT_SESSION", null, AiOperationStatus.SUCCEEDED, "完成",
                2, 2, 0, 0, false, 1, null, null, null, null, null));
    }

    @AfterEach void logout() { SecurityContextHolder.clearContext(); }

    private record Fixture(User user, UUID manuscript, UUID branch, UUID scene, UUID otherScene) {}

    private Fixture fixture() {
        return transactions.execute(status -> {
            var user = new User();
            String name = "assistant-" + UUID.randomUUID();
            user.setUsername(name); user.setEmail(name + "@example.invalid"); user.setPasswordHash("test"); em.persist(user);
            var story = new Story(); story.setUser(user); story.setTitle("助手 MySQL 验收"); story.setStatus("draft"); em.persist(story);
            UUID scene = UUID.randomUUID(), otherScene = UUID.randomUUID();
            var outline = new Outline(); outline.setStory(story); outline.setTitle("验收大纲");
            outline.setContentJson(store.write(Map.of("chapters", List.of(Map.of("id", UUID.randomUUID(), "title", "第一章", "order", 0,
                    "scenes", List.of(Map.of("id", otherScene, "title", "前文", "order", 0), Map.of("id", scene, "title", "当前", "order", 1)))))));
            em.persist(outline);
            var manuscript = new Manuscript(); manuscript.setOutline(outline); manuscript.setTitle("助手测试稿件");
            contents.initialize(manuscript); em.persist(manuscript); em.flush();
            contents.writeScene(manuscript, otherScene, "<p>他在前文收到一封信。</p>");
            contents.writeScene(manuscript, scene, "<p>她握紧信封。</p><p>天亮前她没有拆开。</p>");
            versions.ensureGenerationBaseline(manuscript, user); em.flush();
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user.getUsername(), "", List.of()));
            return new Fixture(user, manuscript.getId(), manuscript.getCurrentBranchId(), scene, otherScene);
        });
    }

    private String html(Fixture fixture) {
        return transactions.execute(status -> contents.readScene(manuscripts.findById(fixture.manuscript()).orElseThrow(), fixture.scene()));
    }

    @Test void frozenStepReceiptsSurviveBusinessRollbackAndCannotBeOverwritten() {
        Fixture f = fixture();
        var session = store.createSession(f.user().getId(), f.manuscript(), f.branch(), f.scene());
        var document = AssistantDocumentProjection.project(html(f), new AssistantDocumentProjection.Selection(0, 0, 0, 6));
        UUID turn = store.createTurn(session.id(), "checkpoint-request", "a".repeat(64), Map.of("document", document));
        var request = new AiChatRequest(List.of(new AiChatRequest.Message("user", "冻结的原始正文与结尾")), null, null);
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            store.checkpoint(turn, "plan_request_json", request);
            store.checkpoint(turn, "plan_result_json", Map.of("content", "已收到的规划结果"));
            store.checkpoint(turn, "reads_json", Map.of("sources", List.of("已冻结前文")));
            throw new IllegalStateException("simulated worker interruption");
        })).isInstanceOf(IllegalStateException.class);
        store.checkpoint(turn, "plan_request_json", new AiChatRequest(List.of(new AiChatRequest.Message("user", "不能重建成最新上下文")), null, null));
        var restartedStore = new AssistantStore(jdbc, json);
        var reloaded = restartedStore.turn(turn);
        assertThat(reloaded.planRequestJson()).isEqualTo(store.write(request));
        assertThat(reloaded.planResultJson()).contains("已收到的规划结果");
        assertThat(reloaded.readsJson()).contains("已冻结前文");
        assertThat(reloaded.frozenJson()).contains(document.sourceHash(), "天亮前她没有拆开。");
        assertThat(html(f)).isEqualTo(document.sourceHtml());
        verifyNoInteractions(ai);
    }

    @Test void mysqlUniquenessAndProposalReplayPreserveOriginalRecords() {
        Fixture f = fixture();
        var session = store.createSession(f.user().getId(), f.manuscript(), f.branch(), f.scene());
        UUID turn = store.createTurn(session.id(), "same-request", "b".repeat(64), Map.of("instruction", "第一次"));
        assertThat(store.replay(session.id(), "same-request").id()).isEqualTo(turn);
        assertThatThrownBy(() -> store.createTurn(session.id(), "same-request", "c".repeat(64), Map.of("instruction", "第二次")))
                .isInstanceOf(DataIntegrityViolationException.class);
        UUID proposal = store.createProposal(turn, Map.of("beforeHtml", html(f), "afterHtml", "<p>新稿</p>"));
        assertThat(store.createProposal(turn, Map.of("afterHtml", "不能覆盖"))).isEqualTo(proposal);
        assertThat(store.proposal(proposal, false).dataJson()).contains("新稿").doesNotContain("不能覆盖");
        assertThat(jdbc.queryForObject("select count(*) from writing_assistant_turns where session_id=?", Integer.class, bytes(session.id()))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from writing_assistant_proposals where turn_id=?", Integer.class, bytes(turn))).isEqualTo(1);
        verifyNoInteractions(ai);
    }

    @Test void duplicateSubmitAcceptAndUndoKeepOneDecisionAndPersistBothSnapshots() {
        Fixture f = fixture(); String before = html(f);
        var m = manuscripts.findById(f.manuscript()).orElseThrow();
        var request = new TurnRequest(f.branch(), m.getVersion(), "改得更具体，保留结尾", before, null, null, "EDIT");
        Accepted accepted = assistant.submit(f.user(), f.manuscript(), f.scene(), request, "submit-once");
        assertThat(assistant.submit(f.user(), f.manuscript(), f.scene(), request, "submit-once")).isEqualTo(accepted);
        assertThatThrownBy(() -> assistant.submit(f.user(), f.manuscript(), f.scene(),
                new TurnRequest(f.branch(), m.getVersion(), "另外要求", before, null, null, "EDIT"), "submit-once"))
                .isInstanceOf(ApiStatusException.class).hasMessage("ASSISTANT_IDEMPOTENCY_CONFLICT");
        verify(operations, times(1)).submit(any(), anyString(), anyString(), any(), any(), anyInt(), anyString(), anyString());
        respondWithProposal(before);
        execute(f, accepted);
        var turn = assistant.turn(f.user(), f.manuscript(), f.scene(), accepted.turnId());
        var proposal = assistant.proposal(f.user(), f.manuscript(), f.scene(), turn.proposalId());
        assertThat(proposal.applicable()).isTrue();
        assertThat(html(f)).isEqualTo(before);
        var decision = new DecisionRequest(m.getVersion(), f.branch());
        var saved = assistant.decide(f.user(), f.manuscript(), f.scene(), proposal.id(), "accept", decision, "accept-once");
        assertThat(assistant.decide(f.user(), f.manuscript(), f.scene(), proposal.id(), "accept", decision, "accept-once")).isEqualTo(saved);
        assertThat(saved.manuscript().version()).isEqualTo(m.getVersion() + 1);
        assertThat(html(f)).isEqualTo(proposal.afterHtml());
        var data = store.read(store.proposal(proposal.id(), false).dataJson(), WritingAssistantService.ProposalData.class);
        assertThat(data.beforeSnapshotId).isNotNull(); assertThat(data.afterSnapshotId).isNotNull();
        assertThat(snapshotScene(data.beforeSnapshotId, f.scene())).isEqualTo(before);
        assertThat(snapshotScene(data.afterSnapshotId, f.scene())).isEqualTo(proposal.afterHtml());
        assertThat(snapshotScene(data.afterSnapshotId, f.otherScene())).isEqualTo("<p>他在前文收到一封信。</p>");
        var undoRequest = new DecisionRequest(saved.manuscript().version(), f.branch());
        var undone = assistant.decide(f.user(), f.manuscript(), f.scene(), proposal.id(), "undo", undoRequest, "undo-once");
        assertThat(assistant.decide(f.user(), f.manuscript(), f.scene(), proposal.id(), "undo", undoRequest, "undo-once")).isEqualTo(undone);
        assertThat(html(f)).isEqualTo(before);
        assertThat(undone.manuscript().version()).isEqualTo(saved.manuscript().version() + 1);
        assertThat(jdbc.queryForObject("select count(*) from manuscript_versions where manuscript_id=? and snapshot_type='assistant_accept'", Integer.class, bytes(f.manuscript()))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from manuscript_versions where manuscript_id=? and snapshot_type='assistant_undo'", Integer.class, bytes(f.manuscript()))).isEqualTo(1);
        verify(ai, times(1)).chatWithIdempotency(any(), any(), eq(accepted.operationId() + ":assistant:plan"));
        verifyNoInteractions(generator, attribution);
    }

    @Test void failedDecisionRollsBackBodySnapshotsAndReceiptAndLaterEditsBlockUndo() {
        Fixture f = fixture(); String before = html(f);
        var m = manuscripts.findById(f.manuscript()).orElseThrow();
        Accepted accepted = assistant.submit(f.user(), f.manuscript(), f.scene(),
                new TurnRequest(f.branch(), m.getVersion(), "修改动作", before, null, null, "EDIT"), "rollback-submit");
        respondWithProposal(before); execute(f, accepted);
        UUID proposal = assistant.turn(f.user(), f.manuscript(), f.scene(), accepted.turnId()).proposalId();
        var request = new DecisionRequest(m.getVersion(), f.branch());
        long count = jdbc.queryForObject("select count(*) from manuscript_versions where manuscript_id=?", Long.class, bytes(f.manuscript()));
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            assistant.decide(f.user(), f.manuscript(), f.scene(), proposal, "accept", request, "rolled-back");
            throw new IllegalStateException("after decision rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(html(f)).isEqualTo(before);
        assertThat(manuscripts.findById(f.manuscript()).orElseThrow().getVersion()).isEqualTo(m.getVersion());
        assertThat(store.proposal(proposal, false).status()).isEqualTo("READY");
        assertThat(store.proposal(proposal, false).receiptsJson()).isEqualTo("{}");
        assertThat(jdbc.queryForObject("select count(*) from manuscript_versions where manuscript_id=?", Long.class, bytes(f.manuscript()))).isEqualTo(count);
        assistant.decide(f.user(), f.manuscript(), f.scene(), proposal, "accept", request, "accepted-now");
        transactions.executeWithoutResult(status -> {
            var edited = manuscripts.findById(f.manuscript()).orElseThrow();
            contents.writeScene(edited, f.scene(), "<p>作者后续编辑，必须保留。</p>"); manuscripts.saveAndFlush(edited);
        });
        var current = manuscripts.findById(f.manuscript()).orElseThrow();
        assertThatThrownBy(() -> assistant.decide(f.user(), f.manuscript(), f.scene(), proposal, "undo",
                new DecisionRequest(current.getVersion(), f.branch()), "unsafe-undo"))
                .isInstanceOf(ApiStatusException.class).hasMessage("ASSISTANT_PROPOSAL_STALE");
        assertThat(html(f)).isEqualTo("<p>作者后续编辑，必须保留。</p>");
    }

    @Test void interruptedSecondStepReusesFrozenRequestSourcesAndFirstResponse() {
        Fixture f = fixture(); var m = manuscripts.findById(f.manuscript()).orElseThrow();
        Accepted accepted = assistant.submit(f.user(), f.manuscript(), f.scene(),
                new TurnRequest(f.branch(), m.getVersion(), "前文在哪里收到信？", html(f), null, null, "AUTHOR_QA"), "recover-two-steps");
        AtomicInteger finals = new AtomicInteger(); var finalRequests = new ArrayList<String>();
        when(ai.chatWithIdempotency(any(), any(), anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(2);
            if (key.endsWith(":plan")) return response("{\"kind\":\"READ_BATCH\",\"intent\":\"AUTHOR_QA\",\"reads\":[{\"tool\":\"search_previous\",\"query\":\"信\"}]}");
            finalRequests.add(store.write(invocation.getArgument(1)));
            if (finals.getAndIncrement() == 0) throw new IllegalStateException("second request interrupted");
            return response("{\"kind\":\"ANSWER\",\"intent\":\"AUTHOR_QA\",\"answer\":\"他在前文收到信，来源 scene:" + f.otherScene() + "。\"}");
        });
        assertThatThrownBy(() -> execute(f, accepted)).hasMessage("second request interrupted");
        var checkpoint = store.turn(accepted.turnId());
        assertThat(checkpoint.planResultJson()).isNotBlank(); assertThat(checkpoint.finalRequestJson()).isNotBlank();
        assertThat(checkpoint.readsJson()).contains("他在前文收到一封信");
        transactions.executeWithoutResult(status -> {
            var edited = manuscripts.findById(f.manuscript()).orElseThrow();
            contents.writeScene(edited, f.otherScene(), "<p>这是任务开始后新的前文，不应进入恢复请求。</p>"); manuscripts.saveAndFlush(edited);
        });
        execute(f, accepted);
        assertThat(finalRequests).hasSize(2);
        assertThat(finalRequests.get(1)).isEqualTo(finalRequests.getFirst()).doesNotContain("任务开始后新的前文");
        assertThat(store.turn(accepted.turnId()).readsJson()).isEqualTo(checkpoint.readsJson());
        var outcome = assistant.turn(f.user(), f.manuscript(), f.scene(), accepted.turnId());
        assertThat(outcome.resultKind()).isEqualTo("ANSWER"); assertThat(outcome.usage().modelCalls()).isEqualTo(2);
        execute(f, accepted); // Completion replay cannot call the model again.
        verify(ai, times(1)).chatWithIdempotency(any(), any(), eq(accepted.operationId() + ":assistant:plan"));
        verify(ai, times(2)).chatWithIdempotency(any(), any(), eq(accepted.operationId() + ":assistant:final"));
    }

    @Test void staleH2ReadyProposalRemainsReadableWithoutRollingBackItsDetailTransaction() {
        Fixture f = fixture(); enableH2(f); String before = html(f);
        assertThat(AopUtils.isAopProxy(narrative)).isTrue();
        assertThat(mockingDetails(narrative).isMock()).isFalse();
        var m = manuscripts.findById(f.manuscript()).orElseThrow();
        Accepted accepted = assistant.submit(f.user(), f.manuscript(), f.scene(),
                new TurnRequest(f.branch(), m.getVersion(), "修改动作并保留结尾", before, null, null, "EDIT"), "h2-stale-submit");
        respondWithProposal(before); execute(f, accepted);
        UUID proposalId = assistant.turn(f.user(), f.manuscript(), f.scene(), accepted.turnId()).proposalId();
        var frozen = store.read(store.turn(accepted.turnId()).frozenJson(), WritingAssistantService.Frozen.class);
        narrative.requireStamp(f.user(), frozen.context().stamp());
        transactions.executeWithoutResult(status -> {
            var edited = manuscripts.findById(f.manuscript()).orElseThrow();
            contents.writeScene(edited, f.otherScene(), "<p>作者更新了另一场景，旧 H2 版本戳必须失效。</p>");
            manuscripts.saveAndFlush(edited);
        });
        assertThat(narrative.stampCurrent(f.user(), frozen.context().stamp())).isFalse();
        assertThatThrownBy(() -> narrative.requireStamp(f.user(), frozen.context().stamp()))
                .isInstanceOf(ApiStatusException.class).hasMessage("H2_CONTEXT_CHANGED");

        // No surrounding test transaction: a rollback-only detail transaction would throw on return.
        var detail = assistant.proposal(f.user(), f.manuscript(), f.scene(), proposalId);
        assertThat(detail.status()).isEqualTo("READY");
        assertThat(detail.applicable()).isFalse();
        assertThat(detail.staleReason()).isEqualTo("ASSISTANT_CONTEXT_CHANGED");
        assertThat(detail.beforeHtml()).isEqualTo(before);
        assertThat(detail.afterHtml()).contains("她把信封折进掌心。", "天亮前她没有拆开。");
        long currentVersion = manuscripts.findById(f.manuscript()).orElseThrow().getVersion();
        assertThatThrownBy(() -> assistant.decide(f.user(), f.manuscript(), f.scene(), proposalId, "accept",
                new DecisionRequest(currentVersion, f.branch()), "h2-stale-accept"))
                .isInstanceOf(ApiStatusException.class).hasMessage("ASSISTANT_CONTEXT_CHANGED");
        assertThat(html(f)).isEqualTo(before);
        assertThat(store.proposal(proposalId, false).status()).isEqualTo("READY");
        verify(ai, times(1)).chatWithIdempotency(any(), any(), eq(accepted.operationId() + ":assistant:plan"));
    }

    @Test void h2ProposalDetailRemainsReadableAfterAcceptAndUndoInvalidateItsOriginalStamp() {
        Fixture f = fixture(); enableH2(f); String before = html(f);
        assertThat(AopUtils.isAopProxy(narrative)).isTrue();
        var m = manuscripts.findById(f.manuscript()).orElseThrow();
        Accepted accepted = assistant.submit(f.user(), f.manuscript(), f.scene(),
                new TurnRequest(f.branch(), m.getVersion(), "修改动作并保留结尾", before, null, null, "EDIT"), "h2-undo-submit");
        respondWithProposal(before); execute(f, accepted);
        UUID proposalId = assistant.turn(f.user(), f.manuscript(), f.scene(), accepted.turnId()).proposalId();
        var frozen = store.read(store.turn(accepted.turnId()).frozenJson(), WritingAssistantService.Frozen.class);
        var saved = assistant.decide(f.user(), f.manuscript(), f.scene(), proposalId, "accept",
                new DecisionRequest(m.getVersion(), f.branch()), "h2-accept-once");
        assertThat(assistant.proposal(f.user(), f.manuscript(), f.scene(), proposalId).applicable()).isTrue();
        var undone = assistant.decide(f.user(), f.manuscript(), f.scene(), proposalId, "undo",
                new DecisionRequest(saved.manuscript().version(), f.branch()), "h2-undo-once");
        assertThat(undone.status()).isEqualTo("UNDONE");
        assertThat(undone.manuscript().version()).isEqualTo(m.getVersion() + 2);
        assertThat(narrative.stampCurrent(f.user(), frozen.context().stamp())).isFalse();
        assertThatThrownBy(() -> narrative.requireStamp(f.user(), frozen.context().stamp()))
                .isInstanceOf(ApiStatusException.class).hasMessage("H2_CONTEXT_CHANGED");

        var detail = assistant.proposal(f.user(), f.manuscript(), f.scene(), proposalId);
        assertThat(detail.status()).isEqualTo("UNDONE");
        assertThat(detail.applicable()).isFalse();
        assertThat(detail.beforeHtml()).isEqualTo(before);
        assertThat(detail.afterHtml()).contains("她把信封折进掌心。", "天亮前她没有拆开。");
        assertThat(html(f)).isEqualTo(before);
        verify(ai, times(1)).chatWithIdempotency(any(), any(), eq(accepted.operationId() + ":assistant:plan"));
        verifyNoInteractions(generator, attribution);
    }

    private void enableH2(Fixture f) {
        var state = narrative.state(f.user(), f.manuscript(), f.branch());
        var document = new NarrativeContextDtos.Document(
                new NarrativeContextDtos.Policy(NarrativeContextDtos.Perspective.OMNISCIENT, true, Map.of()), List.of(), List.of());
        narrative.update(f.user(), f.manuscript(), f.branch(), new NarrativeContextDtos.Update(state.manuscriptVersion(),
                state.canonRevision(), state.revision(), state.settingsRevision(), true, document), "h2-configuration");
    }

    private void respondWithProposal(String before) {
        String run = AssistantDocumentProjection.project(before, null).runs().getFirst().runId();
        when(ai.chatWithIdempotency(any(), any(), anyString())).thenReturn(response(store.write(Map.of("kind", "EDIT_PROPOSAL", "intent", "EDIT",
                "answer", "动作更具体，保留结尾。", "replacements", List.of(Map.of("runId", run, "paragraphs", List.of("她把信封折进掌心。", "天亮前她没有拆开。")))))));
    }

    private AiChatResponse response(String content) { return new AiChatResponse("assistant", content, new AiUsageDto(10, 20, 0, 0, 1), 99); }

    private void execute(Fixture f, Accepted accepted) {
        assistant.execute(new AiOperationExecution(accepted.operationId(), f.user(), "{}", json, (label, completed, total) -> {}), accepted.turnId());
    }

    private String snapshotScene(UUID snapshot, UUID scene) {
        String body = jdbc.queryForObject("select sections_json from manuscript_versions where id=?", String.class, bytes(snapshot));
        try { return json.readTree(body).path(scene.toString()).asText(); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }
}
