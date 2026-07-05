package com.ainovel.app.quality;

import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.dto.AiChatResponse;
import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.common.JsonColumnCodec;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.quality.model.PlotQualityRun;
import com.ainovel.app.quality.repo.PlotQualityRunRepository;
import com.ainovel.app.story.model.CharacterCard;
import com.ainovel.app.story.model.Outline;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.story.repo.CharacterCardRepository;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlotQualityServiceTest {

    @Test
    void analyzeSceneShouldBuildContextAndParsePlotIssues() {
        AiService aiService = mock(AiService.class);
        ManuscriptRepository manuscriptRepository = mock(ManuscriptRepository.class);
        CharacterCardRepository characterCardRepository = mock(CharacterCardRepository.class);
        PlotQualityRunRepository repository = mock(PlotQualityRunRepository.class);
        SlopQualityGate slopQualityGate = mock(SlopQualityGate.class);
        PlotQualityService service = new PlotQualityService(
                aiService,
                new ObjectMapper(),
                manuscriptRepository,
                characterCardRepository,
                repository,
                slopQualityGate,
                new JsonColumnCodec(new ObjectMapper())
        );
        User user = user();
        UUID sceneId = UUID.randomUUID();
        UUID previousSceneId = UUID.randomUUID();
        Manuscript manuscript = manuscriptWithSceneContext(sceneId, previousSceneId, "上一场景发现铜扣。", "林烬突然放弃追查，转身离开雨巷。");
        Story story = manuscript.getOutline().getStory();
        when(characterCardRepository.findByStory(story)).thenReturn(List.of(characterCard()));

        when(aiService.chat(any(), any())).thenReturn(new AiChatResponse("assistant", """
                {
                  "risk_score": 76,
                  "max_severity": "HIGH",
                  "summary": "场景目标明确，但关键转折缺少动机桥接。",
                  "issues": [
                    {
                      "dimension": "AGENCY",
                      "severity": "HIGH",
                      "risk_score": 82,
                      "evidence": "主角突然放弃追查",
                      "why_it_matters": "角色行动缺少可理解的内在理由",
                      "minimal_fix": "补一拍主角权衡失败代价"
                    }
                  ],
                  "rewrite_plan": [
                    "保留主角追查目标",
                    "补出放弃追查前的阻力",
                    "让角色明确选择暂退"
                  ],
                  "surgical_fixes": ["补动机桥接"]
                }
                """, null, 0));
        when(repository.save(any(PlotQualityRun.class))).thenAnswer(invocation -> {
            PlotQualityRun run = invocation.getArgument(0);
            run.setId(UUID.randomUUID());
            return run;
        });

        PlotQualityRun run = service.analyzeScene(user, manuscript, sceneId);

        assertEquals(PlotQualityStatus.ACCEPTED_WITH_ISSUES, run.getStatus());
        assertEquals(76, run.getOverallRiskScore());
        assertEquals(PlotQualitySeverity.HIGH, run.getMaxSeverity());
        assertEquals(1, run.getIssues().size());
        assertEquals(PlotQualityDimension.AGENCY, run.getIssues().get(0).getDimension());
        assertTrue(run.getRewritePlanJson().contains("保留主角追查目标"));
        ArgumentCaptor<AiChatRequest> requestCaptor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiService).chat(any(), requestCaptor.capture());
        String prompt = requestCaptor.getValue().messages().get(0).content();
        assertTrue(prompt.contains("雨城疑案"));
        assertTrue(prompt.contains("主线是追查副将复活真相"));
        assertTrue(prompt.contains("上一场景发现铜扣"));
        assertTrue(prompt.contains("林烬"));
    }

    @Test
    void trendShouldAggregateLatestSceneRunsInChapterOrder() {
        PlotQualityRunRepository repository = mock(PlotQualityRunRepository.class);
        PlotQualityService service = new PlotQualityService(
                mock(AiService.class),
                new ObjectMapper(),
                mock(ManuscriptRepository.class),
                mock(CharacterCardRepository.class),
                repository,
                mock(SlopQualityGate.class),
                new JsonColumnCodec(new ObjectMapper())
        );
        UUID manuscriptId = UUID.randomUUID();
        UUID sceneA = UUID.randomUUID();
        UUID sceneB = UUID.randomUUID();

        when(repository.findTop200ByManuscriptIdOrderByCreatedAtDesc(manuscriptId)).thenReturn(List.of(
                run(manuscriptId, sceneB, 1, 2, 44, PlotQualitySeverity.MEDIUM),
                run(manuscriptId, sceneA, 1, 1, 72, PlotQualitySeverity.HIGH),
                run(manuscriptId, sceneA, 1, 1, 20, PlotQualitySeverity.LOW)
        ));

        PlotQualityTrend trend = service.buildTrend(manuscriptId);

        assertEquals(2, trend.points().size());
        assertEquals(sceneA, trend.points().get(0).sceneId());
        assertEquals(72, trend.points().get(0).riskScore());
        assertEquals(sceneB, trend.points().get(1).sceneId());
        assertEquals(58.0, trend.averageRisk(), 0.001);
        assertTrue(trend.dimensionCounts().containsKey(PlotQualityDimension.AGENCY.name()));
    }

    @Test
    void applyRevisionShouldRejectWhenCurrentTextHashChanged() {
        PlotQualityRunRepository repository = mock(PlotQualityRunRepository.class);
        PlotQualityService service = new PlotQualityService(
                mock(AiService.class),
                new ObjectMapper(),
                mock(ManuscriptRepository.class),
                mock(CharacterCardRepository.class),
                repository,
                mock(SlopQualityGate.class),
                new JsonColumnCodec(new ObjectMapper())
        );
        UUID runId = UUID.randomUUID();
        PlotQualityRun run = new PlotQualityRun();
        run.setId(runId);
        run.setSourceTextHash(PlotQualityService.hashText("旧正文"));
        run.setRevisionCandidateText("新正文");
        run.setSceneId(UUID.randomUUID());
        Manuscript manuscript = manuscriptWithSection(run.getSceneId(), "正文已被用户修改");
        run.setManuscriptId(manuscript.getId());

        when(repository.findById(runId)).thenReturn(Optional.of(run));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.applyRevision(user(), manuscript, runId)
        );
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void applyRevisionShouldPersistAcceptedTextIntoManuscript() {
        ManuscriptRepository manuscriptRepository = mock(ManuscriptRepository.class);
        PlotQualityRunRepository repository = mock(PlotQualityRunRepository.class);
        SlopQualityGate slopQualityGate = mock(SlopQualityGate.class);
        PlotQualityService service = new PlotQualityService(
                mock(AiService.class),
                new ObjectMapper(),
                manuscriptRepository,
                mock(CharacterCardRepository.class),
                repository,
                slopQualityGate,
                new JsonColumnCodec(new ObjectMapper())
        );
        UUID runId = UUID.randomUUID();
        UUID sceneId = UUID.randomUUID();
        PlotQualityRun run = new PlotQualityRun();
        run.setId(runId);
        run.setSceneId(sceneId);
        run.setManuscriptId(UUID.randomUUID());
        run.setSourceTextHash(PlotQualityService.hashText("旧正文"));
        run.setRevisionCandidateText("修订候选");
        Manuscript manuscript = manuscriptWithSection(sceneId, "旧正文");
        manuscript.setId(run.getManuscriptId());

        when(repository.findById(runId)).thenReturn(Optional.of(run));
        when(manuscriptRepository.save(any(Manuscript.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any(PlotQualityRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(slopQualityGate.evaluateAndRepair(any(), any())).thenReturn(new SlopQualityResult(
                runId,
                "修订后正文",
                18,
                SlopSeverity.LOW,
                false,
                0,
                SlopQualityStatus.ACCEPTED,
                List.of()
        ));

        PlotQualityRun revised = service.applyRevision(user(), manuscript, runId);

        assertTrue(revised.isRevisionApplied());
        assertEquals(PlotQualityService.hashText("修订后正文"), revised.getSourceTextHash());
        assertTrue(manuscript.getSectionsJson().contains("<p>修订后正文</p>"));
        verify(manuscriptRepository).save(manuscript);
        verify(repository).save(run);
    }

    private PlotQualityRun run(UUID manuscriptId, UUID sceneId, int chapterOrder, int sceneOrder, int risk, PlotQualitySeverity severity) {
        PlotQualityRun run = new PlotQualityRun();
        run.setId(UUID.randomUUID());
        run.setManuscriptId(manuscriptId);
        run.setSceneId(sceneId);
        run.setChapterOrder(chapterOrder);
        run.setSceneOrder(sceneOrder);
        run.setOverallRiskScore(risk);
        run.setMaxSeverity(severity);
        run.setStatus(risk >= 70 ? PlotQualityStatus.ACCEPTED_WITH_ISSUES : PlotQualityStatus.ACCEPTED);
        run.getIssues().add(PlotQualityIssueFactory.issue(run, PlotQualityDimension.AGENCY, severity, risk));
        return run;
    }

    private Manuscript manuscriptWithSection(UUID sceneId, String text) {
        Manuscript manuscript = new Manuscript();
        manuscript.setId(UUID.randomUUID());
        manuscript.setOutline(null);
        manuscript.setSectionsJson("{\"" + sceneId + "\":\"<p>" + text + "</p>\"}");
        return manuscript;
    }

    private Manuscript manuscriptWithSceneContext(UUID sceneId, UUID previousSceneId, String previousText, String currentText) {
        Story story = new Story();
        story.setId(UUID.randomUUID());
        story.setTitle("雨城疑案");
        story.setGenre("悬疑");
        story.setTone("冷峻");
        Outline outline = new Outline();
        outline.setStory(story);
        outline.setContentJson("""
                {"planning":{"corePromise":"主线是追查副将复活真相"},"chapters":[{"title":"第一章","order":1,"scenes":[
                  {"id":"%s","title":"雨巷","summary":"发现铜扣","order":1},
                  {"id":"%s","title":"雨夜门外","summary":"主角追查副将复活线索","order":2}
                ]}]}
                """.formatted(previousSceneId, sceneId));
        Manuscript manuscript = new Manuscript();
        manuscript.setId(UUID.randomUUID());
        manuscript.setOutline(outline);
        manuscript.setSectionsJson("""
                {"%s":"<p>%s</p>","%s":"<p>%s</p>"}
                """.formatted(previousSceneId, previousText, sceneId, currentText));
        return manuscript;
    }

    private CharacterCard characterCard() {
        CharacterCard card = new CharacterCard();
        card.setName("林烬");
        card.setSynopsis("谨慎、重视证据");
        card.setDetails("习惯先观察现场，再决定下一步。");
        card.setRelationships("与副将旧案有私人牵连。");
        return card;
    }

    private User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("plot_user");
        user.setEmail("plot_user@example.com");
        user.setPasswordHash("x");
        user.setRemoteUid(9000011L);
        return user;
    }

    private static final class PlotQualityIssueFactory {
        static com.ainovel.app.quality.model.PlotQualityIssue issue(PlotQualityRun run,
                                                                    PlotQualityDimension dimension,
                                                                    PlotQualitySeverity severity,
                                                                    int risk) {
            com.ainovel.app.quality.model.PlotQualityIssue issue = new com.ainovel.app.quality.model.PlotQualityIssue();
            issue.setRun(run);
            issue.setDimension(dimension);
            issue.setSeverity(severity);
            issue.setRiskScore(risk);
            issue.setEvidence("主角突然放弃追查");
            return issue;
        }
    }
}
