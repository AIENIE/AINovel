package com.ainovel.app.quality;

import java.util.List;

public record SlopPatternRule(
        String id,
        String category,
        SlopPatternStatus status,
        List<String> literals,
        List<String> regexes,
        boolean ignoreCase,
        String detector,
        SlopDimension dimension,
        String module,
        List<String> contextDowngradeHints,
        String whyItMatters,
        String repairHint,
        List<String> alternativeExplanations,
        Generation generation,
        List<String> observedModels,
        List<String> sourceRefs,
        List<String> positiveSamples,
        List<ContextSample> contextualSamples
) {
    public SlopPatternRule {
        literals = copy(literals);
        regexes = copy(regexes);
        contextDowngradeHints = copy(contextDowngradeHints);
        alternativeExplanations = copy(alternativeExplanations);
        observedModels = copy(observedModels);
        sourceRefs = copy(sourceRefs);
        positiveSamples = copy(positiveSamples);
        contextualSamples = contextualSamples == null ? List.of() : List.copyOf(contextualSamples);
    }

    public boolean hasMatcher() {
        return !literals.isEmpty() || !regexes.isEmpty() || (detector != null && !detector.isBlank());
    }

    private static List<String> copy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    public record Generation(boolean enabled, String category, String promptText, int weight) {
    }

    public record ContextSample(String text, String contextHint) {
    }
}
