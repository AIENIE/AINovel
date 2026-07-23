package com.ainovel.app.quality;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

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
        SlopIssueDraft generic = result.issues().stream()
                .filter(issue -> issue.dimension() == SlopDimension.GENERICITY)
                .findFirst()
                .orElseThrow();
        assertEquals("E2", generic.evidenceLevel());
        assertTrue(generic.charStart() >= 0);
        assertTrue(generic.charEnd() > generic.charStart());
        assertEquals("surface_template", generic.module());
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

    @Test
    void everyActiveRuleHasExecutablePositiveAndContextualSamples() {
        SlopPatternRegistry registry = new SlopPatternRegistry();
        LocalSlopHeuristics heuristics = new LocalSlopHeuristics(registry);

        for (SlopPatternRule rule : registry.activeRules()) {
            for (String sample : rule.positiveSamples()) {
                SlopHeuristicResult result = heuristics.evaluate(sample);
                assertTrue(result.issues().stream().anyMatch(issue -> rule.id().equals(issue.patternId())), rule.id());
            }
            for (SlopPatternRule.ContextSample sample : rule.contextualSamples()) {
                SlopHeuristicResult result = heuristics.evaluate(new SlopHeuristicInput(
                        sample.text(), "", "", "", "", "", "", sample.contextHint()));
                SlopIssueDraft issue = result.issues().stream()
                        .filter(value -> rule.id().equals(value.patternId()))
                        .findFirst().orElseThrow(() -> new AssertionError(rule.id()));
                assertTrue(issue.riskScore() <= 28, rule.id());
                assertFalse(result.requiresAiReview(), rule.id());
            }
        }
    }

    @Test
    void appliesExactSameFamilyDensityBoundariesInFiveHundredCharacters() {
        LocalSlopHeuristics heuristics = new LocalSlopHeuristics();

        assertEquals(34, heuristics.evaluate("嘴角微微上扬。随后指节泛白。").overallRiskScore());
        assertEquals(58, heuristics.evaluate("嘴角微微上扬。随后指节泛白。她又喉咙发紧。").overallRiskScore());
        assertEquals(72, heuristics.evaluate("嘴角微微上扬。指节泛白。喉咙发紧。耳尖泛红。呼吸一滞。").overallRiskScore());
    }

    @Test
    void appliesExactCrossCategoryDensityBoundariesInFiveHundredCharacters() {
        LocalSlopHeuristics heuristics = new LocalSlopHeuristics();

        assertEquals(34, heuristics.evaluate("她嘴角微微上扬。窗外阳光透过窗户洒进来。").overallRiskScore());
        assertEquals(58, heuristics.evaluate("她嘴角微微上扬。窗外阳光透过窗户洒进来。她心中涌起一股寒意。").overallRiskScore());
        assertEquals(72, heuristics.evaluate("她嘴角微微上扬。窗外阳光透过窗户洒进来。她心中涌起一股寒意。夜还很长。").overallRiskScore());
    }

    @Test
    void countsRepeatedMatcherOccurrencesAndKeepsOriginalOffsets() {
        LocalSlopHeuristics heuristics = new LocalSlopHeuristics();
        String text = "<p>她嘴角微微上扬，走向甲。</p>\n<div>稍后她嘴角微微上扬，走向乙。</div>\n末了她嘴角微微上扬，走向丙。";

        SlopHeuristicResult result = heuristics.evaluate(text);
        SlopIssueDraft issue = result.issues().stream()
                .filter(value -> "BODY_MOUTH_CURVE".equals(value.patternId())).findFirst().orElseThrow();

        assertEquals(58, issue.riskScore());
        assertEquals(issue.quote(), text.substring(issue.charStart(), issue.charEnd()));
        assertEquals(text.indexOf("嘴角微微上扬"), issue.charStart());
    }

    @Test
    void emitsOneE4IssuePerDistinctProviderArtifact() {
        SlopHeuristicResult result = new LocalSlopHeuristics().evaluate(
                "turn0search2 [cite: 1] grok_card 【2†L10-L14】");

        assertEquals(4, result.issues().stream().filter(issue -> "E4".equals(issue.evidenceLevel())).count());
        assertEquals(88, result.overallRiskScore());
    }

    @Test
    void capsTotalIssuesAtTwelve() {
        SlopHeuristicResult result = new LocalSlopHeuristics().evaluate("""
                ``` # 标题 作为一个AI 以下是为你 希望这能帮助到你 系统提示词
                <|assistant|> 分析如下： 抱歉，我刚才 turn0search2 oai_citation
                [cite: 1] [start_span] grok_card grok_render_citation_card_json 【2†L10-L14】
                """);

        assertEquals(12, result.issues().size());
    }

    @Test
    void shadowSignalsNeverChangeRiskIssuesSeverityOrReview() {
        String text = "首先检查门锁。其次记录脚印。此外比对灰尘。同时询问门卫。因此排除正门。然而窗台仍有泥。最后封存钥匙。";

        SlopHeuristicResult result = new LocalSlopHeuristics().evaluate(text);

        assertEquals(0, result.overallRiskScore());
        assertEquals(SlopSeverity.LOW, result.maxSeverity());
        assertFalse(result.requiresAiReview());
        assertTrue(result.issues().isEmpty());
        assertTrue(result.shadowHits().stream().anyMatch(hit -> "SHADOW_CONNECTOR_DENSITY".equals(hit.patternId())));
    }

    @Test
    void evaluatesOneHundredThousandCharactersWithinControlledTimeout() {
        String text = "雨落在锈铁门上，林烬用刀背挑开泥里的铜扣。".repeat(2_100);
        LocalSlopHeuristics heuristics = new LocalSlopHeuristics();

        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> heuristics.evaluate(text));
    }
}
