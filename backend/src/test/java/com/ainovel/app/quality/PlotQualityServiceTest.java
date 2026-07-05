package com.ainovel.app.quality;

import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.dto.AiChatResponse;
import com.ainovel.app.common.JsonColumnCodec;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.quality.model.PlotQualityRun;
import com.ainovel.app.quality.repo.PlotQualityRunRepository;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlotQualityServiceTest {

    @Test
    void analyzeShouldParsePlotIssuesAndRewritePlan() {
        AiService aiService = mock(AiService.class);
        PlotQualityRunRepository repository = mock(PlotQualityRunRepository.class);
        SlopQualityGate slopQualityGate = mock(SlopQualityGate.class);
        PlotQualityService service = new PlotQualityService(aiService, new ObjectMapper(), repository, slopQualityGate, new JsonColumnCodec(new ObjectMapper()));
        User user = user();
        UUID manuscriptId = UUID.randomUUID();
        UUID sceneId = UUID.randomUUID();

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

        PlotQualityRun run = service.analyze(user, new PlotQualityRequest(
                UUID.randomUUID(),
                manuscriptId,
                sceneId,
                "雨城疑案",
                "悬疑",
                "冷峻",
                "第一章",
                1,
                "雨夜门外",
                1,
                "主角追查副将复活线索",
                "主线是追查副将复活真相",
                "上一场景发现铜扣",
                "林烬：谨慎，重视证据",
                "林烬突然放弃追查，转身离开雨巷。"
        ));

        assertEquals(PlotQualityStatus.ACCEPTED_WITH_ISSUES, run.getStatus());
        assertEquals(76, run.getOverallRiskScore());
        assertEquals(PlotQualitySeverity.HIGH, run.getMaxSeverity());
        assertEquals(1, run.getIssues().size());
        assertEquals(PlotQualityDimension.AGENCY, run.getIssues().get(0).getDimension());
        assertTrue(run.getRewritePlanJson().contains("保留主角追查目标"));
    }

    @Test
    void trendShouldAggregateLatestSceneRunsInChapterOrder() {
        PlotQualityRunRepository repository = mock(PlotQualityRunRepository.class);
        PlotQualityService service = new PlotQualityService(mock(AiService.class), new ObjectMapper(), repository, mock(SlopQualityGate.class), new JsonColumnCodec(new ObjectMapper()));
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
        PlotQualityService service = new PlotQualityService(mock(AiService.class), new ObjectMapper(), repository, mock(SlopQualityGate.class), new JsonColumnCodec(new ObjectMapper()));
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
        Story story = new Story();
        story.setId(UUID.randomUUID());
        Manuscript manuscript = new Manuscript();
        manuscript.setId(UUID.randomUUID());
        manuscript.setOutline(null);
        manuscript.setSectionsJson("{\"" + sceneId + "\":\"<p>" + text + "</p>\"}");
        return manuscript;
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
