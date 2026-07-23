package com.ainovel.app.quality;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlopQualitySignalsTest {

    @Test
    void shouldKeepMultipleWeakSignalsAtE1WhenRiskStaysLow() {
        List<SlopIssueDraft> issues = List.of(
                new SlopIssueDraft(
                        SlopDimension.GENERICITY,
                        SlopSeverity.LOW,
                        28,
                        "眼神变得坚定",
                        "单点题材俗套。",
                        "仅在密度升高时优先修改。",
                        1,
                        7,
                        "眼神变得坚定",
                        "surface_template",
                        "SURFACE_GENERIC_PHRASE",
                        "phrase_pattern",
                        "E1",
                        "[\"传统网文俗套\"]",
                        "仅在密度升高时优先修改。"
                ),
                new SlopIssueDraft(
                        SlopDimension.GENERICITY,
                        SlopSeverity.LOW,
                        30,
                        "心中涌起一股",
                        "单点情绪套话。",
                        "结合场景具体化。",
                        18,
                        25,
                        "心中涌起一股",
                        "surface_template",
                        "SURFACE_GENERIC_PHRASE",
                        "phrase_pattern",
                        "E1",
                        "[\"作者个人文风\"]",
                        "结合场景具体化。"
                )
        );

        SlopQualitySignals signals = SlopQualitySignals.fromIssues(35, SlopSeverity.LOW, issues);

        assertEquals("E1", signals.evidenceLevel());
        assertEquals("low", signals.riskLabel());
    }

    @Test
    void shadowMetadataDoesNotChangeUserFacingSignals() {
        SlopQualitySignals original = SlopQualitySignals.fromIssues(0, SlopSeverity.LOW, List.of());

        SlopQualitySignals enriched = original.withShadowHits(List.of(
                new SlopShadowHit("SHADOW_CONNECTOR_DENSITY", "SHADOW_CONNECTOR_DENSITY", 6, 1, 20, "首先……最后")
        ));

        assertEquals(original.riskLabel(), enriched.riskLabel());
        assertEquals(original.evidenceLevel(), enriched.evidenceLevel());
        assertEquals(original.safeClaim(), enriched.safeClaim());
        assertTrue(enriched.moduleScores().containsKey("_shadow_pattern_hits"));
        assertFalse(enriched.toString().contains("ChatGPT"));
    }
}
