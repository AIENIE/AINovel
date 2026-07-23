package com.ainovel.app.quality;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class LocalSlopHeuristics {
    private final SlopHeuristicPolicy policy;
    private final SlopPatternRegistry registry;
    private final SlopPatternMatcher matcher;
    private final SlopIssueAggregator aggregator;
    private final SlopRepetitionDetector repetitionDetector;
    private final SlopShadowAnalyzer shadowAnalyzer;

    public LocalSlopHeuristics() {
        this(new SlopPatternRegistry(), SlopHeuristicPolicy.defaultPolicy());
    }

    @Autowired
    public LocalSlopHeuristics(SlopPatternRegistry registry) {
        this(registry, SlopHeuristicPolicy.defaultPolicy());
    }

    LocalSlopHeuristics(SlopHeuristicPolicy policy) {
        this(new SlopPatternRegistry(), policy);
    }

    LocalSlopHeuristics(SlopPatternRegistry registry, SlopHeuristicPolicy policy) {
        this.registry = registry;
        this.policy = policy == null ? SlopHeuristicPolicy.defaultPolicy() : policy;
        this.matcher = new SlopPatternMatcher(registry);
        this.aggregator = new SlopIssueAggregator(registry, this.policy);
        this.repetitionDetector = new SlopRepetitionDetector();
        this.shadowAnalyzer = new SlopShadowAnalyzer(matcher);
    }

    public SlopHeuristicResult evaluate(String text) {
        return evaluate(SlopHeuristicInput.textOnly(text));
    }

    public SlopHeuristicResult evaluate(SlopHeuristicInput input) {
        SlopHeuristicInput safeInput = input == null ? SlopHeuristicInput.textOnly("") : input;
        List<SlopPatternHit> activeHits = matcher.match(safeInput.text(), registry.activeRules());
        List<SlopIssueDraft> issues = new ArrayList<>(aggregator.aggregate(safeInput, activeHits));
        SlopIssueDraft repetition = repetitionDetector.detect(safeInput.text());
        if (repetition != null) issues.add(repetition);
        issues = issues.stream()
                .sorted(Comparator.comparingInt(SlopIssueDraft::riskScore).reversed()
                        .thenComparing(issue -> issue.charStart() == null ? Integer.MAX_VALUE : issue.charStart()))
                .limit(registry.issueCap())
                .toList();

        int risk = issues.stream().mapToInt(SlopIssueDraft::riskScore).max().orElse(0);
        SlopSeverity severity = issues.stream()
                .map(SlopIssueDraft::severity)
                .max(Comparator.comparingInt(this::rank))
                .orElse(SlopSeverity.LOW);
        boolean requiresAiReview = risk >= policy.aiReviewRiskThreshold()
                || severity == SlopSeverity.HIGH
                || severity == SlopSeverity.BLOCKING;
        List<SlopShadowHit> shadowHits = shadowAnalyzer.analyze(safeInput.text(), registry.shadowRules());
        return new SlopHeuristicResult(risk, severity, requiresAiReview, issues, shadowHits);
    }

    private int rank(SlopSeverity severity) {
        return switch (severity) {
            case LOW -> 1;
            case MEDIUM -> 2;
            case HIGH -> 3;
            case BLOCKING -> 4;
        };
    }
}
