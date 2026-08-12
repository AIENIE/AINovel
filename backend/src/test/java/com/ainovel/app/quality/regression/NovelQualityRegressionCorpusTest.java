package com.ainovel.app.quality.regression;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NovelQualityRegressionCorpusTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> DEFECT_TYPES = Set.of(
            "SPACE_ENTITY",
            "POV_KNOWLEDGE",
            "MOTIVATION_GOAL",
            "CAUSAL_AFTERSHOCK",
            "FORESHADOW_REPEAT",
            "VOICE_STYLE_PROTOCOL"
    );

    @Test
    void corpusShouldKeepTheFrozenTruthDistributionAndEvidence() throws Exception {
        JsonNode root = loadCorpus();
        JsonNode fixtures = root.path("fixtures");

        assertEquals(1, root.path("schemaVersion").asInt());
        assertTrue(fixtures.isArray());
        assertEquals(36, fixtures.size());

        Set<String> ids = new HashSet<>();
        Map<String, Integer> groups = new HashMap<>();
        Map<String, Integer> defectCounts = new HashMap<>();
        int clean = 0;
        int holdout = 0;

        for (JsonNode fixture : fixtures) {
            String id = requiredText(fixture, "id");
            String groupId = requiredText(fixture, "groupId");
            String defectType = requiredText(fixture, "defectType");
            String targetSceneId = requiredText(fixture, "targetSceneId");
            String targetText = requiredText(fixture, "text");

            assertTrue(ids.add(id), "duplicate fixture id: " + id);
            groups.merge(groupId, 1, Integer::sum);
            assertFalse(targetText.isBlank(), id);
            assertTrue(fixture.path("context").path("planning").isObject(), id);
            assertTrue(fixture.path("context").path("facts").isArray(), id);
            assertFalse(fixture.path("expectedContext").path("requiredTerms").isEmpty(), id);

            Map<String, String> sourceText = sourceText(fixture);
            for (JsonNode sourceId : fixture.path("sourceSceneIds")) {
                assertTrue(sourceText.containsKey(sourceId.asText()), id + " missing source scene " + sourceId.asText());
            }
            for (JsonNode requiredId : fixture.path("expectedContext").path("requiredSourceIds")) {
                assertTrue(sourceText.containsKey(requiredId.asText()), id + " requires unknown source " + requiredId.asText());
            }
            for (JsonNode excludedId : fixture.path("expectedContext").path("excludedSourceIds")) {
                assertFalse(sourceText.containsKey(excludedId.asText()), id + " excludes an available source");
            }

            if (fixture.path("holdout").asBoolean()) {
                holdout++;
                assertEquals("holdout", requiredText(fixture, "split"), id);
            }

            JsonNode evidence = fixture.path("evidence");
            if ("NONE".equals(defectType)) {
                clean++;
                assertTrue(evidence.isArray() && evidence.isEmpty(), id);
                continue;
            }

            assertTrue(DEFECT_TYPES.contains(defectType), id + " unknown defect type");
            defectCounts.merge(defectType, 1, Integer::sum);
            assertTrue(evidence.isArray() && evidence.size() >= 2, id + " requires an evidence pair");
            for (JsonNode item : evidence) {
                String sourceId = requiredText(item, "sourceId");
                String span = requiredText(item, "text");
                String haystack = targetSceneId.equals(sourceId) ? targetText : sourceText.get(sourceId);
                assertNotNull(haystack, id + " evidence references unknown source " + sourceId);
                assertTrue(haystack.contains(span), id + " evidence is not verbatim: " + span);
            }
        }

        assertEquals(12, clean);
        assertEquals(6, holdout);
        assertEquals(12, groups.size());
        assertTrue(groups.values().stream().allMatch(count -> count == 3));
        assertEquals(DEFECT_TYPES, defectCounts.keySet());
        assertTrue(defectCounts.values().stream().allMatch(count -> count == 4));
    }

    static JsonNode loadCorpus() throws Exception {
        try (InputStream stream = NovelQualityRegressionCorpusTest.class
                .getResourceAsStream("/quality/novel-quality-regression-fixtures.json")) {
            if (stream == null) {
                throw new IllegalStateException("Missing novel quality regression fixtures");
            }
            return OBJECT_MAPPER.readTree(stream);
        }
    }

    private static Map<String, String> sourceText(JsonNode fixture) {
        Map<String, String> result = new HashMap<>();
        for (JsonNode source : fixture.path("context").path("sourceScenes")) {
            result.put(requiredText(source, "id"), requiredText(source, "text"));
        }
        return result;
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        assertTrue(value.isTextual() && !value.asText().isBlank(), "missing " + field);
        return value.asText();
    }
}
