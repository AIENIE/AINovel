package com.ainovel.app.quality;

import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
        int risk = intVal(root.get("risk_score"), heuristicResult.overallRiskScore());
        boolean revisionRecommended = boolVal(root.get("revision_recommended"), risk >= 55);
        String hint = str(root.get("actionable_hint"), "优先处理重复、套话和格式伪迹。");
        List<SlopIssueDraft> issues = parseIssues(root.get("issues"));
        if (issues.isEmpty()) {
            issues = heuristicResult.issues();
        }
        return new SlopJudgeResult(risk, revisionRecommended, issues, hint);
    }

    private String buildPrompt(SlopQualityRequest request, SlopHeuristicResult heuristicResult) {
        return """
                你是中文小说反 AI slop 质量审校器。只诊断给定候选正文，不续写剧情。
                第一版目标：识别重复、套话、AI 输出伪迹、局部承接问题和轻量风格漂移。
                不要用“AI率”判断，不要要求深度重构。

                必须输出 JSON，格式：
                {
                  "risk_score": 0-100,
                  "revision_recommended": true|false,
                  "actionable_hint": "一句话说明先改什么",
                  "issues": [
                    {
                      "dimension": "REPETITION|GENERICITY|ARTIFACT|LOCAL_COHERENCE|STYLE_DRIFT_LIGHT",
                      "severity": "LOW|MEDIUM|HIGH|BLOCKING",
                      "risk_score": 0-100,
                      "evidence": "原文证据片段",
                      "why_it_matters": "为什么影响阅读",
                      "minimal_fix": "最小修复建议"
                    }
                  ]
                }

                约束：
                - 只在证据明确时判高风险。
                - 武侠/悬疑/奇幻等体裁表达不要机械误伤。
                - 修订建议必须是保守修订，不改变剧情事件和角色决策。

                故事：%s
                类型：%s
                文风目标：%s
                章节：%s
                场景：%s
                场景摘要：%s
                角色上下文：%s
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

    private List<SlopIssueDraft> parseIssues(Object value) {
        List<SlopIssueDraft> issues = new ArrayList<>();
        if (!(value instanceof List<?> list)) {
            return issues;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            issues.add(new SlopIssueDraft(
                    dimension(raw.get("dimension")),
                    severity(raw.get("severity")),
                    intVal(raw.get("risk_score"), 50),
                    str(raw.get("evidence"), ""),
                    str(raw.get("why_it_matters"), ""),
                    str(raw.get("minimal_fix"), "")
            ));
        }
        return issues;
    }

    private SlopDimension dimension(Object value) {
        try {
            return SlopDimension.valueOf(str(value, "GENERICITY"));
        } catch (Exception ignored) {
            return SlopDimension.GENERICITY;
        }
    }

    private SlopSeverity severity(Object value) {
        try {
            return SlopSeverity.valueOf(str(value, "MEDIUM"));
        } catch (Exception ignored) {
            return SlopSeverity.MEDIUM;
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
