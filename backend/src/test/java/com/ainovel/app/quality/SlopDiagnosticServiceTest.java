package com.ainovel.app.quality;

import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.dto.AiChatResponse;
import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.quality.model.SlopQualityRun;
import com.ainovel.app.quality.repo.SlopQualityRunRepository;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlopDiagnosticServiceTest {

    @Test
    void analyzeShouldPersistEvidenceLevelModuleScoresAndRewriteTasks() {
        AiService aiService = mock(AiService.class);
        SlopQualityRunRepository repository = mock(SlopQualityRunRepository.class);
        SlopDiagnosticService service = new SlopDiagnosticService(
                aiService,
                new ObjectMapper(),
                new LocalSlopHeuristics(),
                repository
        );
        UUID manuscriptId = UUID.randomUUID();
        UUID sceneId = UUID.randomUUID();

        when(aiService.chat(any(), any())).thenReturn(new AiChatResponse("assistant", """
                {
                  "overall": {
                    "overall_slop_risk": 72,
                    "risk_label": "high",
                    "evidence_level": "E2",
                    "safe_claim": "该片段呈现较高模板化风险，但不能证明作者使用 AI。"
                  },
                  "module_scores": {
                    "surface_template": {"score": 78},
                    "breath_focus_pacing": {"score": 64}
                  },
                  "evidence_items": [
                    {
                      "char_start": 2,
                      "char_end": 12,
                      "quote": "空气仿佛凝固",
                      "module": "surface_template",
                      "pattern_id": "SURFACE_GENERIC_001",
                      "issue_type": "phrase_pattern",
                      "evidence_level": "E2",
                      "severity": "high",
                      "risk_explanation": "短窗口内模板氛围句和抽象情绪句共振。",
                      "alternative_explanations": ["传统网文俗套", "作者个人文风"],
                      "repair_hint": "改为当前场景独有的可见动作。"
                    }
                  ],
                  "alternative_explanations": ["传统网文俗套", "工作室公式化"],
                  "revision_priorities": ["先处理共振片段，再处理单点套话"],
                  "rewrite_tasks": [
                    {
                      "task_id": "R1",
                      "priority": 1,
                      "problem": "模板氛围句密度偏高",
                      "repair_goal": "用具体动作和后果替代表层套话"
                    }
                  ]
                }
                """, null, 0));
        when(repository.save(any(SlopQualityRun.class))).thenAnswer(invocation -> {
            SlopQualityRun run = invocation.getArgument(0);
            run.setId(UUID.randomUUID());
            return run;
        });

        SlopQualityRun run = service.analyze(user(), new SlopQualityRequest(
                UUID.randomUUID(),
                manuscriptId,
                sceneId,
                "雨城疑案",
                "悬疑",
                "冷峻",
                "第一章",
                "雨夜门外",
                "主角发现线索",
                "上一场景发现铜扣",
                "林烬：谨慎、重视证据",
                "Active style profile: 冷峻悬疑画像\nCharacter voice: 林烬 speechPattern=短句、反问、重视证据",
                "雨水砸在铁皮棚上，空气仿佛凝固。林烬心中涌起一股说不出的感觉。"
        ));

        ArgumentCaptor<AiChatRequest> requestCaptor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiService).chat(any(), requestCaptor.capture());
        String prompt = requestCaptor.getValue().messages().get(0).content();
        assertTrue(prompt.contains("风格/角色声音语境"));
        assertTrue(prompt.contains("Active style profile: 冷峻悬疑画像"));
        assertTrue(prompt.contains("Character voice: 林烬 speechPattern=短句、反问、重视证据"));
        assertEquals("manual_scene", run.getAnalysisMode());
        assertEquals("high", run.getRiskLabel());
        assertEquals("E2", run.getEvidenceLevel());
        assertTrue(run.getSafeClaim().contains("不能证明作者使用 AI"));
        assertTrue(run.getModuleScoresJson().contains("surface_template"));
        assertTrue(run.getRewriteTasksJson().contains("R1"));
        assertEquals(72, run.getOverallRiskScore());
        assertEquals(SlopSeverity.HIGH, run.getMaxSeverity());
        assertEquals(1, run.getIssues().size());
        assertEquals(2, run.getIssues().get(0).getCharStart());
        assertEquals("surface_template", run.getIssues().get(0).getModule());
        assertEquals("E2", run.getIssues().get(0).getEvidenceLevel());
    }

    private User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("slop_user");
        user.setEmail("slop_user@example.com");
        user.setPasswordHash("x");
        user.setRemoteUid(9000021L);
        return user;
    }
}
