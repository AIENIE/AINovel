package com.ainovel.app.quality;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SlopPatternSamplingServiceTest {

    @Test
    void samplesStableCategoryQuotasRegardlessOfRepositoryOrder() {
        SlopPatternRepository repository = mock(SlopPatternRepository.class);
        List<SlopPattern> patterns = patterns();
        List<SlopPattern> reversed = new ArrayList<>(patterns);
        Collections.reverse(reversed);
        when(repository.findByEnabledTrue()).thenReturn(reversed, patterns);

        SlopPatternSamplingService service = new SlopPatternSamplingService();
        ReflectionTestUtils.setField(service, "patternRepository", repository);
        UUID sceneId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        List<String> first = service.sample(sceneId);
        List<String> second = service.sample(sceneId);

        assertEquals(first, second);
        assertEquals(15, first.size());
        assertEquals(4, first.stream().filter(value -> value.startsWith("PHRASE-")).count());
        assertEquals(4, first.stream().filter(value -> value.startsWith("BODY_ACTION-")).count());
        assertEquals(3, first.stream().filter(value -> value.startsWith("IMAGERY-")).count());
        assertEquals(2, first.stream().filter(value -> value.startsWith("ENDING_CLICHE-")).count());
        assertEquals(2, first.stream().filter(value -> value.startsWith("NARRATIVE_MECHANIC-")).count());
    }

    private List<SlopPattern> patterns() {
        List<SlopPattern> patterns = new ArrayList<>();
        add(patterns, "PHRASE", 5);
        add(patterns, "BODY_ACTION", 5);
        add(patterns, "IMAGERY", 4);
        add(patterns, "ENDING_CLICHE", 3);
        add(patterns, "NARRATIVE_MECHANIC", 3);
        return patterns;
    }

    private void add(List<SlopPattern> patterns, String category, int count) {
        for (int index = 0; index < count; index++) {
            SlopPattern pattern = new SlopPattern();
            pattern.setId(UUID.nameUUIDFromBytes((category + index).getBytes(StandardCharsets.UTF_8)));
            pattern.setCategory(category);
            pattern.setPattern(category + "-" + index);
            pattern.setWeight(index % 3 + 1);
            pattern.setEnabled(true);
            patterns.add(pattern);
        }
    }
}
