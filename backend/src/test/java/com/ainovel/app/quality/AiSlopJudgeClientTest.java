package com.ainovel.app.quality;

import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.dto.AiChatResponse;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiSlopJudgeClientTest {

    @Test
    void shouldParseRicherGenerationGateJson() {
        AiService aiService = mock(AiService.class);
        when(aiService.chat(any(), any())).thenReturn(new AiChatResponse("assistant", """
                {
                  "overall": {
                    "overall_slop_risk": 74,
                    "risk_label": "high",
                    "evidence_level": "E2",
                    "safe_claim": "该文本呈现 high 级模板化/slop风险；这不能证明作者使用AI。"
                  },
                  "revision_recommended": false,
                  "actionable_hint": "先处理密度共振片段",
                  "module_scores": {
                    "surface_template": {"score": 78, "evidence_count": 2}
                  },
                  "evidence_items": [
                    {
                      "char_start": 4,
                      "char_end": 10,
                      "quote": "空气仿佛凝固",
                      "module": "surface_template",
                      "pattern_id": "SURFACE_GENERIC_PHRASE",
                      "issue_type": "phrase_pattern",
                      "evidence_level": "E2",
                      "severity": "high",
                      "risk_score": 78,
                      "risk_explanation": "模板氛围句和抽象情绪句在短窗口共振。",
                      "alternative_explanations": ["传统网文俗套"],
                      "repair_hint": "换成具体动作。"
                    }
                  ],
                  "alternative_explanations": ["传统网文俗套", "作者个人文风"],
                  "revision_priorities": ["先处理共振片段"],
                  "rewrite_tasks": [
                    {
                      "task_id": "R1",
                      "priority": 1,
                      "problem": "模板句密度偏高",
                      "repair_goal": "换成具体动作"
                    }
                  ]
                }
                """, null, 0));
        AiSlopJudgeClient client = new AiSlopJudgeClient(aiService, new ObjectMapper());

        SlopJudgeResult result = client.judge(user(), request(), new LocalSlopHeuristics().evaluate("""
                雨水砸在铁皮棚上，空气仿佛凝固。林烬心中涌起一股说不出的感觉。
                """));

        assertEquals(74, result.riskScore());
        assertEquals("E2", result.signals().evidenceLevel());
        assertEquals("high", result.signals().riskLabel());
        assertTrue(result.signals().moduleScores().containsKey("surface_template"));
        assertEquals("R1", ((java.util.Map<?, ?>) result.signals().rewriteTasks().get(0)).get("task_id"));
        assertEquals(1, result.issues().size());
        assertEquals("surface_template", result.issues().get(0).module());
        assertEquals("E2", result.issues().get(0).evidenceLevel());
    }

    private SlopQualityRequest request() {
        return new SlopQualityRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "雨城疑案",
                "悬疑",
                "冷峻",
                "第一章",
                "雨夜门外",
                "主角发现线索",
                "前文暂无",
                "林烬：谨慎、重视证据",
                "雨水砸在铁皮棚上，空气仿佛凝固。林烬心中涌起一股说不出的感觉。"
        );
    }

    private User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("judge_user");
        user.setEmail("judge_user@example.com");
        user.setPasswordHash("x");
        user.setRemoteUid(9000031L);
        return user;
    }
}
