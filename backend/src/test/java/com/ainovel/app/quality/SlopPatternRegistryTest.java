package com.ainovel.app.quality;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlopPatternRegistryTest {
    @Test
    void loadsSplitCatalogFromPackagedClasspath() {
        SlopPatternRegistry registry = new SlopPatternRegistry();

        assertTrue(registry.activeRules().size() >= 45);
        assertEquals(5, registry.shadowRules().size());
        assertEquals(38, registry.generationConstraints().size());
        assertNotNull(getClass().getClassLoader().getResource(SlopPatternRegistry.INDEX_RESOURCE));
    }

    @Test
    void failsFastOnDuplicateIds() {
        String rule = activeRule("DUPLICATE_ID", "示例");
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> registry("%s,%s".formatted(rule, rule), thresholds(3, 5, 3, 4)));
        assertTrue(error.getMessage().contains("duplicate rule id"));
    }

    @Test
    void failsFastOnUnboundedRegex() {
        String rule = activeRule("BAD_REGEX_ID", "")
                .replace("\"literals\":[\"\"]", "\"literals\":[]")
                .replace("\"regexes\":[]", "\"regexes\":[\".*而是\"]");
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> registry(rule, thresholds(3, 5, 3, 4)));
        assertTrue(error.getMessage().contains("explicitly bounded"));
    }

    @Test
    void failsFastOnNonMonotonicThresholds() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> registry(activeRule("VALID_RULE_ID", "示例"), thresholds(5, 3, 3, 4)));
        assertTrue(error.getMessage().contains("monotonic"));
    }

    private SlopPatternRegistry registry(String rules, String thresholdFields) {
        String index = """
                {"schemaVersion":1,%s,"resources":["rules.json"]}
                """.formatted(thresholdFields);
        String file = """
                {"schemaVersion":1,"rules":[%s]}
                """.formatted(rules);
        ClassLoader loader = new MapClassLoader(Map.of(
                SlopPatternRegistry.INDEX_RESOURCE, index,
                "rules.json", file
        ));
        return new SlopPatternRegistry(loader, new ObjectMapper());
    }

    private String thresholds(int medium, int high, int categoryMedium, int categoryHigh) {
        return ("\"windowChars\":500,\"sameFamilyMediumCount\":%d,\"sameFamilyHighCount\":%d," +
                "\"categoryMediumCount\":%d,\"categoryHighCount\":%d,\"issueCap\":12")
                .formatted(medium, high, categoryMedium, categoryHigh);
    }

    private String activeRule(String id, String literal) {
        return """
                {"id":"%s","category":"BODY_ACTION","status":"ACTIVE","literals":["%s"],"regexes":[],
                 "ignoreCase":false,"dimension":"GENERICITY","module":"surface_template",
                 "contextDowngradeHints":["测试体"],"whyItMatters":"说明","repairHint":"修复",
                 "alternativeExplanations":[],"observedModels":[],"sourceRefs":[],
                 "positiveSamples":["%s"],"contextualSamples":[{"text":"%s","contextHint":"测试体"}]}
                """.formatted(id, literal, literal, literal);
    }

    private static final class MapClassLoader extends ClassLoader {
        private final Map<String, String> resources;

        private MapClassLoader(Map<String, String> resources) {
            super(null);
            this.resources = resources;
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            String value = resources.get(name);
            return value == null ? null : new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
