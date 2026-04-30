package com.ainovel.app.story;

import com.ainovel.app.common.RefineRequest;
import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.story.dto.*;
import com.ainovel.app.story.model.CharacterCard;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.story.repo.CharacterCardRepository;
import com.ainovel.app.story.repo.StoryRepository;
import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.dto.AiRefineRequest;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;

@Service
public class StoryService {
    @Autowired
    private StoryRepository storyRepository;
    @Autowired
    private CharacterCardRepository characterCardRepository;
    @Autowired
    private AiService aiService;
    @Autowired
    private ResourceAccessGuard accessGuard;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<StoryDto> listStories(User user) {
        accessGuard.assertCurrentUserEquals(user.getUsername());
        return storyRepository.findByUser(user).stream().map(this::toDto).toList();
    }

    public StoryDto getStory(UUID id) {
        Story story = storyRepository.findByIdWithUser(id).orElseThrow(() -> new RuntimeException("故事不存在"));
        accessGuard.assertOwner(story.getUser());
        return toDto(story);
    }

    @Transactional
    public StoryDto createStory(User user, StoryCreateRequest request) {
        Story story = new Story();
        story.setUser(user);
        story.setTitle(request.title());
        story.setSynopsis(request.synopsis());
        story.setGenre(request.genre());
        story.setTone(request.tone());
        story.setWorldId(request.worldId());
        story.setStatus("draft");
        storyRepository.save(story);
        return toDto(story);
    }

    @Transactional
    public StoryDto updateStory(UUID id, StoryUpdateRequest request) {
        Story story = storyRepository.findByIdWithUser(id).orElseThrow(() -> new RuntimeException("故事不存在"));
        accessGuard.assertOwner(story.getUser());
        if (request.title() != null) story.setTitle(request.title());
        if (request.synopsis() != null) story.setSynopsis(request.synopsis());
        if (request.genre() != null) story.setGenre(request.genre());
        if (request.tone() != null) story.setTone(request.tone());
        if (request.status() != null) story.setStatus(request.status());
        if (request.worldId() != null) story.setWorldId(request.worldId());
        storyRepository.save(story);
        return toDto(story);
    }

    @Transactional
    public void deleteStory(UUID id) {
        Story story = storyRepository.findByIdWithUser(id).orElseThrow(() -> new RuntimeException("故事不存在"));
        accessGuard.assertOwner(story.getUser());
        characterCardRepository.deleteAll(characterCardRepository.findByStory(story));
        storyRepository.delete(story);
    }

    public List<CharacterDto> listCharacters(UUID storyId) {
        Story story = storyRepository.findByIdWithUser(storyId).orElseThrow();
        accessGuard.assertOwner(story.getUser());
        return characterCardRepository.findByStory(story).stream().map(this::toCharacterDto).toList();
    }

    @Transactional
    public CharacterDto addCharacter(UUID storyId, CharacterRequest request) {
        Story story = storyRepository.findByIdWithUser(storyId).orElseThrow();
        accessGuard.assertOwner(story.getUser());
        CharacterCard card = new CharacterCard();
        card.setStory(story);
        card.setName(request.name());
        card.setSynopsis(request.synopsis());
        card.setDetails(request.details());
        card.setRelationships(request.relationships());
        characterCardRepository.save(card);
        return toCharacterDto(card);
    }

    @Transactional
    public CharacterDto updateCharacter(UUID id, CharacterRequest request) {
        CharacterCard card = characterCardRepository.findByIdWithStoryUser(id).orElseThrow(() -> new RuntimeException("角色不存在"));
        accessGuard.assertOwner(card.getStory().getUser());
        if (request.name() != null) card.setName(request.name());
        card.setSynopsis(request.synopsis());
        card.setDetails(request.details());
        card.setRelationships(request.relationships());
        characterCardRepository.save(card);
        return toCharacterDto(card);
    }

    @Transactional
    public void deleteCharacter(UUID id) {
        CharacterCard card = characterCardRepository.findByIdWithStoryUser(id).orElseThrow(() -> new RuntimeException("角色不存在"));
        accessGuard.assertOwner(card.getStory().getUser());
        characterCardRepository.delete(card);
    }

    public String refineStory(User user, UUID storyId, RefineRequest request) {
        Story story = storyRepository.findByIdWithUser(storyId).orElseThrow(() -> new RuntimeException("故事不存在"));
        accessGuard.assertOwner(story.getUser());
        String instruction = request.instruction() == null ? "" : request.instruction();
        return aiService.refine(user, new AiRefineRequest(request.text(), instruction, null)).result();
    }

