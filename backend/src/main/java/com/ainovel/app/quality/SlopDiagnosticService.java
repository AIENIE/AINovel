package com.ainovel.app.quality;

import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.common.JsonColumnCodec;
import com.ainovel.app.quality.model.SlopQualityIssue;
import com.ainovel.app.quality.model.SlopQualityRun;
import com.ainovel.app.quality.repo.SlopQualityRunRepository;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
public class SlopDiagnosticService {
    private final AiService aiService;
    private final ObjectMapper objectMapper;
    private final LocalSlopHeuristics heuristics;
    private final SlopQualityRunRepository runRepository;
    private final JsonColumnCodec jsonColumnCodec;

    public SlopDiagnosticService(AiService aiService,
                                 ObjectMapper objectMapper,
                                 LocalSlopHeuristics heuristics,
                                 SlopQualityRunRepository runRepository,
                                 JsonColumnCodec jsonColumnCodec) {
        this.aiService = aiService;
        this.objectMapper = objectMapper;
        this.heuristics = heuristics;
        this.runRepository = runRepository;
        this.jsonColumnCodec = jsonColumnCodec;
    }

    @Transactional
    public SlopQualityRun analyze(User user, SlopQualityRequest request) {
        SlopHeuristicResult heuristicResult = heuristics.evaluate(SlopHeuristicInput.from(request, request.candidateText()));
        try {
            Map<String, Object> root = parseJson(aiService.chat(user, new AiChatRequest(
                    List.of(new AiChatRequest.Message("user", buildPrompt(request, heuristicResult))),
                    null,
                    null
            )).content());
            return runRepository.save(toRun(request, root, heuristicResult, false));
        } catch (RuntimeException ex) {
            return runRepository.save(fallbackRun(request, heuristicResult, ex));
        }
    }

    private SlopQualityRun toRun(SlopQualityRequest request,
                                 Map<String, Object> root,
                                 SlopHeuristicResult heuristicResult,
                                 boolean degraded) {
        Map<String, Object> overall = map(root.get("overall"));
        int risk = intVal(overall.get("overall_slop_risk"), heuristicResult.overallRiskScore());
        String riskLabel = str(overall.get("risk_label"), label(risk));
        String evidenceLevel = str(overall.get("evidence_level"), evidenceLevel(heuristicResult.maxSeverity(), risk));
        String safeClaim = str(overall.get("safe_claim"),
                "该文本呈现%s级模板化/slop风险；这不能证明作者使用AI。".formatted(riskLabel));
        List<SlopIssueDraft> parsedIssues = parseIssues(root.get("evidence_items"), request.candidateText());
        List<SlopIssueDraft> issues = parsedIssues.isEmpty() ? heuristicResult.issues() : parsedIssues;

        SlopQualityRun run = baseRun(request);
        run.setStatus(statusFor(degraded, risk, issues));
        run.setMaxSeverity(maxSeverity(issues, severityForRisk(risk)));
        run.setOverallRiskScore(risk);
        run.setRiskLabel(riskLabel);
        run.setEvidenceLevel(evidenceLevel);
        run.setSafeClaim(truncate(safeClaim, 500));
        run.setSummary(str(root.get("summary"), safeClaim));
        run.setModuleScoresJson(writeJson(root.getOrDefault("module_scores", Map.of())));
        run.setAlternativeExplanationsJson(writeJson(root.getOrDefault("alternative_explanations", defaultAlternatives())));
        run.setRevisionPrioritiesJson(writeJson(root.getOrDefault("revision_priorities", List.of())));
        run.setRewriteTasksJson(writeJson(root.getOrDefault("rewrite_tasks", List.of())));
        for (SlopIssueDraft draft : issues) {
            run.getIssues().add(toIssue(run, draft));
        }
        return run;
    }

    private SlopQualityRun fallbackRun(SlopQualityRequest request, SlopHeuristicResult heuristicResult, RuntimeException ex) {
        SlopQualityRun run = baseRun(request);
        int risk = heuristicResult.overallRiskScore();
        String riskLabel = label(risk);
        run.setStatus(SlopQualityStatus.DEGRADED);
        run.setMaxSeverity(heuristicResult.maxSeverity());
        run.setOverallRiskScore(risk);
        run.setRiskLabel(riskLabel);
        run.setEvidenceLevel(evidenceLevel(heuristicResult.maxSeverity(), risk));
        run.setSafeClaim("该文本呈现%s级模板化/slop风险；这不能证明作者使用AI。".formatted(riskLabel));
        run.setSummary("文本 slop 语义诊断失败，已按本地规则降级记录：" + truncate(ex.getMessage(), 160));
        run.setModuleScoresJson(writeJson(localModuleScores(heuristicResult)));
        run.setAlternativeExplanationsJson(writeJson(defaultAlternatives()));
        run.setRevisionPrioritiesJson("[]");
        run.setRewriteTasksJson("[]");
        for (SlopIssueDraft draft : heuristicResult.issues()) {
            run.getIssues().add(toIssue(run, draft));
        }
        return run;
    }

