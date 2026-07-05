package com.ainovel.app.quality;

import com.ainovel.app.common.BusinessException;
import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.common.JsonColumnCodec;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.quality.model.PlotQualityIssue;
import com.ainovel.app.quality.model.PlotQualityRun;
import com.ainovel.app.quality.repo.PlotQualityRunRepository;
import com.ainovel.app.story.model.CharacterCard;
import com.ainovel.app.story.model.Outline;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.story.repo.CharacterCardRepository;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class PlotQualityService {
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private final AiService aiService;
    private final ObjectMapper objectMapper;
    private final ManuscriptRepository manuscriptRepository;
    private final CharacterCardRepository characterCardRepository;
    private final PlotQualityRunRepository runRepository;
    private final SlopQualityGate slopQualityGate;
    private final JsonColumnCodec jsonColumnCodec;

    public PlotQualityService(AiService aiService,
                              ObjectMapper objectMapper,
                              ManuscriptRepository manuscriptRepository,
                              CharacterCardRepository characterCardRepository,
                              PlotQualityRunRepository runRepository,
                              SlopQualityGate slopQualityGate,
                              JsonColumnCodec jsonColumnCodec) {
        this.aiService = aiService;
        this.objectMapper = objectMapper;
        this.manuscriptRepository = manuscriptRepository;
        this.characterCardRepository = characterCardRepository;
        this.runRepository = runRepository;
        this.slopQualityGate = slopQualityGate;
        this.jsonColumnCodec = jsonColumnCodec;
    }

    public List<PlotQualityRun> listRuns(UUID manuscriptId, UUID sceneId) {
        return sceneId == null
                ? runRepository.findTop20ByManuscriptIdOrderByCreatedAtDesc(manuscriptId)
                : runRepository.findTop20ByManuscriptIdAndSceneIdOrderByCreatedAtDesc(manuscriptId, sceneId);
    }

    @Transactional
    public PlotQualityRun analyzeScene(User user, Manuscript manuscript, UUID sceneId) {
        return analyze(user, buildRequest(manuscript, sceneId));
    }

    @Transactional
    public PlotQualityRun analyze(User user, PlotQualityRequest request) {
        try {
            String content = aiService.chat(user, new AiChatRequest(
                    List.of(new AiChatRequest.Message("user", buildAnalysisPrompt(request))),
                    null,
                    null
            )).content();
            return runRepository.save(toRun(request, parseJson(content), false));
        } catch (RuntimeException ex) {
            PlotQualityRun degraded = newBaseRun(request);
            degraded.setStatus(PlotQualityStatus.DEGRADED);
            degraded.setMaxSeverity(PlotQualitySeverity.MEDIUM);
            degraded.setOverallRiskScore(45);
            degraded.setSummary("剧情质量诊断失败，已降级记录：" + truncate(ex.getMessage(), 180));
            degraded.setRewritePlanJson("[]");
            degraded.setSurgicalFixesJson("[]");
            return runRepository.save(degraded);
        }
    }

    private PlotQualityRequest buildRequest(Manuscript manuscript, UUID sceneId) {
        Outline outline = manuscript.getOutline();
        Story story = outline.getStory();
        Map<String, String> sections = readSectionMap(manuscript.getSectionsJson());
        SceneContext scene = resolveScene(outline, sceneId);
        return new PlotQualityRequest(
                story.getId(),
                manuscript.getId(),
                sceneId,
                safe(story.getTitle(), "未命名故事"),
                safe(story.getGenre(), "未指定"),
                safe(story.getTone(), "沉浸、连贯"),
                scene.chapterTitle(),
                scene.chapterOrder(),
                scene.sceneTitle(),
                scene.sceneOrder(),
                scene.sceneSummary(),
                outlinePlanning(outline),
                previousContext(scene, sections),
                characterContext(characterCardRepository.findByStory(story)),
                stripHtml(sections.get(sceneId.toString()))
        );
    }

    public PlotQualityTrend buildTrend(UUID manuscriptId) {
        List<PlotQualityRun> runs = runRepository.findTop200ByManuscriptIdOrderByCreatedAtDesc(manuscriptId);
        Map<UUID, PlotQualityRun> latestByScene = new LinkedHashMap<>();
        for (PlotQualityRun run : runs) {
            latestByScene.putIfAbsent(run.getSceneId(), run);
        }
        List<PlotQualityRun> latest = latestByScene.values().stream()
                .sorted(Comparator.comparingInt(PlotQualityRun::getChapterOrder)
                        .thenComparingInt(PlotQualityRun::getSceneOrder)
                        .thenComparing(PlotQualityRun::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        double average = latest.stream().mapToInt(PlotQualityRun::getOverallRiskScore).average().orElse(0d);
        int highRisk = (int) latest.stream()
                .filter(run -> run.getOverallRiskScore() >= 70 || run.getMaxSeverity() == PlotQualitySeverity.HIGH || run.getMaxSeverity() == PlotQualitySeverity.BLOCKING)
                .count();
        Map<String, Long> dimensionCounts = latest.stream()
                .flatMap(run -> run.getIssues().stream())
                .collect(Collectors.groupingBy(issue -> issue.getDimension().name(), LinkedHashMap::new, Collectors.counting()));
        List<PlotQualityTrend.Point> points = latest.stream()
                .map(run -> new PlotQualityTrend.Point(
                        run.getId(),
                        run.getSceneId(),
                        run.getChapterTitle(),
                        run.getSceneTitle(),
                        run.getChapterOrder(),
                        run.getSceneOrder(),
                        run.getOverallRiskScore(),
                        run.getMaxSeverity().name(),
                        run.getStatus().name()
                ))
                .toList();
        return new PlotQualityTrend(manuscriptId, Math.round(average * 1000d) / 1000d, highRisk, dimensionCounts, points);
    }

    @Transactional
    public PlotQualityRun generateRevisionCandidate(User user, Manuscript manuscript, UUID runId) {
        PlotQualityRun run = requireRun(runId);
        ensureRunBelongsToManuscript(run, manuscript);
        String currentText = currentPlainText(manuscript, run.getSceneId());
        if (!Objects.equals(run.getSourceTextHash(), hashText(currentText))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前正文已变化，请重新进行剧情诊断");
        }
        String candidate = aiService.chat(user, new AiChatRequest(
                List.of(new AiChatRequest.Message("user", buildRevisionPrompt(run, currentText))),
                null,
                null
        )).content();
        run.setRevisionCandidateText(stripWrapper(candidate));
        return runRepository.save(run);
    }

    @Transactional
    public PlotQualityRun applyRevision(User user, Manuscript manuscript, UUID runId) {
        PlotQualityRun run = requireRun(runId);
        ensureRunBelongsToManuscript(run, manuscript);
        if (run.getRevisionCandidateText() == null || run.getRevisionCandidateText().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "尚未生成可采纳的剧情修订候选");
        }
        String currentText = currentPlainText(manuscript, run.getSceneId());
        if (!Objects.equals(run.getSourceTextHash(), hashText(currentText))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前正文已变化，请重新进行剧情诊断");
        }

        SlopQualityResult textGate = slopQualityGate.evaluateAndRepair(user, new SlopQualityRequest(
                run.getStoryId(),
                run.getManuscriptId(),
                run.getSceneId(),
                "未指定",
                "未指定",
                "沉浸、连贯",
                safe(run.getChapterTitle(), "未指定章节"),
                safe(run.getSceneTitle(), "未指定场景"),
                "",
                "剧情修订采纳前的文本门禁",
                "",
                "剧情修订候选采纳前未加载风格画像；仅按候选文本执行保守门禁。",
                run.getRevisionCandidateText()
        ));
        Map<String, String> sections = readSectionMap(manuscript.getSectionsJson());
        sections.put(run.getSceneId().toString(), toEditorHtml(textGate.acceptedText()));
        manuscript.setSectionsJson(writeJson(sections));
        run.setRevisionApplied(true);
        run.setRevisionAppliedAt(Instant.now());
        run.setSourceTextHash(hashText(textGate.acceptedText()));
        manuscriptRepository.save(manuscript);
        return runRepository.save(run);
    }

    private PlotQualityRun toRun(PlotQualityRequest request, Map<String, Object> root, boolean degraded) {
        PlotQualityRun run = newBaseRun(request);
        int risk = intVal(root.get("risk_score"), 0);
        PlotQualitySeverity maxSeverity = severity(root.get("max_severity"), risk >= 70 ? PlotQualitySeverity.HIGH : PlotQualitySeverity.LOW);
        run.setOverallRiskScore(risk);
        run.setMaxSeverity(maxSeverity);
        run.setStatus(risk >= 70 || maxSeverity == PlotQualitySeverity.HIGH || maxSeverity == PlotQualitySeverity.BLOCKING
                ? PlotQualityStatus.ACCEPTED_WITH_ISSUES
                : PlotQualityStatus.ACCEPTED);
        run.setSummary(str(root.get("summary"), degraded ? "剧情质量诊断降级。" : "剧情质量诊断完成。"));
        run.setRewritePlanJson(writeJson(root.getOrDefault("rewrite_plan", List.of())));
        run.setSurgicalFixesJson(writeJson(root.getOrDefault("surgical_fixes", List.of())));
        for (PlotQualityIssue issue : parseIssues(root.get("issues"))) {
            issue.setRun(run);
            run.getIssues().add(issue);
        }
        return run;
    }

    private PlotQualityRun newBaseRun(PlotQualityRequest request) {
        PlotQualityRun run = new PlotQualityRun();
        run.setStoryId(request.storyId());
        run.setManuscriptId(request.manuscriptId());
        run.setSceneId(request.sceneId());
        run.setChapterTitle(truncate(request.chapterTitle(), 200));
        run.setSceneTitle(truncate(request.sceneTitle(), 200));
        run.setChapterOrder(request.chapterOrder());
        run.setSceneOrder(request.sceneOrder());
        run.setSourceTextHash(hashText(request.sceneText()));
        run.setStatus(PlotQualityStatus.ACCEPTED);
        run.setMaxSeverity(PlotQualitySeverity.LOW);
        run.setOverallRiskScore(0);
        run.setRevisionApplied(false);
        return run;
    }

    private List<PlotQualityIssue> parseIssues(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<PlotQualityIssue> issues = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            PlotQualityIssue issue = new PlotQualityIssue();
            issue.setDimension(dimension(raw.get("dimension")));
            issue.setSeverity(severity(raw.get("severity"), PlotQualitySeverity.MEDIUM));
            issue.setRiskScore(intVal(raw.get("risk_score"), 50));
            issue.setEvidence(truncate(str(raw.get("evidence"), ""), 800));
            issue.setWhyItMatters(truncate(str(raw.get("why_it_matters"), ""), 800));
            issue.setMinimalFix(truncate(str(raw.get("minimal_fix"), ""), 800));
            issues.add(issue);
        }
        return issues;
    }

    private String buildAnalysisPrompt(PlotQualityRequest request) {
        return """
                你是中文小说剧情质量审稿人。不要润色文风，只检查剧情层 slop。
                请基于故事规划、前文、角色和当前场景正文，输出 JSON。

                固定维度：
                - GOAL_CONFLICT：场景目标与阻力/冲突是否清楚
                - CAUSALITY：关键事件是否有足够前因和结果承接
                - AGENCY：角色是否主动决策，行动是否符合设定
                - STAKES：失败代价、收益和风险是否具体
                - FORESHADOW_PAYOFF：伏笔、误导和回收是否断裂
                - REPETITION：桥段、冲突或信息释放是否重复
                - SCENE_FUNCTION：场景是否承担推进、揭示或转折功能
                - READER_CURIOSITY：悬念、问题和期待是否持续

                输出 JSON 格式：
                {
                  "risk_score": 0-100,
                  "max_severity": "LOW|MEDIUM|HIGH|BLOCKING",
                  "summary": "一句话总结",
                  "issues": [
                    {
                      "dimension": "AGENCY",
                      "severity": "LOW|MEDIUM|HIGH|BLOCKING",
                      "risk_score": 0-100,
                      "evidence": "原文证据",
                      "why_it_matters": "为什么影响剧情阅读",
                      "minimal_fix": "最小剧情修复建议"
                    }
                  ],
                  "rewrite_plan": ["若允许一键采纳，按 3-5 个 beat 给当前场景修订计划"],
                  "surgical_fixes": ["不重写正文时的最小修复建议"]
                }

                约束：
                - 只诊断当前场景，不评价作者。
                - 不因类型小说惯例直接判负；必须指出当前文本中的具体落点。
                - rewrite_plan 只能改变当前场景的呈现顺序和桥接，不得改结局、关键设定、人物关系。

                故事：%s
                类型：%s
                文风：%s
                章节：第 %d 章《%s》
                场景：第 %d 节《%s》
                场景摘要：%s
                结构规划：%s
                角色上下文：%s
                前文：%s

                当前场景正文：
                %s
                """.formatted(
                safe(request.storyTitle(), "未命名故事"),
                safe(request.genre(), "未指定"),
                safe(request.tone(), "沉浸、连贯"),
                request.chapterOrder(),
                safe(request.chapterTitle(), "未命名章节"),
                request.sceneOrder(),
                safe(request.sceneTitle(), "未命名场景"),
                truncate(request.sceneSummary(), 900),
                truncate(request.outlinePlanning(), 1800),
                truncate(request.characterContext(), 1400),
                truncate(request.previousContext(), 1200),
                truncate(request.sceneText(), 7000)
        );
    }

    private String buildRevisionPrompt(PlotQualityRun run, String currentText) {
        String issues = run.getIssues().stream()
                .map(issue -> "- %s/%s：%s；建议：%s".formatted(issue.getDimension(), issue.getSeverity(), issue.getEvidence(), issue.getMinimalFix()))
                .collect(Collectors.joining("\n"));
        return """
                你是中文小说剧情修订执行者。请按 rewrite_plan 对当前场景做受约束修订。
                只输出修订后的小说正文，不要标题、解释、markdown 或 JSON。

                硬性约束：
                1) 只修当前场景。
                2) 不改变结局、关键设定、人物关系、主角目标和场景核心事件。
                3) 优先补动机桥接、因果前因、stakes 明示和节奏推进。
                4) 保持原文大致篇幅和文风。

                章节：%s
                场景：%s
                问题证据：
                %s

                rewrite_plan:
                %s

                当前正文：
                %s
                """.formatted(
                safe(run.getChapterTitle(), "未指定章节"),
                safe(run.getSceneTitle(), "未指定场景"),
                issues.isBlank() ? "暂无结构化问题，按摘要做最小修订。" : issues,
                safe(run.getRewritePlanJson(), "[]"),
                truncate(currentText, 7000)
        );
    }

    private PlotQualityRun requireRun(UUID runId) {
        return runRepository.findById(runId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "剧情质量记录不存在"));
    }

    private void ensureRunBelongsToManuscript(PlotQualityRun run, Manuscript manuscript) {
        if (manuscript == null || manuscript.getId() == null || !manuscript.getId().equals(run.getManuscriptId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "剧情质量记录不属于当前稿件");
        }
    }

    private SceneContext resolveScene(Outline outline, UUID sceneId) {
        Map<String, Object> root = readObjectMap(outline.getContentJson());
        List<Map<String, Object>> chapters = listOfMap(root.get("chapters"));
        for (int chapterIndex = 0; chapterIndex < chapters.size(); chapterIndex++) {
            Map<String, Object> chapter = chapters.get(chapterIndex);
            List<Map<String, Object>> scenes = listOfMap(chapter.get("scenes"));
            List<UUID> previousSceneIds = new ArrayList<>();
            for (int sceneIndex = 0; sceneIndex < scenes.size(); sceneIndex++) {
                Map<String, Object> scene = scenes.get(sceneIndex);
                UUID id = uuid(scene.get("id"));
                if (id != null && id.equals(sceneId)) {
                    return new SceneContext(
                            safe(str(chapter.get("title"), ""), "未命名章节"),
                            intVal(chapter.get("order"), chapterIndex + 1),
                            safe(str(scene.get("title"), ""), "未命名场景"),
                            intVal(scene.get("order"), sceneIndex + 1),
                            safe(str(scene.get("summary"), ""), ""),
                            previousSceneIds
                    );
                }
                if (id != null) {
                    previousSceneIds.add(id);
                }
            }
        }
        throw new BusinessException("场景不存在，无法进行剧情诊断");
    }

    private String outlinePlanning(Outline outline) {
        Map<String, Object> root = readObjectMap(outline.getContentJson());
        Object planning = root.get("planning");
        if (planning == null) {
            return "暂无结构规划。";
        }
        try {
            return objectMapper.writeValueAsString(planning);
        } catch (Exception ex) {
            return String.valueOf(planning);
        }
    }

    private String previousContext(SceneContext scene, Map<String, String> sections) {
        if (scene.previousSceneIds().isEmpty()) {
            return "暂无可用前文。";
        }
        StringBuilder builder = new StringBuilder();
        int kept = 0;
        for (int i = scene.previousSceneIds().size() - 1; i >= 0 && kept < 2; i--) {
            String plain = stripHtml(sections.get(scene.previousSceneIds().get(i).toString()));
            if (plain.isBlank()) {
                continue;
            }
            builder.append("前文片段 ").append(kept + 1).append("：").append(truncate(plain, 600)).append('\n');
            kept++;
        }
        return builder.length() == 0 ? "暂无可用前文。" : builder.toString();
    }

    private String characterContext(List<CharacterCard> cards) {
        if (cards == null || cards.isEmpty()) {
            return "暂无角色卡。";
        }
        StringBuilder builder = new StringBuilder();
        for (CharacterCard card : cards) {
            builder.append("- ").append(safe(card.getName(), "角色"))
                    .append("：").append(safe(card.getSynopsis(), ""))
                    .append(" 背景：").append(truncate(safe(card.getDetails(), ""), 180))
                    .append(" 关系：").append(truncate(safe(card.getRelationships(), ""), 160))
                    .append('\n');
        }
        return builder.toString();
    }

    private String currentPlainText(Manuscript manuscript, UUID sceneId) {
        return stripHtml(readSectionMap(manuscript.getSectionsJson()).get(sceneId.toString()));
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
            throw new RuntimeException("剧情诊断 JSON 解析失败", ex);
        }
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

    private Map<String, String> readSectionMap(String json) {
        return jsonColumnCodec.read(json, new TypeReference<>() {}, new HashMap<>());
    }

    private Map<String, Object> readObjectMap(String json) {
        return jsonColumnCodec.read(json, new TypeReference<>() {}, new HashMap<>());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMap(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                out.add(new HashMap<>((Map<String, Object>) map));
            }
        }
        return out;
    }

    private String writeJson(Object value) {
        return jsonColumnCodec.write(value, "[]");
    }

    private String stripHtml(String html) {
        if (html == null) {
            return "";
        }
        return HTML_TAG_PATTERN.matcher(html)
                .replaceAll("")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .trim();
    }

    private String toEditorHtml(String text) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isBlank()) {
            return "";
        }
        StringBuilder html = new StringBuilder();
        for (String paragraph : normalized.split("\\R+")) {
            String p = paragraph.trim();
            if (!p.isBlank()) {
                html.append("<p>").append(escapeHtml(p)).append("</p>");
            }
        }
        return html.toString();
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static String hashText(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return null;
        }
    }

    private UUID uuid(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (Exception ex) {
            return null;
        }
    }

    private PlotQualityDimension dimension(Object value) {
        try {
            return PlotQualityDimension.valueOf(str(value, "CAUSALITY"));
        } catch (Exception ex) {
            return PlotQualityDimension.CAUSALITY;
        }
    }

    private PlotQualitySeverity severity(Object value, PlotQualitySeverity fallback) {
        try {
            return PlotQualitySeverity.valueOf(str(value, fallback.name()));
        } catch (Exception ex) {
            return fallback;
        }
    }

    private int intVal(Object value, int fallback) {
        if (value instanceof Number number) {
            return Math.max(0, Math.min(100, number.intValue()));
        }
        try {
            return Math.max(0, Math.min(100, Integer.parseInt(String.valueOf(value))));
        } catch (Exception ex) {
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

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String truncate(String value, int limit) {
        if (value == null) {
            return "";
        }
        return value.length() <= limit ? value : value.substring(0, limit);
    }

    private record SceneContext(
            String chapterTitle,
            int chapterOrder,
            String sceneTitle,
            int sceneOrder,
            String sceneSummary,
            List<UUID> previousSceneIds
    ) {
    }
}
