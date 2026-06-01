package com.ainovel.app.quality;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalSlopHeuristicsTest {

    @Test
    void shouldFlagTemplateRepetitionAndArtifacts() {
        LocalSlopHeuristics heuristics = new LocalSlopHeuristics();

        SlopHeuristicResult result = heuristics.evaluate("""
                她的嘴角微微上扬，空气仿佛凝固，时间像是停了下来。
                她的嘴角微微上扬，空气仿佛凝固，时间像是停了下来。
                这是一个重要的时刻，这是一个重要的时刻。
                ```markdown
                """);

        assertTrue(result.requiresAiReview());
        assertTrue(result.overallRiskScore() >= 70);
        assertTrue(result.issues().stream().anyMatch(issue -> issue.dimension() == SlopDimension.REPETITION));
        assertTrue(result.issues().stream().anyMatch(issue -> issue.dimension() == SlopDimension.GENERICITY));
        assertTrue(result.issues().stream().anyMatch(issue -> issue.dimension() == SlopDimension.ARTIFACT));
    }

    @Test
    void shouldKeepSpecificConcreteTextLowRisk() {
        LocalSlopHeuristics heuristics = new LocalSlopHeuristics();

        SlopHeuristicResult result = heuristics.evaluate("""
                雨水沿着锈红色的铁门往下淌。林烬蹲在门槛前，用刀背挑开泥里的半枚铜扣。
                铜扣内侧刻着一个小小的“陆”字，边缘还沾着没有干透的蜡。
                """);

        assertFalse(result.requiresAiReview());
        assertEquals(SlopSeverity.LOW, result.maxSeverity());
    }
}