    public String refineCharacter(User user, UUID characterId, RefineRequest request) {
        CharacterCard card = characterCardRepository.findByIdWithStoryUser(characterId).orElseThrow(() -> new RuntimeException("角色不存在"));
        accessGuard.assertOwner(card.getStory().getUser());
        String instruction = request.instruction() == null ? "" : request.instruction();
        return aiService.refine(user, new AiRefineRequest(request.text(), instruction, null)).result();
    }

    @Transactional
    public Map<String, Object> conception(User user, StoryCreateRequest request) {
        StoryDto story = createStory(user, request);

        Map<String, Object> generated = generateConceptionDraft(user, request);
        StoryDto updatedStory = story;
        if (!generated.isEmpty()) {
            updatedStory = updateStory(story.id(), new StoryUpdateRequest(
                    asText(generated.get("title"), story.title()),
                    asText(generated.get("synopsis"), request.synopsis()),
                    asText(generated.get("genre"), request.genre()),
                    asText(generated.get("tone"), request.tone()),
                    null,
                    request.worldId()
            ));
        }

        List<CharacterDto> createdCharacters = new ArrayList<>();
        List<Map<String, Object>> generatedCharacters = readObjectList(generated.get("characters"));
        if (!generatedCharacters.isEmpty()) {
            for (Map<String, Object> item : generatedCharacters) {
                String name = asText(item.get("name"), "");
                if (name.isBlank()) continue;
                createdCharacters.add(addCharacter(updatedStory.id(),
                        new CharacterRequest(
                                name,
                                asText(item.get("synopsis"), "AI 生成角色设定"),
                                asText(item.get("details"), ""),
                                asText(item.get("relationships"), "")
                        )));
            }
        }
        if (createdCharacters.isEmpty()) {
            createdCharacters.add(addCharacter(updatedStory.id(),
                    new CharacterRequest("主角", "AI 生成的主角设定", "初始详情", "关系网")));
        }

        Map<String, Object> plotPlanning = buildPlotPlanningPayload(generated);
        Map<String, Object> outlineSeed = buildOutlineSeed(generated);
        Map<String, Object> result = new HashMap<>();
        result.put("generatedAt", Instant.now().toString());
        result.put("storyCard", updatedStory);
        result.put("characterCards", createdCharacters);
        result.put("plotPlanning", plotPlanning);
        result.put("outlineSeed", outlineSeed);
        generated.put("plotPlanning", plotPlanning);
        generated.put("outlineSeed", outlineSeed);
        result.put("generated", generated);
        return result;
    }

