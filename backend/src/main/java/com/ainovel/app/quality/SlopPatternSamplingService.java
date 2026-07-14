package com.ainovel.app.quality;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Samples negative-constraint patterns from the slop_patterns table for crafted generation mode.
 * Each category has a quota; the sample is seeded by sceneId so the same scene always gets
 * the same constraints (deterministic re-drafts), while different scenes rotate patterns.
 */
@Service
public class SlopPatternSamplingService {

    private static final int PHRASE_QUOTA          = 4;
    private static final int BODY_ACTION_QUOTA     = 4;
    private static final int IMAGERY_QUOTA         = 3;
    private static final int ENDING_CLICHE_QUOTA   = 2;
    private static final int NARRATIVE_MECHANIC_QUOTA = 2;

    private static final List<Map.Entry<String, Integer>> CATEGORY_QUOTAS = List.of(
            Map.entry("PHRASE", PHRASE_QUOTA),
            Map.entry("BODY_ACTION", BODY_ACTION_QUOTA),
            Map.entry("IMAGERY", IMAGERY_QUOTA),
            Map.entry("ENDING_CLICHE", ENDING_CLICHE_QUOTA),
            Map.entry("NARRATIVE_MECHANIC", NARRATIVE_MECHANIC_QUOTA)
    );

    @Autowired
    private SlopPatternRepository patternRepository;

    /**
     * Returns up to 15 pattern strings sampled by category quota.
     * The seed and stable input order keep the sample deterministic per scene.
     */
    public List<String> sample(UUID sceneId) {
        long seed = sceneId == null ? System.currentTimeMillis() : sceneId.hashCode();
        List<SlopPattern> all = patternRepository.findByEnabledTrue();
        if (all.isEmpty()) {
            return List.of();
        }
        List<SlopPattern> ordered = all.stream()
                .sorted(Comparator.comparing(SlopPattern::getCategory)
                        .thenComparing(SlopPattern::getPattern)
                        .thenComparing(SlopPattern::getId))
                .toList();
        Map<String, List<SlopPattern>> byCategory = groupByCategory(ordered);
        List<String> result = new ArrayList<>();
        Random rng = new Random(seed);
        for (Map.Entry<String, Integer> entry : CATEGORY_QUOTAS) {
            List<SlopPattern> pool = byCategory.getOrDefault(entry.getKey(), List.of());
            result.addAll(weightedSample(pool, entry.getValue(), rng));
        }
        return Collections.unmodifiableList(result);
    }

    private Map<String, List<SlopPattern>> groupByCategory(List<SlopPattern> patterns) {
        Map<String, List<SlopPattern>> map = new LinkedHashMap<>();
        for (SlopPattern p : patterns) {
            map.computeIfAbsent(p.getCategory(), k -> new ArrayList<>()).add(p);
        }
        return map;
    }

    /** Weighted sampling without replacement using a simple weight-based shuffle. */
    private List<String> weightedSample(List<SlopPattern> pool, int quota, Random rng) {
        if (pool.isEmpty()) return List.of();
        // Build weighted list: repeat each entry by its weight.
        List<SlopPattern> weighted = new ArrayList<>();
        for (SlopPattern p : pool) {
            int w = Math.max(1, p.getWeight());
            for (int i = 0; i < w; i++) {
                weighted.add(p);
            }
        }
        Collections.shuffle(weighted, rng);
        Set<UUID> seen = new LinkedHashSet<>();
        List<String> selected = new ArrayList<>();
        for (SlopPattern p : weighted) {
            if (seen.add(p.getId())) {
                selected.add(p.getPattern());
                if (selected.size() >= quota) break;
            }
        }
        return selected;
    }
}