    private SlopQualityRun baseRun(SlopQualityRequest request) {
        SlopQualityRun run = new SlopQualityRun();
        run.setStoryId(request.storyId());
        run.setManuscriptId(request.manuscriptId());
        run.setSceneId(request.sceneId());
        run.setRevised(false);
        run.setRevisionCount(0);
        run.setCandidateTextHash(hash(request.candidateText()));
        run.setAcceptedTextHash(hash(request.candidateText()));
        run.setSourceTextHash(hash(request.candidateText()));
        run.setAnalysisMode("manual_scene");
        return run;
    }

    private SlopQualityIssue toIssue(SlopQualityRun run, SlopIssueDraft draft) {
        SlopQualityIssue issue = new SlopQualityIssue();
        issue.setRun(run);
        issue.setDimension(draft.dimension());
        issue.setSeverity(draft.severity());
        issue.setRiskScore(draft.riskScore());
        issue.setEvidence(truncate(draft.evidence(), 600));
        issue.setWhyItMatters(truncate(draft.whyItMatters(), 800));
        issue.setMinimalFix(truncate(draft.minimalFix(), 800));
        issue.setCharStart(draft.charStart());
        issue.setCharEnd(draft.charEnd());
        issue.setQuote(truncate(draft.quote(), 800));
        issue.setModule(truncate(draft.module(), 80));
        issue.setPatternId(truncate(draft.patternId(), 80));
        issue.setIssueType(truncate(draft.issueType(), 80));
        issue.setEvidenceLevel(truncate(draft.evidenceLevel(), 8));
        issue.setAlternativeExplanationsJson(draft.alternativeExplanationsJson());
        issue.setRepairHint(truncate(draft.repairHint(), 800));
        return issue;
    }

