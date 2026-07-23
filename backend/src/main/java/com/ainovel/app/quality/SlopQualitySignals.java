package com.ainovel.app.quality;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record SlopQualitySignals(
        String riskLabel,
        String evidenceLevel,
        String safeClaim,
        Map<String, Object> moduleScores,
        List<String> alternativeExplanations,
        List<Object> revisionPriorities,
        List<Object> rewriteTasks
) {
    public SlopQualitySignals {
        riskLabel = blankTo(riskLabel, "low");
        evidenceLevel = blankTo(evidenceLevel, "E1");
        safeClaim = blankTo(safeClaim, "该文本呈现%s级模板化/slop风险；这不能证明作者使用AI。".formatted(riskLabel));
        moduleScores = moduleScores == null ? Map.of() : Map.copyOf(moduleScores);
        alternativeExplanations = alternativeExplanations == null ? defaultAlternatives() : List.copyOf(alternativeExplanations);
        revisionPriorities = revisionPriorities == null ? List.of() : List.copyOf(revisionPriorities);
        rewriteTasks = rewriteTasks == null ? List.of() : List.copyOf(rewriteTasks);
    }

    public static SlopQualitySignals empty() {
        return fromIssues(0, SlopSeverity.LOW, List.of());
    }

    public static SlopQualitySignals fromIssues(int riskScore, SlopSeverity maxSeverity, List<SlopIssueDraft> issues) {
        List<SlopIssueDraft> safeIssues = issues == null ? List.of() : issues;
        String riskLabel = label(riskScore);
        String evidenceLevel = evidenceLevel(riskScore, maxSeverity, safeIssues);
        return new SlopQualitySignals(
                riskLabel,
                evidenceLevel,
                "该文本呈现%s级模板化/slop风险；这不能证明作者使用AI。".formatted(riskLabel),
                moduleScores(safeIssues),
                defaultAlternatives(),
                revisionPriorities(safeIssues),
                rewriteTasks(safeIssues)
        );
    }

    public SlopQualitySignals withDefaults(int riskScore, SlopSeverity maxSeverity, List<SlopIssueDraft> issues) {
        SlopQualitySignals fallback = fromIssues(riskScore, maxSeverity, issues);
        return new SlopQualitySignals(
                blankTo(riskLabel, fallback.riskLabel()),
                blankTo(evidenceLevel, fallback.evidenceLevel()),
                blankTo(safeClaim, fallback.safeClaim()),
                moduleScores.isEmpty() ? fallback.moduleScores() : moduleScores,
                alternativeExplanations.isEmpty() ? fallback.alternativeExplanations() : alternativeExplanations,
                revisionPriorities.isEmpty() ? fallback.revisionPriorities() : revisionPriorities,
                rewriteTasks.isEmpty() ? fallback.rewriteTasks() : rewriteTasks
        );
    }

    public SlopQualitySignals withShadowHits(List<SlopShadowHit> shadowHits) {
        List<SlopShadowHit> safeHits = shadowHits == null ? List.of() : shadowHits;
        Map<String, Object> mergedScores = new LinkedHashMap<>(moduleScores);
        List<Object> serialized = new ArrayList<>();
        for (SlopShadowHit hit : safeHits) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("pattern_id", hit.patternId());
            value.put("category", hit.category());
            value.put("occurrence_count", hit.occurrenceCount());
            value.put("char_start", hit.charStart());
            value.put("char_end", hit.charEnd());
            value.put("evidence", hit.evidence());
            serialized.add(value);
        }
        mergedScores.put("_shadow_pattern_hits", Map.of(
                "schema_version", 1,
                "count", serialized.size(),
                "hits", serialized
        ));
        return new SlopQualitySignals(
                riskLabel, evidenceLevel, safeClaim, mergedScores,
                alternativeExplanations, revisionPriorities, rewriteTasks
        );
    }

    private static Map<String, Object> moduleScores(List<SlopIssueDraft> issues) {
        Map<String, ModuleScore> scores = new LinkedHashMap<>();
        for (SlopIssueDraft issue : issues) {
            String module = blankTo(issue.module(), "surface_template");
            ModuleScore current = scores.getOrDefault(module, new ModuleScore(0, 0));
            scores.put(module, new ModuleScore(Math.max(current.score(), issue.riskScore()), current.evidenceCount() + 1));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, ModuleScore> entry : scores.entrySet()) {
            result.put(entry.getKey(), Map.of(
                    "score", entry.getValue().score(),
                    "evidence_count", entry.getValue().evidenceCount()
            ));
        }
        return result;
    }

    private static List<Object> revisionPriorities(List<SlopIssueDraft> issues) {
        List<Object> priorities = new ArrayList<>();
        for (SlopIssueDraft issue : issues) {
            if (issue.severity() == SlopSeverity.HIGH || issue.severity() == SlopSeverity.BLOCKING) {
                priorities.add(blankTo(issue.repairHint(), blankTo(issue.minimalFix(), "优先处理高风险文本证据")));
            }
        }
        if (priorities.isEmpty() && !issues.isEmpty()) {
            SlopIssueDraft first = issues.get(0);
            priorities.add(blankTo(first.repairHint(), blankTo(first.minimalFix(), "优先处理文本风险证据")));
        }
        return priorities;
    }

    private static List<Object> rewriteTasks(List<SlopIssueDraft> issues) {
        List<Object> tasks = new ArrayList<>();
        int index = 1;
        for (SlopIssueDraft issue : issues) {
            Map<String, Object> task = new LinkedHashMap<>();
            task.put("task_id", "R" + index);
            task.put("priority", index);
            Map<String, Object> targetSpan = new LinkedHashMap<>();
            targetSpan.put("char_start", issue.charStart());
            targetSpan.put("char_end", issue.charEnd());
            targetSpan.put("quote", blankTo(issue.quote(), issue.evidence()));
            task.put("target_span", targetSpan);
            task.put("problem", blankTo(issue.whyItMatters(), issue.evidence()));
            task.put("repair_goal", blankTo(issue.repairHint(), issue.minimalFix()));
            task.put("constraints", List.of("不改变剧情事件", "不改变角色决策", "只做局部保守修订"));
            task.put("suggested_method", blankTo(issue.issueType(), issue.dimension().name().toLowerCase()));
            tasks.add(task);
            index++;
        }
        return tasks;
    }

    private static String evidenceLevel(int riskScore, SlopSeverity maxSeverity, List<SlopIssueDraft> issues) {
        int rank = 1;
        for (SlopIssueDraft issue : issues) {
            rank = Math.max(rank, evidenceRank(issue.evidenceLevel()));
        }
        if (rank >= 4) {
            return "E4";
        }
        if (rank == 3) {
            return "E3";
        }
        if (rank == 2 || riskScore >= 70 || maxSeverity == SlopSeverity.HIGH || maxSeverity == SlopSeverity.BLOCKING) {
            return "E2";
        }
        return "E1";
    }

    private static int evidenceRank(String value) {
        return switch (blankTo(value, "E1").toUpperCase()) {
            case "E4" -> 4;
            case "E3" -> 3;
            case "E2" -> 2;
            default -> 1;
        };
    }

    private static String label(int riskScore) {
        if (riskScore >= 85) {
            return "critical";
        }
        if (riskScore >= 70) {
            return "high";
        }
        if (riskScore >= 40) {
            return "medium";
        }
        return "low";
    }

    private static List<String> defaultAlternatives() {
        return List.of("传统网文俗套", "人工低水平写作", "工作室公式化", "题材/平台惯例", "作者个人文风");
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record ModuleScore(int score, int evidenceCount) {
    }
}
