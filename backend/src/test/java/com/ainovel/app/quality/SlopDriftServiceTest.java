package com.ainovel.app.quality;

import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.dto.AiChatResponse;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.quality.model.SlopDriftRun;
import com.ainovel.app.quality.repo.SlopDriftRunRepository;
import com.ainovel.app.story.model.Outline;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlopDriftServiceTest {

    @Test
    void analyzeShouldPersistLlmWindowComparison() {
        AiService aiService = mock(AiService.class);
        SlopDriftRunRepository repository = mock(SlopDriftRunRepository.class);
        SlopDriftService service = new SlopDriftService(aiService, new ObjectMapper(), repository);
        Manuscript manuscript = manuscriptWithRepeatedSections("雨水砸在铁皮棚上，林烬记住了铜扣的划痕。", 12);

        when(aiService.chat(any(), any())).thenReturn(new AiChatResponse("assistant", """
                {
                  "summary": "中后段模板化和事件传送带风险升高。",
                  "overall": {
                    "midbook_drift_risk_score": 76,
                    "risk_label": "high",
                    "safe_claim": "该稿件在中后段呈现叙事机制断层风险；这不能证明作者使用 AI。"
                  },
                  "window_summaries": [
                    {"label": "opening", "summary": "开头具体"},
                    {"label": "latest", "summary": "后段事件密集"}
                  ],
                  "metric_curves": {
                    "template_density": [{"window": "opening", "score": 24}, {"window": "latest", "score": 78}],
                    "role_stability": [{"window": "opening", "score": 82}, {"window": "latest", "score": 41}]
                  },
                  "drift_points": [
                    {
                      "from_window": "opening",
                      "to_window": "latest",
                      "changed_metrics": ["template_density", "role_stability"],
                      "interpretation": "后段赞美词和抽象总结密度升高。",
                      "safe_claim": "文本出现文风和叙事机制断层风险。"
                    }
                  ],
                  "evidence_items": [
                    {
                      "window": "latest",
                      "quote": "事件接连发生，众人纷纷震惊",
                      "module": "breath_focus_pacing",
                      "evidence_level": "E2",
                      "risk_score": 72,
                      "risk_explanation": "连续事件缺少后果消化。",
                      "repair_hint": "补出上一事件造成的具体代价。"
                    }
                  ],
                  "alternative_explanations": ["赶稿", "平台节奏压力"],
                  "rewrite_tasks": [
                    {"task_id": "D1", "priority": 1, "problem": "后段事件传送带", "repair_goal": "补后果和缓冲"}
                  ]
                }
                """, null, 0));
        when(repository.save(any(SlopDriftRun.class))).thenAnswer(invocation -> {
            SlopDriftRun run = invocation.getArgument(0);
            run.setId(UUID.randomUUID());
            return run;
        });

        SlopDriftRun run = service.analyze(user(), manuscript);

        assertEquals(SlopDriftStatus.COMPLETED, run.getStatus());
        assertEquals(76, run.getOverallRiskScore());
        assertEquals("high", run.getRiskLabel());
        assertTrue(run.getSafeClaim().contains("不能证明作者使用 AI"));
        assertTrue(run.getMetricCurvesJson().contains("template_density"));
        assertTrue(run.getDriftPointsJson().contains("role_stability"));
        assertTrue(run.getEvidenceItemsJson().contains("breath_focus_pacing"));
        assertTrue(run.getRewriteTasksJson().contains("D1"));
        assertTrue(run.getWindowCount() >= 3);
    }

    @Test
    void analyzeShouldSkipAiWhenTextHasTooFewWindows() {
        AiService aiService = mock(AiService.class);
        SlopDriftRunRepository repository = mock(SlopDriftRunRepository.class);
        SlopDriftService service = new SlopDriftService(aiService, new ObjectMapper(), repository);
        Manuscript manuscript = manuscriptWithRepeatedSections("短稿。", 2);

        when(repository.save(any(SlopDriftRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SlopDriftRun run = service.analyze(user(), manuscript);

        assertEquals(SlopDriftStatus.INSUFFICIENT_TEXT, run.getStatus());
        assertEquals(0, run.getOverallRiskScore());
        assertTrue(run.getSummary().contains("正文不足"));
        verify(aiService, never()).chat(any(), any());
    }

    private Manuscript manuscriptWithRepeatedSections(String sentence, int scenes) {
        User owner = user();
        Story story = new Story();
        story.setId(UUID.randomUUID());
        story.setUser(owner);
        story.setTitle("雨城疑案");
        story.setGenre("悬疑");
        story.setTone("冷峻");

        StringBuilder chaptersJson = new StringBuilder("{\"chapters\":[");
        StringBuilder sectionsJson = new StringBuilder("{");
        for (int i = 0; i < scenes; i++) {
            UUID sceneId = UUID.randomUUID();
            if (i > 0) {
                chaptersJson.append(',');
                sectionsJson.append(',');
            }
            chaptersJson.append("{\"title\":\"第").append(i + 1).append("章\",\"order\":").append(i + 1)
                    .append(",\"scenes\":[{\"id\":\"").append(sceneId)
                    .append("\",\"title\":\"场景").append(i + 1)
                    .append("\",\"summary\":\"推进线索\",\"order\":1}]}");
            sectionsJson.append('"').append(sceneId).append('"').append(":\"<p>")
                    .append(sentence.repeat(90))
                    .append("</p>\"");
        }
        chaptersJson.append("]}");
        sectionsJson.append('}');

        Outline outline = new Outline();
        outline.setId(UUID.randomUUID());
        outline.setStory(story);
        outline.setContentJson(chaptersJson.toString());

        Manuscript manuscript = new Manuscript();
        manuscript.setId(UUID.randomUUID());
        manuscript.setOutline(outline);
        manuscript.setSectionsJson(sectionsJson.toString());
        return manuscript;
    }

    private User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("drift_user");
        user.setEmail("drift_user@example.com");
        user.setPasswordHash("x");
        user.setRemoteUid(9000031L);
        return user;
    }
}
