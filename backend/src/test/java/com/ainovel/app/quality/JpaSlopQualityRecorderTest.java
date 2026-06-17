package com.ainovel.app.quality;

import com.ainovel.app.quality.model.SlopQualityRun;
import com.ainovel.app.quality.repo.SlopQualityRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JpaSlopQualityRecorderTest {

    @Test
    void shouldPersistGenerationGateSignalsJson() {
        SlopQualityRunRepository repository = mock(SlopQualityRunRepository.class);
        when(repository.save(any(SlopQualityRun.class))).thenAnswer(invocation -> {
            SlopQualityRun run = invocation.getArgument(0);
            run.setId(UUID.randomUUID());
            return run;
        });
        JpaSlopQualityRecorder recorder = new JpaSlopQualityRecorder(repository, new ObjectMapper());
        SlopQualitySignals signals = new SlopQualitySignals(
                "high",
                "E2",
                "该文本呈现 high 级模板化/slop风险；这不能证明作者使用AI。",
                Map.of("surface_template", Map.of("score", 78, "evidence_count", 2)),
                List.of("传统网文俗套"),
                List.of("先处理共振片段"),
                List.of(Map.of("task_id", "R1", "problem", "模板句密集", "repair_goal", "换成具体动作"))
        );

        UUID id = recorder.record(new SlopQualityRecord(
                request(),
                "雨水砸在铁皮棚上。",
                78,
                SlopSeverity.HIGH,
                false,
                0,
                SlopQualityStatus.ACCEPTED_WITH_ISSUES,
                List.of(new SlopIssueDraft(
                        SlopDimension.GENERICITY,
                        SlopSeverity.HIGH,
                        78,
                        "空气仿佛凝固",
                        "模板化氛围句",
                        "换成具体动作"
                )),
                "先处理共振片段",
                signals
        ));

        assertTrue(id != null);
        SlopQualityRun saved = org.mockito.Mockito.mockingDetails(repository)
                .getInvocations()
                .stream()
                .filter(invocation -> invocation.getMethod().getName().equals("save"))
                .map(invocation -> (SlopQualityRun) invocation.getArgument(0))
                .findFirst()
                .orElseThrow();
        assertEquals("generation_gate", saved.getAnalysisMode());
        assertEquals("high", saved.getRiskLabel());
        assertEquals("E2", saved.getEvidenceLevel());
        assertTrue(saved.getModuleScoresJson().contains("surface_template"));
        assertTrue(saved.getRevisionPrioritiesJson().contains("先处理共振片段"));
        assertTrue(saved.getRewriteTasksJson().contains("R1"));
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
                "雨水砸在铁皮棚上，空气仿佛凝固。"
        );
    }
}
