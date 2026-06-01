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
