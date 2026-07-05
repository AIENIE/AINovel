package com.ainovel.app.quality;

import com.ainovel.app.common.BusinessException;
import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.common.JsonColumnCodec;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.quality.model.SlopDriftRun;
import com.ainovel.app.quality.repo.SlopDriftRunRepository;
import com.ainovel.app.story.model.Outline;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class SlopDriftService {
    private static final int MIN_TOTAL_CHARACTERS = 6000;
    private static final int MIN_WINDOWS = 3;
    private static final int MIN_WINDOW_CHARACTERS = 3000;
    private static final int MAX_WINDOW_CHARACTERS = 7000;
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    private final AiService aiService;
    private final ObjectMapper objectMapper;
    private final SlopDriftRunRepository runRepository;
    private final JsonColumnCodec jsonColumnCodec;

    public SlopDriftService(AiService aiService,
                            ObjectMapper objectMapper,
                            SlopDriftRunRepository runRepository,
                            JsonColumnCodec jsonColumnCodec) {
        this.aiService = aiService;
        this.objectMapper = objectMapper;
        this.runRepository = runRepository;
        this.jsonColumnCodec = jsonColumnCodec;
    }

    public List<SlopDriftRun> listRuns(UUID manuscriptId) {
        return runRepository.findTop20ByManuscriptIdOrderByCreatedAtDesc(manuscriptId);
    }

    @Transactional
    public SlopDriftRun analyze(User user, Manuscript manuscript) {
        DriftInput input = buildInput(manuscript);
        List<WindowSample> windows = buildWindows(input.fullText());
        if (input.totalCharacters() < MIN_TOTAL_CHARACTERS || windows.size() < MIN_WINDOWS) {
            return runRepository.save(insufficientRun(input, windows));
        }
        try {
            Map<String, Object> root = parseJson(aiService.chat(user, new AiChatRequest(
                    List.of(new AiChatRequest.Message("user", buildPrompt(input, windows))),
                    null,
                    null
            )).content());
            return runRepository.save(completedRun(input, windows, root));
        } catch (RuntimeException ex) {
            return runRepository.save(degradedRun(input, windows, ex));
        }
    }

    private DriftInput buildInput(Manuscript manuscript) {
        Outline outline = manuscript.getOutline();
        Story story = outline.getStory();
        Map<String, String> sections = readSectionMap(manuscript.getSectionsJson());
        List<OrderedScene> scenes = orderedScenes(outline);
        StringBuilder fullText = new StringBuilder();
        int totalCharacters = 0;
        for (OrderedScene scene : scenes) {
            String text = stripHtml(sections.get(scene.sceneId().toString()));
            if (text.isBlank()) {
                continue;
            }
            if (!fullText.isEmpty()) {
                fullText.append("\n\n");
            }
            fullText.append("第").append(scene.chapterOrder()).append("章《").append(scene.chapterTitle()).append("》 ")
                    .append("第").append(scene.sceneOrder()).append("节《").append(scene.sceneTitle()).append("》\n")
                    .append(text);
            totalCharacters += text.length();
        }
        return new DriftInput(
                story.getId(),
                manuscript.getId(),
                safe(story.getTitle(), "未命名故事"),
                safe(story.getGenre(), "未指定"),
                safe(story.getTone(), "沉浸、连贯"),
                totalCharacters,
                fullText.toString(),
                hash(fullText.toString())
        );
    }

    private List<OrderedScene> orderedScenes(Outline outline) {
        Map<String, Object> root = readObjectMap(outline.getContentJson());
        List<Map<String, Object>> chapters = listOfMap(root.get("chapters"));
        List<OrderedScene> scenes = new ArrayList<>();
        for (int chapterIndex = 0; chapterIndex < chapters.size(); chapterIndex++) {
            Map<String, Object> chapter = chapters.get(chapterIndex);
            int chapterOrder = intVal(chapter.get("order"), chapterIndex + 1);
            String chapterTitle = safe(str(chapter.get("title"), ""), "未命名章节");
            List<Map<String, Object>> sceneList = listOfMap(chapter.get("scenes"));
            for (int sceneIndex = 0; sceneIndex < sceneList.size(); sceneIndex++) {
                Map<String, Object> scene = sceneList.get(sceneIndex);
                UUID sceneId = uuid(scene.get("id"));
                if (sceneId == null) {
                    continue;
                }
                scenes.add(new OrderedScene(
                        sceneId,
                        chapterTitle,
                        chapterOrder,
                        safe(str(scene.get("title"), ""), "未命名场景"),
                        intVal(scene.get("order"), sceneIndex + 1)
                ));
            }
        }
        scenes.sort(Comparator.comparingInt(OrderedScene::chapterOrder).thenComparingInt(OrderedScene::sceneOrder));
        return scenes;
    }

    private List<WindowSample> buildWindows(String fullText) {
        int total = fullText.length();
        if (total == 0) {
            return List.of();
        }
        int windowSize = Math.min(MAX_WINDOW_CHARACTERS, Math.max(MIN_WINDOW_CHARACTERS, total / 3));
        Map<String, WindowSample> windows = new LinkedHashMap<>();
        addWindow(windows, "opening", fullText, 0, windowSize);
        addWindow(windows, "mid", fullText, Math.max(0, total / 2 - windowSize / 2), windowSize);
        if (total >= 100_000) {
            addWindow(windows, "100k", fullText, 100_000 - windowSize / 2, windowSize);
        }
        if (total >= 200_000) {
            addWindow(windows, "200k", fullText, 200_000 - windowSize / 2, windowSize);
        }
        addWindow(windows, "latest", fullText, Math.max(0, total - windowSize), windowSize);
        return new ArrayList<>(windows.values());
    }

    private void addWindow(Map<String, WindowSample> windows, String label, String fullText, int requestedStart, int windowSize) {
        int start = Math.max(0, Math.min(requestedStart, Math.max(0, fullText.length() - windowSize)));
        int end = Math.min(fullText.length(), start + windowSize);
        if (end <= start) {
            return;
        }
        windows.put(label, new WindowSample(label, start, end, fullText.substring(start, end)));
    }

    private SlopDriftRun insufficientRun(DriftInput input, List<WindowSample> windows) {
        SlopDriftRun run = baseRun(input, windows);
        run.setStatus(SlopDriftStatus.INSUFFICIENT_TEXT);
        run.setOverallRiskScore(0);
        run.setRiskLabel("unavailable");
        run.setSafeClaim("正文窗口不足，无法判断长篇 drift；这不能证明作者使用 AI。");
        run.setSummary("正文不足，长篇 drift 巡检需要至少 6000 字且形成 3 个有效窗口。");
        run.setMetricCurvesJson("{}");
        run.setDriftPointsJson("[]");
        run.setEvidenceItemsJson("[]");
        run.setAlternativeExplanationsJson(writeJson(defaultAlternatives()));
        run.setRewriteTasksJson("[]");
        return run;
    }

    private SlopDriftRun completedRun(DriftInput input, List<WindowSample> windows, Map<String, Object> root) {
        Map<String, Object> overall = map(root.get("overall"));
        int risk = intVal(overall.get("midbook_drift_risk_score"), intVal(root.get("risk_score"), 0));
        String riskLabel = str(overall.get("risk_label"), label(risk));
        String safeClaim = str(overall.get("safe_claim"),
                "该稿件呈现%s级长篇 drift 风险；这不能证明作者使用 AI。".formatted(riskLabel));

        SlopDriftRun run = baseRun(input, windows);
        run.setStatus(SlopDriftStatus.COMPLETED);
        run.setOverallRiskScore(risk);
        run.setRiskLabel(riskLabel);
        run.setSafeClaim(truncate(safeClaim, 500));
        run.setSummary(truncate(str(root.get("summary"), safeClaim), 800));
        run.setWindowSummariesJson(writeJson(root.getOrDefault("window_summaries", windowMetadata(windows))));
        run.setMetricCurvesJson(writeJson(root.getOrDefault("metric_curves", Map.of())));
        run.setDriftPointsJson(writeJson(root.getOrDefault("drift_points", List.of())));
        run.setEvidenceItemsJson(writeJson(root.getOrDefault("evidence_items", List.of())));
        run.setAlternativeExplanationsJson(writeJson(root.getOrDefault("alternative_explanations", defaultAlternatives())));
        run.setRewriteTasksJson(writeJson(root.getOrDefault("rewrite_tasks", List.of())));
        return run;
    }

    private SlopDriftRun degradedRun(DriftInput input, List<WindowSample> windows, RuntimeException ex) {
        SlopDriftRun run = baseRun(input, windows);
        run.setStatus(SlopDriftStatus.DEGRADED);
        run.setOverallRiskScore(45);
        run.setRiskLabel("medium");
        run.setSafeClaim("长篇 drift 语义巡检失败，已降级记录；这不能证明作者使用 AI。");
        run.setSummary("长篇 drift 巡检失败，已降级记录：" + truncate(ex.getMessage(), 160));
        run.setMetricCurvesJson("{}");
        run.setDriftPointsJson("[]");
        run.setEvidenceItemsJson("[]");
        run.setAlternativeExplanationsJson(writeJson(defaultAlternatives()));
        run.setRewriteTasksJson("[]");
        return run;
    }

    private SlopDriftRun baseRun(DriftInput input, List<WindowSample> windows) {
        SlopDriftRun run = new SlopDriftRun();
        run.setStoryId(input.storyId());
        run.setManuscriptId(input.manuscriptId());
        run.setTotalCharacters(input.totalCharacters());
        run.setWindowCount(windows.size());
        run.setSourceTextHash(input.sourceTextHash());
        run.setWindowSummariesJson(writeJson(windowMetadata(windows)));
        return run;
    }

    private String buildPrompt(DriftInput input, List<WindowSample> windows) {
        return """
                你是中文长篇小说 drift 巡检编辑。请比较多个章节/字数窗口，诊断是否存在中后段文风或叙事机制断层风险。

                安全边界：
                - 只评价文本呈现出的模板化、角色漂移、事件传送带、伏笔遗忘和叙事机制断层风险。
                - 禁止输出 AI 概率，禁止说作者从某章开始使用 AI。
                - E1-E3 判断必须给出替代解释，例如赶稿、换写手、剧情高潮、平台节奏、作者疲劳、题材惯例。

                输出 JSON：
                {
                  "summary": "一句话总结",
                  "overall": {
                    "midbook_drift_risk_score": 0-100,
                    "risk_label": "low|medium|high|critical",
                    "safe_claim": "该稿件在某阶段呈现...风险；这不能证明作者使用AI。"
                  },
                  "window_summaries": [{"label": "opening", "summary": "窗口观察"}],
                  "metric_curves": {
                    "template_density": [{"window": "opening", "score": 0}],
                    "causal_coherence": [{"window": "opening", "score": 0}],
                    "role_stability": [{"window": "opening", "score": 0}],
                    "foreshadow_memory": [{"window": "opening", "score": 0}],
                    "breath_score": [{"window": "opening", "score": 0}]
                  },
                  "drift_points": [
                    {
                      "from_window": "opening",
                      "to_window": "latest",
                      "changed_metrics": [],
                      "interpretation": "变化解释",
                      "safe_claim": "文本出现文风和叙事机制断层风险。"
                    }
                  ],
                  "evidence_items": [
                    {
                      "window": "latest",
                      "quote": "原文证据",
                      "module": "surface_template|voice_fit|consistency_assimilation|breath_focus_pacing|human_trace",
                      "evidence_level": "E1|E2|E3|E4",
                      "risk_score": 0-100,
                      "risk_explanation": "为什么影响阅读",
                      "repair_hint": "最小修复建议"
                    }
                  ],
                  "alternative_explanations": [],
                  "rewrite_tasks": []
                }

                故事：%s
                类型：%s
                文风目标：%s
                正文总字符数：%d

                窗口：
                %s
                """.formatted(
                safe(input.storyTitle(), "未命名故事"),
                safe(input.genre(), "未指定"),
                safe(input.tone(), "沉浸、连贯"),
                input.totalCharacters(),
                writeJson(windowsForPrompt(windows))
        );
    }

    private List<Map<String, Object>> windowsForPrompt(List<WindowSample> windows) {
        return windows.stream()
                .map(window -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("label", window.label());
                    item.put("charStart", window.charStart());
                    item.put("charEnd", window.charEnd());
                    item.put("text", window.text());
                    return item;
                })
                .toList();
    }

    private List<Map<String, Object>> windowMetadata(List<WindowSample> windows) {
        return windows.stream()
                .map(window -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("label", window.label());
                    item.put("charStart", window.charStart());
                    item.put("charEnd", window.charEnd());
                    item.put("charCount", window.text().length());
                    return item;
                })
                .toList();
    }

    private Map<String, Object> parseJson(String raw) {
        String text = raw == null ? "" : raw.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            text = text.substring(start, end + 1);
        }
        try {
            return objectMapper.readValue(text, new TypeReference<>() {});
        } catch (Exception ex) {
            throw new RuntimeException("长篇 drift 诊断 JSON 解析失败", ex);
        }
    }

    private Map<String, String> readSectionMap(String json) {
        return jsonColumnCodec.read(json, new TypeReference<>() {}, Map.of());
    }

    private Map<String, Object> readObjectMap(String json) {
        return jsonColumnCodec.read(json, new TypeReference<>() {}, Map.of());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMap(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                out.add(new LinkedHashMap<>((Map<String, Object>) map));
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> raw ? (Map<String, Object>) raw : Map.of();
    }

    private String writeJson(Object value) {
        return jsonColumnCodec.write(value, "[]");
    }

    private List<String> defaultAlternatives() {
        return List.of("赶稿", "换写手", "剧情高潮段落", "平台节奏压力", "作者疲劳", "题材/平台惯例");
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

    private int intVal(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ex) {
            return fallback;
        }
    }

    private String str(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = value.toString().trim();
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

    private String label(int risk) {
        if (risk >= 85) {
            return "critical";
        }
        if (risk >= 70) {
            return "high";
        }
        if (risk >= 40) {
            return "medium";
        }
        return "low";
    }

    private String hash(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : encoded) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            return "";
        }
    }

    private record DriftInput(
            UUID storyId,
            UUID manuscriptId,
            String storyTitle,
            String genre,
            String tone,
            int totalCharacters,
            String fullText,
            String sourceTextHash
    ) {
    }

    private record OrderedScene(
            UUID sceneId,
            String chapterTitle,
            int chapterOrder,
            String sceneTitle,
            int sceneOrder
    ) {
    }

    private record WindowSample(
            String label,
            int charStart,
            int charEnd,
            String text
    ) {
    }
}
