package com.ainovel.app.story;

import com.ainovel.app.story.dto.CharacterRequest;
import com.ainovel.app.story.dto.StoryCreateRequest;
import com.ainovel.app.story.dto.StoryUpdateRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class StoryConceptionProjectionBuilder {
    public Projection build(StoryCreateRequest request, Map<String, Object> generated) {
        StoryUpdateRequest storyUpdate = new StoryUpdateRequest(
                asText(generated.get("title"), request.title()),
                asText(generated.get("synopsis"), request.synopsis()),
                asText(generated.get("genre"), request.genre()),
                asText(generated.get("tone"), request.tone()),
                null,
                request.worldId()
        );
        List<CharacterRequest> characters = buildCharacterRequests(generated);
        Map<String, Object> plotPlanning = buildPlotPlanningPayload(generated);
        Map<String, Object> outlineSeed = buildOutlineSeed(generated);
        generated.put("plotPlanning", plotPlanning);
        generated.put("outlineSeed", outlineSeed);
        return new Projection(storyUpdate, characters, plotPlanning, outlineSeed);
    }

    private List<CharacterRequest> buildCharacterRequests(Map<String, Object> generated) {
        List<CharacterRequest> requests = new ArrayList<>();
        List<Map<String, Object>> generatedCharacters = readObjectList(generated.get("characters"));
        if (!generatedCharacters.isEmpty()) {
            for (Map<String, Object> item : generatedCharacters) {
                String name = asText(item.get("name"), "");
                if (name.isBlank()) {
                    continue;
                }
                requests.add(new CharacterRequest(
                        name,
                        asText(item.get("synopsis"), "AI 生成角色设定"),
                        asText(item.get("details"), ""),
                        asText(item.get("relationships"), "")
                ));
            }
        }
        if (requests.isEmpty()) {
            requests.add(new CharacterRequest("主角", "AI 生成的主角设定", "初始详情", "关系网"));
        }
        return requests;
    }

    private Map<String, Object> buildPlotPlanningPayload(Map<String, Object> generated) {
        Map<String, Object> skeleton = readObjectMap(generated.get("skeleton"));
        Map<String, Object> plotPlanning = new HashMap<>();
        plotPlanning.put("corePromise", asText(skeleton.get("corePromise"), "围绕隐藏真相持续推进读者期待。"));
        plotPlanning.put("centralQuestion", asText(skeleton.get("centralQuestion"), "主角真正面对的真相到底是什么？"));
        plotPlanning.put("hiddenTruth", asText(skeleton.get("hiddenTruth"), "真正答案潜伏在角色关系或既有设定的反面。"));
        plotPlanning.put("readerMisdirect", asText(skeleton.get("readerExpectation"), "先让读者相信表层答案。"));
        plotPlanning.put("stakes", "一旦误判被拆穿，角色将不得不重写自己的目标与关系。");
        plotPlanning.put("beats", buildBeats(generated));
        plotPlanning.put("twistOptions", generated.getOrDefault("twistOptions", List.of()));
        plotPlanning.put("foreshadowPlans", buildForeshadowPlans(generated));
        plotPlanning.put("memeStrategy", buildPlotMemeStrategy(generated));
        plotPlanning.put("confidence", 0.64d);
        plotPlanning.put("warnings", List.of(
                "当前风险评估属于写前提示，不等同于真实读者识破率。",
                "若想保持惊喜感，优先在大纲阶段控制信息释放节奏。"
        ));
        return plotPlanning;
    }

    private List<Map<String, Object>> buildBeats(Map<String, Object> generated) {
        List<Map<String, Object>> chapters = readObjectList(readObjectMap(generated.get("outlineSuggestion")).get("chapters"));
        List<Map<String, Object>> beats = new ArrayList<>();
        for (int i = 0; i < chapters.size(); i++) {
            Map<String, Object> chapter = chapters.get(i);
            beats.add(Map.of(
                    "id", "beat-" + (i + 1),
                    "label", asText(chapter.get("title"), "Beat " + (i + 1)),
                    "summary", asText(chapter.get("summary"), "推进反转与误导结构。")
            ));
        }
        return beats;
    }

    private List<Map<String, Object>> buildForeshadowPlans(Map<String, Object> generated) {
        List<Map<String, Object>> foreshadowSeeds = readObjectList(generated.get("foreshadowSeeds"));
        List<Map<String, Object>> plans = new ArrayList<>();
        for (int i = 0; i < foreshadowSeeds.size(); i++) {
            Map<String, Object> seed = foreshadowSeeds.get(i);
            plans.add(Map.of(
                    "id", asText(seed.get("entryKey"), "foreshadow-" + (i + 1)),
                    "clue", asText(seed.get("setup"), "在早期场景里埋入一个异常细节。"),
                    "disguise", asText(seed.get("coverLayer"), "用表面解释掩护它。"),
                    "payoff", asText(seed.get("payoff"), "在揭示时完成回收。"),
                    "revealTiming", "中后段"
            ));
        }
        return plans;
    }

    private Map<String, Object> buildPlotMemeStrategy(Map<String, Object> generated) {
        Map<String, Object> memeStrategy = readObjectMap(generated.get("memeStrategy"));
        return Map.of(
                "reference", asText(memeStrategy.get("sourceDomain"), "圈层梗 / 亚文化暗号"),
                "purpose", asText(memeStrategy.get("useCase"), "增加角色辨识度，不破坏主线推进。"),
                "usage", asText(memeStrategy.get("naturalVersion"), "先服务角色，再服务笑点。"),
                "caution", asText(memeStrategy.get("immersionRisk"), "如果会让读者跳戏，就回退到更保守的版本。")
        );
    }

    private Map<String, Object> buildOutlineSeed(Map<String, Object> generated) {
        Map<String, Object> outlineSuggestion = readObjectMap(generated.get("outlineSuggestion"));
        Map<String, Object> planning = readObjectMap(outlineSuggestion.get("planning"));
        List<Map<String, Object>> chapters = readObjectList(outlineSuggestion.get("chapters"));
        List<Map<String, Object>> normalizedChapters = new ArrayList<>();
        for (Map<String, Object> chapter : chapters) {
            Map<String, Object> chapterPlanning = readObjectMap(chapter.get("planning"));
            List<Map<String, Object>> scenes = new ArrayList<>();
            for (Map<String, Object> scene : readObjectList(chapter.get("scenes"))) {
                Map<String, Object> scenePlanning = readObjectMap(scene.get("planning"));
                scenes.add(Map.of(
                        "title", asText(scene.get("title"), "场景"),
                        "summary", asText(scene.get("summary"), "推进剧情。"),
                        "planning", Map.of(
                                "goal", asText(scenePlanning.get("goal"), asText(chapterPlanning.get("purpose"), "推进主线")),
                                "conflict", asText(scenePlanning.get("conflict"), "让角色面对新阻力"),
                                "infoRelease", asText(scenePlanning.get("informationRelease"), asText(chapterPlanning.get("informationRelease"), "")),
                                "foreshadowId", asText(scenePlanning.get("foreshadowId"), ""),
                                "revealFor", asText(planning.get("selectedTwistId"), "twist-intuition"),
                                "memeUsage", asText(scenePlanning.get("memeUsage"), "")
                        )
                ));
            }
            normalizedChapters.add(Map.of(
                    "title", asText(chapter.get("title"), "章节"),
                    "summary", asText(chapter.get("summary"), "推进反转结构。"),
                    "planning", Map.of(
                            "revealFocus", asText(chapterPlanning.get("informationRelease"), ""),
                            "tensionShift", asText(chapterPlanning.get("purpose"), "推进误导链"),
                            "selectedTwistId", asText(planning.get("selectedTwistId"), "twist-intuition")
                    ),
                    "scenes", scenes
            ));
        }
        return Map.of(
                "title", asText(outlineSuggestion.get("title"), "剧情结构规划稿"),
                "chapters", normalizedChapters
        );
    }

    private List<Map<String, Object>> readObjectList(Object value) {
        if (!(value instanceof List<?> rawList)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> copy = new HashMap<>();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    copy.put(String.valueOf(e.getKey()), e.getValue());
                }
                out.add(copy);
            }
        }
        return out;
    }

    private Map<String, Object> readObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> copy = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            copy.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return copy;
    }

    private String asText(Object value, String fallback) {
        if (value == null) return fallback;
        String text = String.valueOf(value).trim();
        return text.isBlank() ? fallback : text;
    }

    public record Projection(
            StoryUpdateRequest storyUpdate,
            List<CharacterRequest> characterRequests,
            Map<String, Object> plotPlanning,
            Map<String, Object> outlineSeed
    ) {
    }
}