    private Map<String, Object> generateConceptionDraft(User user, StoryCreateRequest request) {
        Map<String, Object> parsed = Map.of();
        String hintedPromise = hintText(request, "corePromise");
        String hintedTruth = hintText(request, "hiddenTruth");
        String hintedMeme = hintText(request, "memeReference");
        try {
            String prompt = """
                    你是长篇小说剧情策划编辑。请根据用户输入生成“结构骨架 + 双轨反转 + 大纲建议”，必须返回 JSON（不要 markdown），结构：
                    {
                      "title":"故事标题",
                      "synopsis":"120-200字故事梗概",
                      "genre":"类型",
                      "tone":"风格基调",
                      "skeleton":{
                        "corePromise":"这本书最吸引读者继续读下去的承诺",
                        "centralQuestion":"主问题",
                        "hiddenTruth":"真正隐藏的真相",
                        "readerExpectation":"读者会先入为主相信什么"
                      },
                      "characters":[
                        {"name":"角色名","synopsis":"一句话定位","details":"关键背景","relationships":"与其他角色关系"}
                      ],
                      "twistOptions":[
                        {
                          "id":"twist-a",
                          "label":"保留灵感版",
                          "summary":"反转方案说明",
                          "setupPoints":["前置铺垫1","前置铺垫2"],
                          "misdirectionPoints":["误导点1","误导点2"],
                          "revealBeat":"揭示瞬间",
                          "revealTiming":"第几章或后段",
                          "earlyRevealRisk":"为什么可能被提前看穿",
                          "payoff":"回收后的惊喜点"
                        },
                        {
                          "id":"twist-b",
                          "label":"结构更强版",
                          "summary":"另一条更稳的反转方案",
                          "setupPoints":["前置铺垫1","前置铺垫2"],
                          "misdirectionPoints":["误导点1","误导点2"],
                          "revealBeat":"揭示瞬间",
                          "revealTiming":"第几章或后段",
                          "earlyRevealRisk":"为什么可能被提前看穿",
                          "payoff":"回收后的惊喜点"
                        }
                      ],
                      "foreshadowSeeds":[
                        {
                          "entryKey":"seed-1",
                          "name":"伏笔名称",
                          "setup":"埋点内容",
                          "coverLayer":"表面掩护层",
                          "misdirectionLayer":"额外误导层",
                          "payoff":"后续回收点"
                        }
                      ],
                      "memeStrategy":{
                        "sourceDomain":"梗来源领域",
                        "useCase":"使用目的",
                        "naturalVersion":"自然融合版",
                        "conservativeVersion":"保守版",
                        "immersionRisk":"可能的出戏风险"
                      },
                      "outlineSuggestion":{
                        "title":"策划大纲标题",
                        "chapters":[
                          {
                            "title":"章节标题",
                            "summary":"章节摘要",
                            "planning":{"purpose":"章节作用","informationRelease":"本章释放什么信息","twistRole":"setup|misdirect|reveal|payoff"},
                            "scenes":[
                              {
                                "title":"场景标题",
                                "summary":"场景摘要",
                                "planning":{
                                  "foreshadowHint":"本场景埋什么",
                                  "misdirectionAction":"如何误导",
                                  "revealTrigger":"触发揭示的条件",
                                  "payoffPlan":"未来如何回收"
                                }
                              }
                            ]
                          }
                        ]
                      },
                      "lorebookSeeds":[
                        {"entryKey":"seed-1","displayName":"条目名","category":"concept","content":"内容","keywords":["关键词"]}
                      ],
                      "graphSeeds":[
                        {"sourceKey":"seed-1","targetKey":"truth","relationType":"foreshadows","label":"指向隐藏真相"}
                      ]
                    }
                    用户输入：
                    标题：%s
                    梗概：%s
                    类型：%s
                    基调：%s
                    核心承诺提示：%s
                    隐藏真相提示：%s
                    梗参考提示：%s
                    """.formatted(
                    safe(request.title()),
                    safe(request.synopsis()),
                    safe(request.genre()),
                    safe(request.tone()),
                    safe(hintedPromise),
                    safe(hintedTruth),
                    safe(hintedMeme)
            );
            var response = aiService.chat(user, new AiChatRequest(
                    List.of(new AiChatRequest.Message("user", prompt)),
                    null,
                    null
            ));
            Map<String, Object> candidate = parseJsonObject(response.content());
            if (candidate != null) {
                parsed = candidate;
            }
        } catch (Exception ex) {
            parsed = Map.of();
        }
        return normalizeConceptionDraft(request, parsed, hintedPromise, hintedTruth, hintedMeme);
    }

    private Map<String, Object> normalizeConceptionDraft(StoryCreateRequest request,
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

    private String hintText(StoryCreateRequest request, String key) {
        if (request == null || request.plotPlanningHints() == null) {
            return "";
        }
        Object value = request.plotPlanningHints().get(key);
        return value == null ? "" : String.valueOf(value).trim();
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

    private Map<String, Object> parseJsonObject(String text) {
        if (text == null || text.isBlank()) return null;
        String candidate = text.trim();
        if (candidate.startsWith("```")) {
            int first = candidate.indexOf('{');
            int last = candidate.lastIndexOf('}');
            if (first >= 0 && last > first) {
                candidate = candidate.substring(first, last + 1);
            }
        }
        try {
            return objectMapper.readValue(candidate, new TypeReference<>() {});
        } catch (Exception ignored) {
            int first = candidate.indexOf('{');
            int last = candidate.lastIndexOf('}');
            if (first >= 0 && last > first) {
                try {
                    return objectMapper.readValue(candidate.substring(first, last + 1), new TypeReference<>() {});
                } catch (Exception ignoredAgain) {
                    return null;
                }
            }
            return null;
        }
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

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String fallbackSynopsis(StoryCreateRequest request) {
        String synopsis = safe(request.synopsis());
        if (!synopsis.isBlank()) {
            return synopsis;
        }
        return "围绕一个被隐藏的真相展开，主角在不断误判中逼近更大的秘密。";
    }

    private StoryDto toDto(Story story) {
        return new StoryDto(story.getId(), story.getTitle(), story.getSynopsis(), story.getGenre(), story.getTone(), story.getStatus(), story.getWorldId(), story.getUpdatedAt());
    }

    private CharacterDto toCharacterDto(CharacterCard card) {
        return new CharacterDto(card.getId(), card.getName(), card.getSynopsis(), card.getDetails(), card.getRelationships(), card.getUpdatedAt());
    }
}
