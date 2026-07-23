package com.ainovel.app.quality;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SlopIssueAggregator {
    private final SlopPatternRegistry registry;
    private final SlopHeuristicPolicy policy;

    SlopIssueAggregator(SlopPatternRegistry registry, SlopHeuristicPolicy policy) {
        this.registry = registry;
        this.policy = policy;
    }

    List<SlopIssueDraft> aggregate(SlopHeuristicInput input, List<SlopPatternHit> hits) {
        List<SlopIssueDraft> issues = new ArrayList<>();
        List<SlopPatternHit> proseHits = new ArrayList<>();
        Map<String, SlopPatternHit> artifacts = new LinkedHashMap<>();
        for (SlopPatternHit hit : hits) {
            if ("ARTIFACT".equals(hit.rule().category())) {
                artifacts.putIfAbsent(hit.rule().id(), hit);
            } else {
                proseHits.add(hit);
            }
        }
        for (SlopPatternHit hit : artifacts.values()) {
            issues.add(artifactIssue(input, hit));
        }
        issues.addAll(proseIssues(input, proseHits));
        return issues.stream()
                .sorted(issueOrder())
                .limit(registry.issueCap())
                .toList();
    }

    private List<SlopIssueDraft> proseIssues(SlopHeuristicInput input, List<SlopPatternHit> hits) {
        Map<String, List<SlopPatternHit>> byCategory = new LinkedHashMap<>();
        for (SlopPatternHit hit : hits) {
            byCategory.computeIfAbsent(hit.rule().category(), ignored -> new ArrayList<>()).add(hit);
        }
        Map<String, Integer> maxDiversity = categoryDiversity(hits);
        List<SlopIssueDraft> issues = new ArrayList<>();
        for (Map.Entry<String, List<SlopPatternHit>> entry : byCategory.entrySet()) {
            List<SlopPatternHit> categoryHits = entry.getValue();
            WindowDensity density = highestDensity(categoryHits);
            SlopPatternHit evidence = density.firstHit();
            boolean downgraded = categoryHits.stream().allMatch(hit -> contextDowngraded(input, hit.rule()));
            int diversity = maxDiversity.getOrDefault(entry.getKey(), 1);
            Score score = score(density.count(), diversity, downgraded);
            SlopPatternRule rule = evidence.rule();
            String why = score.evidenceLevel().equals("E2")
                    ? "500 字窗口内同类表达或多类模板信号集中出现，容易让场景由套话推动。"
                    : rule.whyItMatters();
            issues.add(new SlopIssueDraft(
                    rule.dimension(), score.severity(), score.risk(), evidence.evidence(), why, rule.repairHint(),
                    evidence.start(), evidence.end(), evidence.evidence(), rule.module(), rule.id(),
                    entry.getKey().toLowerCase(), score.evidenceLevel(), alternatives(rule), rule.repairHint()
            ));
        }
        return issues;
    }

    private SlopIssueDraft artifactIssue(SlopHeuristicInput input, SlopPatternHit hit) {
        SlopPatternRule rule = hit.rule();
        boolean downgraded = contextDowngraded(input, rule);
        return new SlopIssueDraft(
                SlopDimension.ARTIFACT,
                downgraded ? SlopSeverity.LOW : SlopSeverity.HIGH,
                downgraded ? policy.expectedStyleWeakSignalRisk() : 88,
                hit.evidence(),
                downgraded ? "当前语境声明了结构化文本或日志文体，该标记只作为弱信号。" : rule.whyItMatters(),
                rule.repairHint(),
                hit.start(), hit.end(), hit.evidence(), rule.module(), rule.id(),
                "model_output_residue", downgraded ? "E1" : "E4", alternatives(rule), rule.repairHint()
        );
    }

    private Score score(int sameCategoryCount, int distinctCategories, boolean downgraded) {
        if (downgraded) {
            return new Score(policy.expectedStyleWeakSignalRisk(), SlopSeverity.LOW, "E1");
        }
        if (sameCategoryCount >= registry.sameFamilyHighCount()
                || distinctCategories >= registry.categoryHighCount()) {
            return new Score(policy.highDensityRisk(), SlopSeverity.HIGH, "E2");
        }
        if (sameCategoryCount >= registry.sameFamilyMediumCount()
                || distinctCategories >= registry.categoryMediumCount()) {
            return new Score(policy.mediumDensityRisk(), SlopSeverity.MEDIUM, "E2");
        }
        return new Score(policy.singleWeakSignalRisk(), SlopSeverity.LOW, "E1");
    }

    private WindowDensity highestDensity(List<SlopPatternHit> hits) {
        int right = 0;
        int bestCount = 0;
        SlopPatternHit best = hits.getFirst();
        for (int left = 0; left < hits.size(); left++) {
            if (right < left) right = left;
            int limit = hits.get(left).start() + registry.windowChars();
            while (right < hits.size() && hits.get(right).start() < limit) right++;
            int count = right - left;
            if (count > bestCount) {
                bestCount = count;
                best = hits.get(left);
            }
        }
        return new WindowDensity(bestCount, best);
    }

    private Map<String, Integer> categoryDiversity(List<SlopPatternHit> hits) {
        Map<String, Integer> maximum = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        int right = 0;
        for (int left = 0; left < hits.size(); left++) {
            int limit = hits.get(left).start() + registry.windowChars();
            while (right < hits.size() && hits.get(right).start() < limit) {
                counts.merge(hits.get(right).rule().category(), 1, Integer::sum);
                right++;
            }
            int diversity = counts.size();
            for (String category : counts.keySet()) {
                maximum.merge(category, diversity, Math::max);
            }
            String category = hits.get(left).rule().category();
            counts.computeIfPresent(category, (ignored, count) -> count == 1 ? null : count - 1);
        }
        return maximum;
    }

    private boolean contextDowngraded(SlopHeuristicInput input, SlopPatternRule rule) {
        for (String hint : rule.contextDowngradeHints()) {
            if (input.hasContextHint(hint)) return true;
        }
        return false;
    }

    private String alternatives(SlopPatternRule rule) {
        Set<String> values = new LinkedHashSet<>(rule.alternativeExplanations());
        if (values.isEmpty()) values.add("作者个人文风");
        return values.stream()
                .map(value -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .reduce("[", (left, value) -> left.equals("[") ? left + value : left + "," + value) + "]";
    }

    private Comparator<SlopIssueDraft> issueOrder() {
        return Comparator.comparingInt(SlopIssueDraft::riskScore).reversed()
                .thenComparing(issue -> issue.charStart() == null ? Integer.MAX_VALUE : issue.charStart());
    }

    private record WindowDensity(int count, SlopPatternHit firstHit) {}
    private record Score(int risk, SlopSeverity severity, String evidenceLevel) {}
}
