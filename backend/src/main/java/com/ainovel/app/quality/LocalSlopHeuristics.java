package com.ainovel.app.quality;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class LocalSlopHeuristics {
    private final SlopHeuristicPolicy policy;

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

    public LocalSlopHeuristics() {
        this(SlopHeuristicPolicy.defaultPolicy());
    }

    LocalSlopHeuristics(SlopHeuristicPolicy policy) {
        this.policy = policy == null ? SlopHeuristicPolicy.defaultPolicy() : policy;
    }

    public SlopHeuristicResult evaluate(String text) {
        return evaluate(SlopHeuristicInput.textOnly(text));
    }

    public SlopHeuristicResult evaluate(SlopHeuristicInput input) {
        SlopHeuristicInput safeInput = input == null ? SlopHeuristicInput.textOnly("") : input;
        String normalized = safeInput.text();
        List<SlopIssueDraft> issues = new ArrayList<>();
        issues.addAll(detectRepetition(normalized));
        issues.addAll(detectGenericity(safeInput));
        issues.addAll(detectArtifacts(safeInput));
        issues.addAll(detectLocalCoherence(normalized));

        int risk = issues.stream().mapToInt(SlopIssueDraft::riskScore).max().orElse(0);
        SlopSeverity severity = issues.stream()
                .map(SlopIssueDraft::severity)
                .max((a, b) -> Integer.compare(rank(a), rank(b)))
                .orElse(SlopSeverity.LOW);
        boolean requiresAiReview = risk >= policy.aiReviewRiskThreshold()
                || severity == SlopSeverity.HIGH
                || severity == SlopSeverity.BLOCKING;
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

    private List<SlopIssueDraft> detectGenericity(SlopHeuristicInput input) {
        String text = input.text();
        List<SlopIssueDraft> issues = new ArrayList<>();
        List<PatternHit> hits = new ArrayList<>();
        for (String pattern : GENERIC_PATTERNS) {
            int start = text.indexOf(pattern);
            if (start >= 0) {
                hits.add(new PatternHit(pattern, start));
            }
        }
        if (hits.isEmpty()) {
            return issues;
        }

        int count = hits.size();
        PatternHit first = hits.get(0);
        String evidenceLevel = count >= policy.genericPhraseMediumDensityCount() ? "E2" : "E1";
        SlopSeverity severity = count >= policy.genericPhraseHighDensityCount()
                ? SlopSeverity.HIGH
                : count >= policy.genericPhraseMediumDensityCount() ? SlopSeverity.MEDIUM : SlopSeverity.LOW;
        int risk = genericRisk(input, count);
        String why = count >= policy.genericPhraseMediumDensityCount()
                ? "短窗口内多个模板表达共现，会让场景显得由套话推动。"
                : "单个高频表达只能作为弱信号，需结合密度和语境判断。";
        String fix = count >= policy.genericPhraseMediumDensityCount()
                ? "优先保留承担剧情功能的句子，其余改成当前场景独有的动作、物件、声音或后果。"
                : "仅在同窗口密度升高时优先修改；单点可按作者风格保留或局部具体化。";
        issues.add(new SlopIssueDraft(
                SlopDimension.GENERICITY,
                severity,
                risk,
                first.pattern(),
                why,
                fix,
                first.start(),
                first.start() + first.pattern().length(),
                first.pattern(),
                "surface_template",
                "SURFACE_GENERIC_PHRASE",
                "phrase_pattern",
                evidenceLevel,
                "[\"传统网文俗套\",\"作者个人文风\",\"平台公式化表达\"]",
                fix
        ));
        return issues;
    }

    private int genericRisk(SlopHeuristicInput input, int count) {
        if (count >= policy.genericPhraseHighDensityCount()) {
            return policy.highDensityRisk();
        }
        if (count >= policy.genericPhraseMediumDensityCount()) {
            return policy.mediumDensityRisk();
        }
        return hasExpectedStyleContext(input) ? policy.expectedStyleWeakSignalRisk() : policy.singleWeakSignalRisk();
    }

    private boolean hasExpectedStyleContext(SlopHeuristicInput input) {
        return input.hasContextHint("传统网文")
                || input.hasContextHint("平台爽文")
                || input.hasContextHint("快节奏")
                || input.hasContextHint("分析腔")
                || input.hasContextHint("排除法");
    }

    private List<SlopIssueDraft> detectArtifacts(SlopHeuristicInput input) {
        String text = input.text();
        String lower = text.toLowerCase(Locale.ROOT);
        for (String pattern : ARTIFACT_PATTERNS) {
            int start = lower.indexOf(pattern);
            if (start >= 0) {
                if (isExpectedStructuredText(input, pattern)) {
                    return List.of(new SlopIssueDraft(
                            SlopDimension.ARTIFACT,
                            SlopSeverity.LOW,
                            policy.expectedStyleWeakSignalRisk(),
                            pattern,
                            "当前语境声明了特殊文体，Markdown/列表痕迹只能作为弱信号。",
                            "确认该格式确属正文文体；若不是正文设定，再删除格式标记。",
                            start,
                            start + pattern.length(),
                            text.substring(start, Math.min(text.length(), start + pattern.length())),
                            "consistency_assimilation",
                            "ARTIFACT_STRUCTURED_TEXT",
                            "markdown_residue",
                            "E1",
                            "[\"特殊文体\",\"系统面板正文\",\"论坛体表达\"]",
                            "确认该格式确属正文文体；若不是正文设定，再删除格式标记。"
                    ));
                }
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

    private boolean isExpectedStructuredText(SlopHeuristicInput input, String pattern) {
        boolean structuredPattern = "```".equals(pattern) || "markdown".equals(pattern) || "# ".equals(pattern);
        return structuredPattern && (input.hasContextHint("论坛体")
                || input.hasContextHint("系统面板")
                || input.hasContextHint("报告体")
                || input.hasContextHint("实验文本"));
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

    private record PatternHit(String pattern, int start) {
    }
}
