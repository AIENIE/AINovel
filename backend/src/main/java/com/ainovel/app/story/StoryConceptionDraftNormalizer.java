package com.ainovel.app.story;

import com.ainovel.app.story.dto.StoryCreateRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class StoryConceptionDraftNormalizer {
    public Map<String, Object> normalize(StoryCreateRequest request,
                                         Map<String, Object> raw,
                                         String hintedPromise,
                                         String hintedTruth,
                                         String hintedMeme) {
        Map<String, Object> normalized = new HashMap<>();
        normalized.put("title", asText(raw.get("title"), request.title()));
        normalized.put("synopsis", asText(raw.get("synopsis"), fallbackSynopsis(request)));
        normalized.put("genre", asText(raw.get("genre"), asText(request.genre(), "未分类")));
        normalized.put("tone", asText(raw.get("tone"), asText(request.tone(), "默认")));

        List<Map<String, Object>> characters = readObjectList(raw.get("characters"));
        if (characters.isEmpty()) {
            characters = List.of(
                    Map.of(
                            "name", "主角",
                            "synopsis", "被核心秘密卷入的行动者",
                            "details", "具备强烈目标，但尚未接近真相。",
                            "relationships", "与反转真相存在隐性连接"
                    ),
                    Map.of(
                            "name", "镜像角色",
                            "synopsis", "用来映照主角误判路径的关键人物",
                            "details", "表面看似可靠，实则掌握误导线索。",
                            "relationships", "与主角形成互补或对立"
                    )
            );
        }
        normalized.put("characters", characters);

        Map<String, Object> skeleton = new HashMap<>(readObjectMap(raw.get("skeleton")));
        skeleton.putIfAbsent("corePromise", hintedPromise.isBlank() ? "围绕一个不断逼近的隐藏真相，持续制造读者误判与反转惊喜。" : hintedPromise);
        skeleton.putIfAbsent("centralQuestion", "主角真正面对的敌人或真相到底是什么？");
        skeleton.putIfAbsent("hiddenTruth", hintedTruth.isBlank() ? "最关键的真相并不在表面冲突里，而埋在角色关系或既有设定反面。" : hintedTruth);
        skeleton.putIfAbsent("readerExpectation", "读者会先相信表层线索，把真正答案当成次要噪音。");
        normalized.put("skeleton", skeleton);

        List<Map<String, Object>> twistOptions = normalizeTwistOptions(readObjectList(raw.get("twistOptions")));
        normalized.put("twistOptions", twistOptions);

        List<Map<String, Object>> foreshadowSeeds = normalizeForeshadowSeeds(readObjectList(raw.get("foreshadowSeeds")));
        normalized.put("foreshadowSeeds", foreshadowSeeds);

        Map<String, Object> memeStrategy = new HashMap<>(readObjectMap(raw.get("memeStrategy")));
        memeStrategy.putIfAbsent("sourceDomain", hintedMeme.isBlank() ? "圈层梗 / 亚文化暗号" : hintedMeme);
        memeStrategy.putIfAbsent("useCase", "用于角色互动时的轻微识别感，不破坏主线沉浸。");
        memeStrategy.putIfAbsent("naturalVersion", "让角色在符合人设的语境里顺手抛出梗，而不是单独解释梗。");
        memeStrategy.putIfAbsent("conservativeVersion", "把梗压缩成隐性彩蛋，只给熟悉领域的读者识别。");
        memeStrategy.putIfAbsent("immersionRisk", "若梗过于显眼，会提前暴露作品调性并削弱紧张感。");
        normalized.put("memeStrategy", memeStrategy);

        Map<String, Object> outlineSuggestion = normalizeOutlineSuggestion(readObjectMap(raw.get("outlineSuggestion")), twistOptions, foreshadowSeeds, memeStrategy);
        normalized.put("outlineSuggestion", outlineSuggestion);
        normalized.put("lorebookSeeds", normalizeLorebookSeeds(readObjectList(raw.get("lorebookSeeds")), foreshadowSeeds, characters));
        normalized.put("graphSeeds", normalizeGraphSeeds(readObjectList(raw.get("graphSeeds")), foreshadowSeeds, memeStrategy));
        return normalized;
    }

    private List<Map<String, Object>> normalizeTwistOptions(List<Map<String, Object>> rawOptions) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (int i = 0; i < rawOptions.size(); i++) {
            Map<String, Object> option = new HashMap<>(rawOptions.get(i));
            String defaultId = i == 0 ? "twist-intuition" : "twist-structured-" + i;
            option.put("id", asText(option.get("id"), defaultId));
            option.put("label", asText(option.get("label"), i == 0 ? "保留灵感版" : "结构更强版"));
            option.put("summary", asText(option.get("summary"), "围绕隐藏真相构建反转，并保留足够的误判空间。"));
            option.put("setupPoints", normalizeStringList(option.get("setupPoints"), List.of("在早期埋下一个看似无害的异常细节", "让角色对关键线索做出错误解释")));
            option.put("misdirectionPoints", normalizeStringList(option.get("misdirectionPoints"), List.of("把注意力引到表层冲突", "让可信角色强化错误判断")));
            option.put("revealBeat", asText(option.get("revealBeat"), "在主角以为胜利时突然翻转证据指向。"));
            option.put("revealTiming", asText(option.get("revealTiming"), i == 0 ? "中后段" : "高潮前一章"));
            option.put("earlyRevealRisk", asText(option.get("earlyRevealRisk"), "若前置线索过于集中，熟悉套路的读者可能提前猜到。"));
            option.put("payoff", asText(option.get("payoff"), "前文看似无关的细节在揭示时全部反向成立。"));
            normalized.add(option);
        }
        if (normalized.size() < 2) {
            normalized = new ArrayList<>(List.of(
                    Map.of(
                            "id", "twist-intuition",
                            "label", "保留灵感版",
                            "summary", "保留最直觉的反转路径，让惊喜来自角色关系反转。",
                            "setupPoints", List.of("让关键人物过早表现出可信度", "把异常细节包在日常行动里"),
                            "misdirectionPoints", List.of("把冲突重点放在外部敌人", "让角色的错误猜测反复得到印证"),
                            "revealBeat", "在角色自以为理解真相时翻开隐藏动机。",
                            "revealTiming", "中后段",
                            "earlyRevealRisk", "如果可信角色的异常行为写得太突出，会被提前怀疑。",
                            "payoff", "前文微小违和感在揭示时集中回收。"
                    ),
                    Map.of(
                            "id", "twist-structured",
                            "label", "结构更强版",
                            "summary", "把反转拆成更稳的误导链，降低读者过早识破概率。",
                            "setupPoints", List.of("先埋一个可双向解释的线索", "用章节目标推进掩盖真正伏笔"),
                            "misdirectionPoints", List.of("提供一个看似完美的替代答案", "让旁证共同支持错误结论"),
                            "revealBeat", "在高潮前让替代答案自行崩塌，逼出真相。",
                            "revealTiming", "高潮前一章",
                            "earlyRevealRisk", "若替代答案过弱，读者会反向猜到真相。",
                            "payoff", "既回收伏笔，又强化角色抉择的必然性。"
                    )
            ));
        }
        return normalized;
    }

    private List<Map<String, Object>> normalizeForeshadowSeeds(List<Map<String, Object>> rawSeeds) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (int i = 0; i < rawSeeds.size(); i++) {
            Map<String, Object> seed = new HashMap<>(rawSeeds.get(i));
            seed.put("entryKey", asText(seed.get("entryKey"), "foreshadow-" + (i + 1)));
            seed.put("name", asText(seed.get("name"), "伏笔 " + (i + 1)));
            seed.put("setup", asText(seed.get("setup"), "在早期场景里作为背景细节出现。"));
            seed.put("coverLayer", asText(seed.get("coverLayer"), "表面上被解释成普通事件或人物习惯。"));
            seed.put("misdirectionLayer", asText(seed.get("misdirectionLayer"), "引导读者把它理解成另一条支线。"));
            seed.put("payoff", asText(seed.get("payoff"), "在真相揭示时反向证明前文判断错误。"));
            normalized.add(seed);
        }
        if (normalized.isEmpty()) {
            normalized = new ArrayList<>(List.of(
                    Map.of(
                            "entryKey", "foreshadow-signal",
                            "name", "异常信号",
                            "setup", "主角第一次接触真相时留下一个被忽视的异常反应。",
                            "coverLayer", "看起来只是情绪波动或环境噪音。",
                            "misdirectionLayer", "读者会把它归因于另一个更明显的危机。",
                            "payoff", "在反转揭示时证明主角当时已经碰到了真相边缘。"
                    ),
                    Map.of(
                            "entryKey", "foreshadow-object",
                            "name", "关键物件",
                            "setup", "一个看似普通的物件在早期章节多次出现。",
                            "coverLayer", "被包装成角色习惯或世界观装饰。",
                            "misdirectionLayer", "读者会以为它只是人物塑造细节。",
                            "payoff", "最终揭示它其实连接着隐藏身份或真实目的。"
                    )
            ));
        }
        return normalized;
    }

    private Map<String, Object> normalizeOutlineSuggestion(Map<String, Object> rawOutline,
                                                           List<Map<String, Object>> twistOptions,
                                                           List<Map<String, Object>> foreshadowSeeds,
                                                           Map<String, Object> memeStrategy) {
        Map<String, Object> outline = new HashMap<>(rawOutline);
        outline.put("title", asText(outline.get("title"), "剧情骨架方案"));
        Map<String, Object> planning = new HashMap<>(readObjectMap(outline.get("planning")));
        planning.putIfAbsent("selectedTwistId", asText(twistOptions.get(0).get("id"), "twist-intuition"));
        planning.put("twistOptions", twistOptions);
        planning.put("foreshadowSeeds", foreshadowSeeds);
        planning.put("memeStrategy", memeStrategy);
        planning.putIfAbsent("readerExpectation", "读者会先相信表层答案，而忽略真正的因果链。");
        outline.put("planning", planning);

        List<Map<String, Object>> chapters = readObjectList(outline.get("chapters"));
        if (chapters.isEmpty()) {
            chapters = List.of(
                    Map.of(
                            "title", "第一章：误判成立",
                            "summary", "建立主角目标与表层冲突，并埋下第一个异常信号。",
                            "planning", Map.of("purpose", "建立主问题", "informationRelease", "给出表层答案", "twistRole", "setup"),
                            "scenes", List.of(
                                    Map.of("title", "开场钩子", "summary", "用一个异常但尚可解释的现象吸引读者。", "planning", Map.of("foreshadowHint", "异常信号初现", "misdirectionAction", "用更直接的危机吸走注意力", "revealTrigger", "", "payoffPlan", "后续回看时形成第一层反证")),
                                    Map.of("title", "错误解释", "summary", "角色与读者共同接受一个看似合理的解释。", "planning", Map.of("foreshadowHint", "关键物件出场", "misdirectionAction", "让可信角色强化错误答案", "revealTrigger", "", "payoffPlan", "在后段成为反转的证据"))
                            )
                    ),
                    Map.of(
                            "title", "第二章：误导加深",
                            "summary", "让错误答案持续自洽，同时增加一个更隐蔽的真相迹象。",
                            "planning", Map.of("purpose", "加深误判", "informationRelease", "扩写替代答案", "twistRole", "misdirect"),
                            "scenes", List.of(
                                    Map.of("title", "支线遮掩", "summary", "用支线目标掩盖真正伏笔的意义。", "planning", Map.of("foreshadowHint", "埋入第二层伏笔", "misdirectionAction", "让支线问题看起来更重要", "revealTrigger", "", "payoffPlan", "回收时揭示支线只是烟雾")),
                                    Map.of("title", "错误被印证", "summary", "通过多个旁证强化当前错误结论。", "planning", Map.of("foreshadowHint", "保留一处明显违和感", "misdirectionAction", "用旁证让违和感显得可忽略", "revealTrigger", "", "payoffPlan", "在高潮前拆穿旁证体系"))
                            )
                    ),
                    Map.of(
                            "title", "第三章：揭示前夜",
                            "summary", "让表层答案开始崩塌，把真相推到揭示边缘。",
                            "planning", Map.of("purpose", "为反转蓄压", "informationRelease", "推翻错误答案", "twistRole", "reveal"),
                            "scenes", List.of(
                                    Map.of("title", "答案松动", "summary", "原本最稳固的一条证据开始反向失效。", "planning", Map.of("foreshadowHint", "回收异常信号", "misdirectionAction", "让角色误以为问题出在另一个人", "revealTrigger", "关键证据失效", "payoffPlan", "为最终揭示腾出空间")),
                                    Map.of("title", "真相逼近", "summary", "在章节尾部揭开真正秘密的一角。", "planning", Map.of("foreshadowHint", "关键物件显露真实意义", "misdirectionAction", "", "revealTrigger", "主角被迫重看前文线索", "payoffPlan", "进入下一章的正式回收"))
                            )
                    )
            );
        }
        outline.put("chapters", chapters);
        return outline;
    }

    private List<Map<String, Object>> normalizeLorebookSeeds(List<Map<String, Object>> rawSeeds,
                                                             List<Map<String, Object>> foreshadowSeeds,
                                                             List<Map<String, Object>> characters) {
        if (!rawSeeds.isEmpty()) {
            return rawSeeds;
        }
        List<Map<String, Object>> seeds = new ArrayList<>();
        for (Map<String, Object> seed : foreshadowSeeds) {
            String entryKey = asText(seed.get("entryKey"), UUID.randomUUID().toString());
            seeds.add(Map.of(
                    "entryKey", entryKey,
                    "displayName", asText(seed.get("name"), "伏笔条目"),
                    "category", "concept",
                    "content", asText(seed.get("setup"), "用于后续回收的伏笔设定。"),
                    "keywords", List.of("伏笔", "结构"),
                    "insertionPosition", "before_scene",
                    "tokenBudget", 160
            ));
        }
        if (!characters.isEmpty()) {
            Map<String, Object> firstCharacter = characters.get(0);
            seeds.add(Map.of(
                    "entryKey", "character-secret",
                    "displayName", asText(firstCharacter.get("name"), "主角") + "的隐藏真相",
                    "category", "character",
                    "content", "该角色与真正秘密存在尚未公开的关联。",
                    "keywords", List.of("角色秘密", "真相"),
                    "insertionPosition", "system_prompt",
                    "tokenBudget", 180
            ));
        }
        return seeds;
    }

    private List<Map<String, Object>> normalizeGraphSeeds(List<Map<String, Object>> rawSeeds,
                                                          List<Map<String, Object>> foreshadowSeeds,
                                                          Map<String, Object> memeStrategy) {
        if (!rawSeeds.isEmpty()) {
            return rawSeeds;
        }
        List<Map<String, Object>> seeds = new ArrayList<>();
        if (!foreshadowSeeds.isEmpty()) {
            String firstKey = asText(foreshadowSeeds.get(0).get("entryKey"), "foreshadow-signal");
            seeds.add(Map.of(
                    "sourceKey", firstKey,
                    "targetKey", "character-secret",
                    "relationType", "foreshadows",
                    "label", "指向隐藏真相"
            ));
        }
        if (!memeStrategy.isEmpty()) {
            seeds.add(Map.of(
                    "sourceKey", "character-secret",
                    "targetKey", "foreshadow-object",
                    "relationType", "echoes_meme",
                    "label", "梗只作为彩蛋回声，不喧宾夺主"
            ));
        }
        return seeds;
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

    private List<String> normalizeStringList(Object value, List<String> fallback) {
        if (!(value instanceof List<?> rawList)) {
            return fallback;
        }
        List<String> normalized = new ArrayList<>();
        for (Object item : rawList) {
            String text = item == null ? "" : item.toString().trim();
            if (!text.isBlank()) {
                normalized.add(text);
            }
        }
        return normalized.isEmpty() ? fallback : normalized;
    }

    private String asText(Object value, String fallback) {
        if (value == null) return fallback;
        String text = String.valueOf(value).trim();
        return text.isBlank() ? fallback : text;
    }

    private String fallbackSynopsis(StoryCreateRequest request) {
        String synopsis = request.synopsis() == null ? "" : request.synopsis().trim();
        if (!synopsis.isBlank()) {
            return synopsis;
        }
        return "围绕一个被隐藏的真相展开，主角在不断误判中逼近更大的秘密。";
    }
}
