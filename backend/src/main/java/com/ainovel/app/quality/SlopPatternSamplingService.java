package com.ainovel.app.quality;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Samples versioned negative constraints for crafted generation mode. */
@Service
public class SlopPatternSamplingService {
    private static final List<Map.Entry<String, Integer>> CATEGORY_QUOTAS = List.of(
            Map.entry("PHRASE", 4),
            Map.entry("BODY_ACTION", 4),
            Map.entry("IMAGERY", 3),
            Map.entry("ENDING_CLICHE", 2),
            Map.entry("NARRATIVE_MECHANIC", 2)
    );

    private final SlopPatternRegistry registry;

    public SlopPatternSamplingService(SlopPatternRegistry registry) {
        this.registry = registry;
    }

    /** Returns up to 15 constraints; a scene UUID produces a stable sample. */
    public List<String> sample(UUID sceneId) {
        long seed = sceneId == null ? System.nanoTime() : sceneId.hashCode();
        List<SlopGenerationConstraint> all = registry.generationConstraints().stream()
                .sorted(Comparator.comparing(SlopGenerationConstraint::category)
                        .thenComparing(SlopGenerationConstraint::promptText)
                        .thenComparing(SlopGenerationConstraint::id))
                .toList();
        Map<String, List<SlopGenerationConstraint>> byCategory = new LinkedHashMap<>();
        for (SlopGenerationConstraint constraint : all) {
            byCategory.computeIfAbsent(constraint.category(), ignored -> new ArrayList<>()).add(constraint);
        }
        Random random = new Random(seed);
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, Integer> quota : CATEGORY_QUOTAS) {
            result.addAll(weightedSample(byCategory.getOrDefault(quota.getKey(), List.of()), quota.getValue(), random));
        }
        return List.copyOf(result);
    }

    private List<String> weightedSample(List<SlopGenerationConstraint> pool, int quota, Random random) {
        List<SlopGenerationConstraint> weighted = new ArrayList<>();
        for (SlopGenerationConstraint constraint : pool) {
            for (int index = 0; index < Math.max(1, constraint.weight()); index++) {
                weighted.add(constraint);
            }
        }
        Collections.shuffle(weighted, random);
        Set<String> seen = new LinkedHashSet<>();
        List<String> selected = new ArrayList<>();
        for (SlopGenerationConstraint constraint : weighted) {
            if (seen.add(constraint.id())) {
                selected.add(constraint.promptText());
                if (selected.size() >= quota) break;
            }
        }
        return selected;
    }
}
