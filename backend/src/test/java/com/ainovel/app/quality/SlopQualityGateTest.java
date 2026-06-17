package com.ainovel.app.quality;

import com.ainovel.app.user.User;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlopQualityGateTest {

    @Test
    void shouldRunOneConservativeRevisionWhenJudgeConfirmsHighRisk() {
        LocalSlopHeuristics heuristics = new LocalSlopHeuristics();
        SlopJudgeClient judgeClient = (user, request, heuristicResult) -> new SlopJudgeResult(
                82,
                true,
                List.of(new SlopIssueDraft(
                        SlopDimension.GENERICITY,
                        SlopSeverity.HIGH,
                        82,
                        "空气仿佛凝固",
                        "模板化氛围句",
                        "改成具体动作或感官细节"
                )),
                "先替换模板化氛围句"
        );
        ConservativeRevisionService revisionService = (user, request, judgeResult) -> "雨水砸在铁皮棚上，林烬听见门闩后面响了一声。";
        InMemorySlopQualityRecorder recorder = new InMemorySlopQualityRecorder();
        SlopQualityGate gate = new SlopQualityGate(heuristics, judgeClient, revisionService, recorder);

        SlopQualityResult result = gate.evaluateAndRepair(UserForTest.create(), new SlopQualityRequest(
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
                "她的嘴角微微上扬，空气仿佛凝固，时间像是停了下来。她的嘴角微微上扬，空气仿佛凝固，时间像是停了下来。"
        ));

        assertEquals("雨水砸在铁皮棚上，林烬听见门闩后面响了一声。", result.acceptedText());
        assertTrue(result.revised());
        assertEquals(1, result.revisionCount());
        assertEquals(1, recorder.recorded().size());
        assertTrue(recorder.recorded().get(0).revised());
    }

    @Test
    void shouldRecordRicherSignalsFromJudgeForGenerationGate() {
        LocalSlopHeuristics heuristics = new LocalSlopHeuristics();
        SlopQualitySignals signals = new SlopQualitySignals(
                "high",
                "E2",
                "该文本呈现 high 级模板化/slop风险；这不能证明作者使用AI。",
                java.util.Map.of("surface_template", java.util.Map.of("score", 78, "evidence_count", 2)),
                List.of("传统网文俗套", "作者个人文风"),
                List.of("先处理密度共振片段"),
                List.of(java.util.Map.of(
                        "task_id", "R1",
                        "priority", 1,
                        "problem", "模板氛围句密度偏高",
                        "repair_goal", "换成当前场景独有的动作和后果"
                ))
        );
        SlopJudgeClient judgeClient = (user, request, heuristicResult) -> new SlopJudgeResult(
                78,
                false,
                List.of(new SlopIssueDraft(
                        SlopDimension.GENERICITY,
                        SlopSeverity.HIGH,
                        78,
                        "空气仿佛凝固",
                        "模板化氛围句与抽象情绪句共振。",
                        "改成具体动作或感官细节",
                        8,
                        14,
                        "空气仿佛凝固",
                        "surface_template",
                        "SURFACE_GENERIC_PHRASE",
                        "phrase_pattern",
                        "E2",
                        "[\"传统网文俗套\"]",
                        "改成具体动作或感官细节"
                )),
                "先处理密度共振片段",
                signals
        );
        ConservativeRevisionService revisionService = (user, request, judgeResult) -> request.candidateText();
        InMemorySlopQualityRecorder recorder = new InMemorySlopQualityRecorder();
        SlopQualityGate gate = new SlopQualityGate(heuristics, judgeClient, revisionService, recorder);

        gate.evaluateAndRepair(UserForTest.create(), new SlopQualityRequest(
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
                "她的嘴角微微上扬，空气仿佛凝固，时间像是停了下来。她的嘴角微微上扬，空气仿佛凝固，时间像是停了下来。"
        ));

        SlopQualityRecord record = recorder.recorded().get(0);
        assertEquals("E2", record.signals().evidenceLevel());
        assertEquals("high", record.signals().riskLabel());
        assertTrue(record.signals().moduleScores().containsKey("surface_template"));
        assertEquals("R1", ((java.util.Map<?, ?>) record.signals().rewriteTasks().get(0)).get("task_id"));
    }

    private static class UserForTest {
        static User create() {
            User user = new User();
            user.setId(UUID.randomUUID());
            user.setUsername("quality_user");
            user.setEmail("quality_user@example.com");
            user.setPasswordHash("x");
            user.setRemoteUid(9000003L);
            return user;
        }
    }
}
