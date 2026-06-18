package com.ainovel.app.quality;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlopCalibrationCorpusTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @ParameterizedTest(name = "{0}")
    @MethodSource("samples")
    void shouldApplyPhase5CalibrationSamples(String sampleId, JsonNode sample) {
        LocalSlopHeuristics heuristics = new LocalSlopHeuristics();

        SlopHeuristicResult result = heuristics.evaluate(new SlopHeuristicInput(
                text(sample, "text"),
                text(sample, "story_title"),
                text(sample, "genre"),
                text(sample, "tone"),
                text(sample, "chapter_title"),
                text(sample, "scene_title"),
                text(sample, "character_context"),
                text(sample, "style_context")
        ));
        SlopQualitySignals signals = SlopQualitySignals.fromIssues(
                result.overallRiskScore(),
                result.maxSeverity(),
                result.issues()
        );

        assertEquals(text(sample, "expected_evidence_level"), signals.evidenceLevel(), sampleId);
        assertEquals(sample.path("expected_requires_ai_review").asBoolean(), result.requiresAiReview(), sampleId);
        if (sample.has("min_risk_score")) {
            assertTrue(result.overallRiskScore() >= sample.path("min_risk_score").asInt(), sampleId);
        }
        if (sample.has("max_risk_score")) {
            assertTrue(result.overallRiskScore() <= sample.path("max_risk_score").asInt(), sampleId);
        }
        assertFalse(signals.safeClaim().contains("作者用了AI"), sampleId);
        assertFalse(signals.safeClaim().contains("AI生成概率"), sampleId);
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> samples() throws Exception {
        InputStream stream = SlopCalibrationCorpusTest.class
                .getResourceAsStream("/quality/slop-calibration-samples.jsonl");
        if (stream == null) {
            throw new IllegalStateException("Missing slop calibration samples");
        }
        List<org.junit.jupiter.params.provider.Arguments> arguments = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode sample = OBJECT_MAPPER.readTree(line);
                arguments.add(org.junit.jupiter.params.provider.Arguments.of(text(sample, "sample_id"), sample));
            }
        }
        return arguments.stream();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText();
    }
}