    private List<SlopIssueDraft> parseIssues(Object value, String text) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<SlopIssueDraft> issues = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            String module = str(raw.get("module"), "surface_template");
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
                    dimension(module),
                    severity(raw.get("severity"), intVal(raw.get("risk_score"), 50)),
                    intVal(raw.get("risk_score"), 50),
                    quote,
                    explanation,
                    repairHint,
                    start,
                    end,
                    quote,
                    module,
                    str(raw.get("pattern_id"), ""),
                    str(raw.get("issue_type"), ""),
                    str(raw.get("evidence_level"), "E1"),
                    writeJson(raw.containsKey("alternative_explanations") ? raw.get("alternative_explanations") : defaultAlternatives()),
                    repairHint
            ));
        }
        return issues;
    }

    private String buildPrompt(SlopQualityRequest request, SlopHeuristicResult heuristicResult) {
        return """
                你是中文小说文本 slop 风险诊断助手。你的任务不是判断作者是否使用 AI，而是评估文本呈现出的模板化、工业化、AI味/slop 风险，并给出可修改证据。

                必须遵守：
                1) 不输出 AI 概率，不说作者用了 AI。
                2) 每个判断必须引用原文证据；单个黑名单词只能作为弱信号。
                3) 必须列出替代解释，如传统网文俗套、人工低水平写作、平台公式化、作者个人风格。
                4) 修改优先级：元泄漏/事实矛盾/设定冲突 > 角色行为逻辑 > 后果/代价/关系变化 > 角色语域 > 表层套话。
                5) voice_fit 只能基于下方“风格/角色声音语境”和正文证据判断；没有配置时不要虚构角色语域问题。

                输出 JSON：
                {
                  "summary": "一句话摘要",
                  "overall": {
                    "overall_slop_risk": 0-100,
                    "risk_label": "low|medium|high|critical",
                    "evidence_level": "E1|E2|E3|E4",
                    "safe_claim": "该文本呈现...风险，但不能证明作者使用AI。"
                  },
                  "module_scores": {
                    "surface_template": {"score": 0-100},
                    "voice_fit": {"score": 0-100},
                    "consistency_assimilation": {"score": 0-100},
                    "breath_focus_pacing": {"score": 0-100},
                    "human_trace": {"score": 0-100}
                  },
                  "evidence_items": [
                    {
                      "char_start": 0,
                      "char_end": 0,
                      "quote": "原文证据",
                      "module": "surface_template|voice_fit|consistency_assimilation|breath_focus_pacing|human_trace",
                      "pattern_id": "可为空",
                      "issue_type": "phrase_pattern|register_mismatch|local_contradiction|event_conveyor_belt|human_trace_missing",
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

                本地规则风险：%d / %s
                故事：%s
                类型：%s
                文风目标：%s
                章节：%s
                场景：%s
                场景摘要：%s
                角色上下文：%s
                风格/角色声音语境：%s
                前文：%s

                当前正文：
                %s
                """.formatted(
                heuristicResult.overallRiskScore(),
                heuristicResult.maxSeverity(),
                safe(request.storyTitle()),
                safe(request.genre()),
                safe(request.tone()),
                safe(request.chapterTitle()),
                safe(request.sceneTitle()),
                truncate(request.sceneSummary(), 900),
                truncate(request.characterContext(), 1400),
                truncate(request.styleContext(), 1400),
                truncate(request.previousContext(), 1200),
                truncate(request.candidateText(), 7000)
        );
    }

    private Map<String, Object> parseJson(String raw) {
        String text = stripWrapper(raw);
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            text = text.substring(start, end + 1);
        }
        try {
            return objectMapper.readValue(text, new TypeReference<>() {});
        } catch (Exception ex) {
            throw new RuntimeException("文本 slop 诊断 JSON 解析失败", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> raw ? (Map<String, Object>) raw : Map.of();
    }

    private String writeJson(Object value) {
        return jsonColumnCodec.write(value, "[]");
    }

    private Map<String, Object> localModuleScores(SlopHeuristicResult result) {
        return Map.of(
                "surface_template", Map.of("score", result.overallRiskScore()),
                "voice_fit", Map.of("score", 0),
                "consistency_assimilation", Map.of("score", result.maxSeverity() == SlopSeverity.BLOCKING ? result.overallRiskScore() : 0),
                "breath_focus_pacing", Map.of("score", 0),
                "human_trace", Map.of("score", 0)
        );
    }

    private List<String> defaultAlternatives() {
        return List.of("传统网文俗套", "人工低水平写作", "工作室公式化", "题材/平台惯例", "作者个人文风");
    }

    private SlopQualityStatus statusFor(boolean degraded, int risk, List<SlopIssueDraft> issues) {
        if (degraded) {
            return SlopQualityStatus.DEGRADED;
        }
        SlopSeverity max = maxSeverity(issues, severityForRisk(risk));
        if (risk >= 70 || max == SlopSeverity.HIGH || max == SlopSeverity.BLOCKING) {
            return SlopQualityStatus.ACCEPTED_WITH_ISSUES;
        }
        return SlopQualityStatus.ACCEPTED;
    }

    private SlopSeverity maxSeverity(List<SlopIssueDraft> issues, SlopSeverity fallback) {
        SlopSeverity max = fallback;
        for (SlopIssueDraft issue : issues) {
            if (rank(issue.severity()) > rank(max)) {
                max = issue.severity();
            }
        }
        return max;
    }

    private SlopSeverity severity(Object value, int risk) {
        String text = str(value, "").toUpperCase();
        try {
            return SlopSeverity.valueOf(text);
        } catch (Exception ignored) {
            return severityForRisk(risk);
        }
    }

    private SlopSeverity severityForRisk(int risk) {
        if (risk >= 85) {
            return SlopSeverity.BLOCKING;
        }
        if (risk >= 70) {
            return SlopSeverity.HIGH;
        }
        if (risk >= 40) {
            return SlopSeverity.MEDIUM;
        }
        return SlopSeverity.LOW;
    }

    private SlopDimension dimension(String module) {
        return switch (str(module, "surface_template")) {
            case "voice_fit" -> SlopDimension.STYLE_DRIFT_LIGHT;
            case "consistency_assimilation" -> SlopDimension.LOCAL_COHERENCE;
            case "breath_focus_pacing" -> SlopDimension.LOCAL_COHERENCE;
            case "human_trace" -> SlopDimension.GENERICITY;
            default -> SlopDimension.GENERICITY;
        };
    }

    private String evidenceLevel(SlopSeverity severity, int risk) {
        if (severity == SlopSeverity.BLOCKING) {
            return "E4";
        }
        if (severity == SlopSeverity.HIGH || risk >= 70) {
            return "E2";
        }
        return "E1";
    }

    private String label(int riskScore) {
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

    private int rank(SlopSeverity severity) {
        return switch (severity) {
            case LOW -> 1;
            case MEDIUM -> 2;
            case HIGH -> 3;
            case BLOCKING -> 4;
        };
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

    private String stripWrapper(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.startsWith("```")) {
            int firstLineEnd = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstLineEnd >= 0 && lastFence > firstLineEnd) {
                text = text.substring(firstLineEnd + 1, lastFence).trim();
            }
        }
        return text;
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

    private String hash(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return null;
        }
    }
}
