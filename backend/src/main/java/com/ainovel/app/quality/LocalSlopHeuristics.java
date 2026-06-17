package com.ainovel.app.quality;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class LocalSlopHeuristics {
    private static final List<String> GENERIC_PATTERNS = List.of(
            "嘴角微微上扬",
            "空气仿佛凝固",
            "时间像是停了下来",
            "眼神变得坚定",
            "心中涌起一股",
            "说不出的感觉",
            "命运的齿轮",
            "一切都在此刻改变",
            "仿佛整个世界"
    );
    private static final List<String> ARTIFACT_PATTERNS = List.of(
            "```",
            "作为一个ai",
            "以下是",
            "markdown",
            "# ",
            "【ai"
    );

    public SlopHeuristicResult evaluate(String text) {
        String normalized = text == null ? "" : text.trim();
        List<SlopIssueDraft> issues = new ArrayList<>();
        issues.addAll(detectRepetition(normalized));
        issues.addAll(detectGenericity(normalized));
        issues.addAll(detectArtifacts(normalized));
        issues.addAll(detectLocalCoherence(normalized));

        int risk = issues.stream().mapToInt(SlopIssueDraft::riskScore).max().orElse(0);
        SlopSeverity severity = issues.stream()
                .map(SlopIssueDraft::severity)
                .max((a, b) -> Integer.compare(rank(a), rank(b)))
                .orElse(SlopSeverity.LOW);
        boolean requiresAiReview = risk >= 55 || severity == SlopSeverity.HIGH || severity == SlopSeverity.BLOCKING;
        return new SlopHeuristicResult(risk, severity, requiresAiReview, List.copyOf(issues));
    }

    private List<SlopIssueDraft> detectRepetition(String text) {
        List<SlopIssueDraft> issues = new ArrayList<>();
        String compact = text.replaceAll("\\s+", "");
        Map<String, Integer> counts = new HashMap<>();
        int window = 10;
        for (int i = 0; i + window <= compact.length(); i += Math.max(1, window / 2)) {
            String gram = compact.substring(i, i + window);
            counts.merge(gram, 1, Integer::sum);
        }
        int maxCount = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        if (maxCount >= 2 && compact.length() > 30) {
            String snippet = repeatedSnippet(counts);
            issues.add(new SlopIssueDraft(
                    SlopDimension.REPETITION,
                    maxCount >= 3 ? SlopSeverity.HIGH : SlopSeverity.MEDIUM,
                    maxCount >= 3 ? 82 : 68,
                    snippet,
                    "连续复用相同表达会让场景显得原地打转。",
                    "保留一次核心信息，其余改为动作、结果或删减。",
                    Math.max(0, compact.indexOf(snippet)),
                    compact.indexOf(snippet) >= 0 ? compact.indexOf(snippet) + snippet.length() : null,
                    snippet,
                    "surface_template",
                    "SURFACE_REPETITION_NGRAM",
                    "repetition",
                    maxCount >= 3 ? "E2" : "E1",
                    "[\"作者刻意回环\",\"类型文强调句\",\"人工水字数\"]",
                    "保留一次核心信息，其余改为动作、结果或删减。"
            ));
        }
        if (text.contains("这是一个重要的时刻，这是一个重要的时刻")) {
            String evidence = "这是一个重要的时刻";
            int start = text.indexOf(evidence);
            issues.add(new SlopIssueDraft(
                    SlopDimension.REPETITION,
                    SlopSeverity.HIGH,
                    84,
                    evidence,
                    "重复抽象判断会形成明显水字数。",
                    "删除重复句，改写为具体可见的变化。",
                    start,
                    start >= 0 ? start + evidence.length() : null,
                    evidence,
                    "surface_template",
                    "SURFACE_REPETITION_ABSTRACT",
                    "repetition",
                    "E2",
                    "[\"人工水字数\",\"强调式修辞\"]",
                    "删除重复句，改写为具体可见的变化。"
            ));
        }
        return issues;
    }

    private List<SlopIssueDraft> detectGenericity(String text) {
        List<SlopIssueDraft> issues = new ArrayList<>();
        for (String pattern : GENERIC_PATTERNS) {
            int start = text.indexOf(pattern);
            if (start >= 0) {
                issues.add(new SlopIssueDraft(
                        SlopDimension.GENERICITY,
                        SlopSeverity.HIGH,
                        78,
                        pattern,
                        "高频模板表达会削弱场景的具体性。",
                        "替换为当前场景独有的动作、物件、声音或后果。",
                        start,
                        start + pattern.length(),
                        pattern,
                        "surface_template",
                        "SURFACE_GENERIC_PHRASE",
                        "phrase_pattern",
                        "E1",
                        "[\"传统网文俗套\",\"作者个人文风\",\"平台公式化表达\"]",
                        "替换为当前场景独有的动作、物件、声音或后果。"
                ));
                break;
            }
        }
        return issues;
    }

    private List<SlopIssueDraft> detectArtifacts(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String pattern : ARTIFACT_PATTERNS) {
            int start = lower.indexOf(pattern);
            if (start >= 0) {
                return List.of(new SlopIssueDraft(
                        SlopDimension.ARTIFACT,
                        SlopSeverity.HIGH,
                        88,
                        pattern,
                        "模型输出残留会直接破坏正文沉浸感。",
                        "删除 markdown、解释语和 AI 标记，只保留小说正文。",
                        start,
                        start + pattern.length(),
                        text.substring(start, Math.min(text.length(), start + pattern.length())),
                        "consistency_assimilation",
                        "ARTIFACT_META_LEAK",
                        "meta_leak",
                        "E4",
                        "[]",
                        "删除 markdown、解释语和 AI 标记，只保留小说正文。"
                ));
            }
        }
        return List.of();
    }

    private List<SlopIssueDraft> detectLocalCoherence(String text) {
        if (text.length() < 80) {
            return List.of();
        }
        int abstractMarkers = 0;
        for (String marker : List.of("突然", "莫名", "不知为何", "一瞬间", "仿佛")) {
            if (text.contains(marker)) {
                abstractMarkers++;
            }
        }
        if (abstractMarkers >= 3) {
            String evidence = "突然/莫名/不知为何";
            return List.of(new SlopIssueDraft(
                    SlopDimension.LOCAL_COHERENCE,
                    SlopSeverity.MEDIUM,
                    58,
                    evidence,
                    "缺少桥接的转折容易造成动机跳变。",
                    "补一句可见原因或角色判断，再进入转折。",
                    null,
                    null,
                    evidence,
                    "breath_focus_pacing",
                    "BREATH_ABSTRACT_TURN",
                    "motivation_jump",
                    "E2",
                    "[\"快节奏类型文\",\"作者刻意制造悬念\"]",
                    "补一句可见原因或角色判断，再进入转折。"
            ));
        }
        return List.of();
    }

    private String repeatedSnippet(Map<String, Integer> counts) {
        return counts.entrySet().stream()
                .filter(entry -> entry.getValue() >= 2)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("重复片段");
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
