package com.ainovel.app.quality;

import com.ainovel.app.common.BusinessException;
import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AiSlopJudgeClient implements SlopJudgeClient {
    private final AiService aiService;
    private final ObjectMapper objectMapper;

    public AiSlopJudgeClient(AiService aiService, ObjectMapper objectMapper) {
        this.aiService = aiService;
        this.objectMapper = objectMapper;
    }

    @Override
    public SlopJudgeResult judge(User user, SlopQualityRequest request, SlopHeuristicResult heuristicResult) {
        String content = aiService.chat(user, new AiChatRequest(
                List.of(new AiChatRequest.Message("user", buildPrompt(request, heuristicResult))),
                null,
                null
        )).content();
        Map<String, Object> root = parseJson(content);
        Map<String, Object> overall = map(root.get("overall"));
        int risk = intVal(overall.get("overall_slop_risk"), intVal(root.get("risk_score"), heuristicResult.overallRiskScore()));
        boolean revisionRecommended = boolVal(root.get("revision_recommended"), risk >= 55);
        String hint = str(root.get("actionable_hint"), "优先处理重复、套话和格式伪迹。");
        List<SlopIssueDraft> issues = parseIssues(root.containsKey("evidence_items") ? root.get("evidence_items") : root.get("issues"), request.candidateText());
        if (issues.isEmpty()) {
            issues = heuristicResult.issues();
        }
        SlopSeverity maxSeverity = issues.stream()
                .map(SlopIssueDraft::severity)
                .max((left, right) -> Integer.compare(left.ordinal(), right.ordinal()))
                .orElse(heuristicResult.maxSeverity());
        SlopQualitySignals signals = parseSignals(root, overall, risk, maxSeverity, issues);
        return new SlopJudgeResult(risk, revisionRecommended, issues, hint, signals);
    }

    private String buildPrompt(SlopQualityRequest request, SlopHeuristicResult heuristicResult) {
        return """
                你是中文小说反 AI slop 质量审校器。只诊断给定候选正文，不续写剧情。
                第一版目标：识别重复、套话、AI 输出伪迹、局部承接问题和轻量风格漂移。
                不要用“AI率”判断，不要要求深度重构。

                必须输出 JSON，格式：
                {
                  "overall": {
                    "overall_slop_risk": 0-100,
                    "risk_label": "low|medium|high|critical",
                    "evidence_level": "E1|E2|E3|E4",
                    "safe_claim": "该文本呈现...风险，但不能证明作者使用AI。"
                  },
                  "revision_recommended": true|false,
                  "actionable_hint": "一句话说明先改什么",
                  "module_scores": {
                    "surface_template": {"score": 0-100, "evidence_count": 0},
                    "voice_fit": {"score": 0-100, "evidence_count": 0},
                    "consistency_assimilation": {"score": 0-100, "evidence_count": 0},
                    "breath_focus_pacing": {"score": 0-100, "evidence_count": 0},
                    "human_trace": {"score": 0-100, "evidence_count": 0}
                  },
                  "evidence_items": [
                    {
                      "char_start": 0,
                      "char_end": 0,
                      "quote": "原文证据",
                      "module": "surface_template|voice_fit|consistency_assimilation|breath_focus_pacing|human_trace",
                      "pattern_id": "可为空",
                      "issue_type": "phrase_pattern|register_mismatch|local_contradiction|event_conveyor_belt|human_trace_missing|meta_leak|repetition",
                      "evidence_level": "E1|E2|E3|E4",
                      "severity": "low|medium|high|blocking",
                      "risk_score": 0-100,
                      "risk_explanation": "为什么影响阅读",
                      "alternative_explanations": ["传统网文俗套"],
                      "repair_hint": "最小修复建议"
                    }
                  ],
                  "alternative_explanations": [],
                  "revision_priorities": [],
                  "rewrite_tasks": []
                }

                约束：
                - 只在证据明确时判高风险。
                - 武侠/悬疑/奇幻等体裁表达不要机械误伤。
                - 修订建议必须是保守修订，不改变剧情事件和角色决策。
                - voice_fit 只能基于下方“风格/角色声音语境”和候选正文证据判断；没有配置时不要虚构角色语域问题。

                故事：%s
                类型：%s
                文风目标：%s
                章节：%s
                场景：%s
                场景摘要：%s
                角色上下文：%s
                风格/角色声音语境：%s
                前文：%s
                本地规则风险：%d / %s

                候选正文：
                %s
                """.formatted(
                safe(request.storyTitle()),
                safe(request.genre()),
                safe(request.tone()),
                safe(request.chapterTitle()),
                safe(request.sceneTitle()),
                safe(request.sceneSummary()),
                truncate(request.characterContext(), 1000),
                truncate(request.styleContext(), 1000),
                truncate(request.previousContext(), 1000),
                heuristicResult.overallRiskScore(),
                heuristicResult.maxSeverity(),
                truncate(request.candidateText(), 5000)
        );
    }

    private Map<String, Object> parseJson(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.startsWith("```")) {
            int firstLineEnd = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstLineEnd >= 0 && lastFence > firstLineEnd) {
                text = text.substring(firstLineEnd + 1, lastFence).trim();
            }
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            text = text.substring(start, end + 1);
        }
        try {
            return objectMapper.readValue(text, new TypeReference<>() {});
        } catch (Exception ex) {
            throw new RuntimeException("反 slop 诊断 JSON 解析失败", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> raw ? (Map<String, Object>) raw : Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Object> list(Object value) {
        return value instanceof List<?> raw ? (List<Object>) raw : List.of();
    }

    private SlopQualitySignals parseSignals(Map<String, Object> root,
                                            Map<String, Object> overall,
                                            int risk,
                                            SlopSeverity maxSeverity,
                                            List<SlopIssueDraft> issues) {
        SlopQualitySignals fallback = SlopQualitySignals.fromIssues(risk, maxSeverity, issues);
        return new SlopQualitySignals(
                str(overall.get("risk_label"), fallback.riskLabel()),
                str(overall.get("evidence_level"), fallback.evidenceLevel()),
                str(overall.get("safe_claim"), fallback.safeClaim()),
                map(root.get("module_scores")).isEmpty() ? fallback.moduleScores() : new LinkedHashMap<>(map(root.get("module_scores"))),
                stringList(root.get("alternative_explanations"), fallback.alternativeExplanations()),
                list(root.get("revision_priorities")).isEmpty() ? fallback.revisionPriorities() : list(root.get("revision_priorities")),
                list(root.get("rewrite_tasks")).isEmpty() ? fallback.rewriteTasks() : list(root.get("rewrite_tasks"))
        );
    }

    private List<String> stringList(Object value, List<String> fallback) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return fallback;
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            String text = str(item, "");
            if (!text.isBlank()) {
                result.add(text);
            }
        }
        return result.isEmpty() ? fallback : result;
    }

    private List<SlopIssueDraft> parseIssues(Object value, String text) {
        List<SlopIssueDraft> issues = new ArrayList<>();
        if (!(value instanceof List<?> list)) {
            return issues;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            String module = str(raw.get("module"), "");
            String quote = str(raw.get("quote"), str(raw.get("evidence"), ""));
            Integer start = nullableInt(raw.get("char_start"));
            Integer end = nullableInt(raw.get("char_end"));
            if ((start == null || end == null) && !quote.isBlank()) {
                int found = text == null ? -1 : text.indexOf(quote);
                if (found >= 0) {
                    start = found;
                    end = found + quote.length();
                }
            }
            String explanation = str(raw.get("risk_explanation"), str(raw.get("why_it_matters"), ""));
            String repairHint = str(raw.get("repair_hint"), str(raw.get("minimal_fix"), ""));
            issues.add(new SlopIssueDraft(
                    module.isBlank() ? dimension(raw.get("dimension")) : dimensionForModule(module),
                    severity(raw.get("severity")),
                    intVal(raw.get("risk_score"), 50),
                    quote,
                    explanation,
                    repairHint,
                    start,
                    end,
                    quote,
                    module.isBlank() ? moduleForDimension(dimension(raw.get("dimension"))) : module,
                    str(raw.get("pattern_id"), ""),
                    str(raw.get("issue_type"), ""),
                    str(raw.get("evidence_level"), "E1"),
                    writeJson(raw.containsKey("alternative_explanations") ? raw.get("alternative_explanations") : List.of()),
                    repairHint
            ));
        }
        return issues;
    }

    private SlopDimension dimension(Object value) {
        try {
            return SlopDimension.valueOf(str(value, "GENERICITY").toUpperCase());
        } catch (Exception ignored) {
            return SlopDimension.GENERICITY;
        }
    }

    private SlopDimension dimensionForModule(String module) {
        return switch (str(module, "surface_template")) {
            case "voice_fit" -> SlopDimension.STYLE_DRIFT_LIGHT;
            case "consistency_assimilation" -> SlopDimension.LOCAL_COHERENCE;
            case "breath_focus_pacing" -> SlopDimension.LOCAL_COHERENCE;
            case "human_trace" -> SlopDimension.GENERICITY;
            default -> SlopDimension.GENERICITY;
        };
    }

    private String moduleForDimension(SlopDimension dimension) {
        if (dimension == null) {
            return "surface_template";
        }
        return switch (dimension) {
            case ARTIFACT -> "consistency_assimilation";
            case LOCAL_COHERENCE -> "breath_focus_pacing";
            case STYLE_DRIFT_LIGHT -> "voice_fit";
            case REPETITION, GENERICITY -> "surface_template";
        };
    }

    private SlopSeverity severity(Object value) {
        try {
            return SlopSeverity.valueOf(str(value, "MEDIUM").toUpperCase());
        } catch (Exception ignored) {
            return SlopSeverity.MEDIUM;
        }
    }

    private Integer nullableInt(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private boolean boolVal(Object value, boolean fallback) {
        return value instanceof Boolean b ? b : fallback;
    }

    private int intVal(Object value, int fallback) {
        if (value instanceof Number number) {
            return Math.max(0, Math.min(100, number.intValue()));
        }
        try {
            return Math.max(0, Math.min(100, Integer.parseInt(String.valueOf(value))));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String str(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "未指定" : value;
    }

    private String truncate(String value, int limit) {
        if (value == null) {
            return "";
        }
        return value.length() <= limit ? value : value.substring(0, limit);
    }
}
