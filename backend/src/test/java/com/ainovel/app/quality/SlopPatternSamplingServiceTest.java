package com.ainovel.app.quality;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlopPatternSamplingServiceTest {
    @Test
    void samplesStableCategoryQuotasFromVersionedRegistry() {
        SlopPatternRegistry registry = new SlopPatternRegistry();
        SlopPatternSamplingService service = new SlopPatternSamplingService(registry);
        UUID sceneId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        List<String> first = service.sample(sceneId);
        List<String> second = service.sample(sceneId);

        assertEquals(first, second);
        assertEquals(15, first.size());
        Map<String, String> categories = new HashMap<>();
        for (SlopGenerationConstraint constraint : registry.generationConstraints()) {
            categories.put(constraint.promptText(), constraint.category());
        }
        assertEquals(4, first.stream().filter(value -> "PHRASE".equals(categories.get(value))).count());
        assertEquals(4, first.stream().filter(value -> "BODY_ACTION".equals(categories.get(value))).count());
        assertEquals(3, first.stream().filter(value -> "IMAGERY".equals(categories.get(value))).count());
        assertEquals(2, first.stream().filter(value -> "ENDING_CLICHE".equals(categories.get(value))).count());
        assertEquals(2, first.stream().filter(value -> "NARRATIVE_MECHANIC".equals(categories.get(value))).count());
    }

    @Test
    void preservesAllHistoricalGenerationConstraints() {
        assertEquals(38, new SlopPatternRegistry().generationConstraints().size());
    }
}
