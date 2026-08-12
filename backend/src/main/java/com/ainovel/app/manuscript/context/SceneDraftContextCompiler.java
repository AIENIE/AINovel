package com.ainovel.app.manuscript.context;

import com.ainovel.app.common.BusinessException;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.story.model.Outline;
import com.ainovel.app.story.model.CharacterCard;
import com.ainovel.app.story.model.SceneType;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.story.repo.CharacterCardRepository;
import com.ainovel.app.style.model.CharacterVoice;
import com.ainovel.app.style.model.StyleProfile;
import com.ainovel.app.style.repo.CharacterVoiceRepository;
import com.ainovel.app.style.repo.StyleProfileRepository;
import com.ainovel.app.v2.V2ContextPersistenceService;
import com.ainovel.app.world.model.World;
import com.ainovel.app.world.repo.WorldRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SceneDraftContextCompiler {
    public static final String COMPILER_VERSION = "scene-draft-v2";
    public static final int DEFAULT_TOKEN_BUDGET = 3500;
    public static final int MAX_TOKEN_BUDGET = 3500;
    public static final int MAX_HISTORICAL_ANCHORS = 2;

    private static final int RECENT_SCENE_COUNT = 2;
    private static final int MAX_LOREBOOK_ENTRIES = 4;
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern WORD_PATTERN = Pattern.compile("[\\p{IsHan}]{2,}|[a-z0-9_]{2,}");
    private static final Set<String> STOP_TERMS = Set.of(
            "一个", "这个", "那个", "当前", "场景", "章节", "故事", "本节", "人物", "角色", "目标", "信息", "冲突"
    );

    private final ObjectMapper objectMapper;
    private final WorldRepository worldRepository;
    private final CharacterCardRepository characterCardRepository;
    private final StyleProfileRepository styleProfileRepository;
    private final CharacterVoiceRepository characterVoiceRepository;
    private final V2ContextPersistenceService contextPersistenceService;

    public SceneDraftContextCompiler(ObjectMapper objectMapper,
                                     WorldRepository worldRepository,
                                     CharacterCardRepository characterCardRepository,
                                     StyleProfileRepository styleProfileRepository,
                                     CharacterVoiceRepository characterVoiceRepository,
                                     V2ContextPersistenceService contextPersistenceService) {
        this.objectMapper = objectMapper;
        this.worldRepository = worldRepository;
        this.characterCardRepository = characterCardRepository;
        this.styleProfileRepository = styleProfileRepository;
        this.characterVoiceRepository = characterVoiceRepository;
        this.contextPersistenceService = contextPersistenceService;
    }

    @Transactional(readOnly = true)
    public CompiledSceneDraftContext compile(Manuscript manuscript,
                                             UUID sceneId,
                                             Map<String, String> existingSections) {
        return compile(manuscript, sceneId, existingSections, DEFAULT_TOKEN_BUDGET);
    }

    @Transactional(readOnly = true)
    public CompiledSceneDraftContext compile(Manuscript manuscript,
                                             UUID sceneId,
                                             int requestedTokenBudget) {
        return compile(manuscript, sceneId, readSections(manuscript), requestedTokenBudget);
    }

    @Transactional(readOnly = true)
    public CompiledSceneDraftContext compile(Manuscript manuscript,
                                             UUID sceneId,
                                             Map<String, String> existingSections,
                                             int requestedTokenBudget) {
        RequiredContext required = requireContext(manuscript, sceneId);
        int tokenBudget = normalizeBudget(requestedTokenBudget);
        Map<String, String> sections = existingSections == null ? Map.of() : existingSections;
        List<String> warnings = new ArrayList<>();
        if (requestedTokenBudget > MAX_TOKEN_BUDGET) {
            warnings.add("requested token budget was capped at " + MAX_TOKEN_BUDGET);
        }

        Object explicitSceneType = required.position().scene().planning().get("sceneType");
        String sceneType = SceneType.parse(explicitSceneType).map(SceneType::value).orElse("");
        if (explicitSceneType != null && !explicitSceneType.toString().isBlank() && sceneType.isBlank()) {
            warnings.add("ignored unsupported sceneType: " + explicitSceneType);
        }
        String baseQueryText = queryText(required, sceneType);
        String queryText = baseQueryText + " " + relevantCharacterNames(required.story(), baseQueryText);
        Set<String> queryTerms = queryTerms(queryText);
        List<Candidate> candidates = new ArrayList<>();

        addPlanningCandidates(candidates, required);
        UUID boundWorldId = addWorldCandidate(candidates, required, warnings);
        addStyleCandidates(candidates, required.story(), sceneType, queryText);

        List<SceneSlot> recentScenes = recentScenes(required.position().priorScenes(), sections);
        addRecentSceneCandidates(candidates, recentScenes, sections);

        LorebookSelection lorebookSelection = selectLorebook(required.story().getId(), queryText, queryTerms);
        addLorebookCandidates(candidates, lorebookSelection.entries());
        addGraphCandidate(candidates, lorebookSelection);

        Set<String> anchorTerms = new LinkedHashSet<>(queryTerms);
        anchorTerms.addAll(lorebookAnchorTerms(lorebookSelection.entries()));

        List<SceneSlot> anchors = historicalAnchors(
                required.position().priorScenes(), recentScenes, sections, anchorTerms
        );
        addAnchorCandidates(candidates, anchors, sections, anchorTerms);

        RenderedContext rendered = render(candidates, tokenBudget);
        Set<String> includedLorebookIds = sourceIds(rendered.sources(), "lorebook");
        List<Map<String, Object>> includedLorebook = lorebookSelection.entries().stream()
                .filter(entry -> includedLorebookIds.contains(text(entry.get("id"))))
                .<Map<String, Object>>map(LinkedHashMap::new)
                .toList();
        List<String> includedRelations = filterRelations(lorebookSelection, includedLorebookIds);
        List<String> activeCharacters = activeCharacters(includedLorebook, rendered.sources());
        String recentContext = rendered.fragmentsBySlot().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("recent."))
                .map(Map.Entry::getValue)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("暂无可用前文。");

        String contextHash = sha256(COMPILER_VERSION + "\n" + tokenBudget + "\n" + rendered.content());
        SceneDraftContextManifest manifest = new SceneDraftContextManifest(
                COMPILER_VERSION,
                contextHash,
                "SHA-256",
                tokenBudget,
                estimateTokens(rendered.content()),
                required.story().getId(),
                required.outline().getId(),
                manuscript.getId(),
                sceneId,
                boundWorldId,
                required.position().chapter().order(),
                required.position().scene().order(),
                sceneType,
                rendered.sources(),
                warnings
        );
        return new CompiledSceneDraftContext(
                rendered.content(),
                manifest,
                recentContext,
                includedLorebook,
                includedRelations,
                activeCharacters
        );
    }

    private RequiredContext requireContext(Manuscript manuscript, UUID sceneId) {
        if (manuscript == null || manuscript.getOutline() == null || manuscript.getOutline().getStory() == null) {
            throw new BusinessException("稿件缺少故事或大纲，无法编译场景上下文");
        }
        if (sceneId == null) {
            throw new BusinessException("sceneId 不能为空");
        }
        Outline outline = manuscript.getOutline();
        return new RequiredContext(manuscript, outline, outline.getStory(), parseOutline(outline, sceneId));
    }

    private OutlinePosition parseOutline(Outline outline, UUID targetSceneId) {
        try {
            Map<String, Object> root = objectMapper.readValue(
                    Optional.ofNullable(outline.getContentJson()).filter(value -> !value.isBlank())
                            .orElse("{\"planning\":{},\"chapters\":[]}"),
                    new TypeReference<>() {
                    }
            );
            Map<String, Object> overallPlanning = map(root.get("planning"));
            List<Map<String, Object>> rawChapters = maps(root.get("chapters"));
            List<IndexedMap> chapters = indexed(rawChapters);
            chapters.sort(Comparator.comparingInt(item -> intValue(item.value().get("order"), item.index() + 1)));

            List<SceneSlot> flattened = new ArrayList<>();
            ChapterSlot targetChapter = null;
            SceneSlot targetScene = null;
            for (IndexedMap indexedChapter : chapters) {
                Map<String, Object> chapterMap = indexedChapter.value();
                ChapterSlot chapter = new ChapterSlot(
                        uuid(chapterMap.get("id")),
                        firstNonBlank(text(chapterMap.get("title")), "未命名章节"),
                        text(chapterMap.get("summary")),
                        intValue(chapterMap.get("order"), indexedChapter.index() + 1),
                        map(chapterMap.get("planning"))
                );
                List<IndexedMap> scenes = indexed(maps(chapterMap.get("scenes")));
                scenes.sort(Comparator.comparingInt(item -> intValue(item.value().get("order"), item.index() + 1)));
                for (IndexedMap indexedScene : scenes) {
                    Map<String, Object> sceneMap = indexedScene.value();
                    SceneSlot scene = new SceneSlot(
                            uuid(sceneMap.get("id")),
                            firstNonBlank(text(sceneMap.get("title")), "未命名场景"),
                            text(sceneMap.get("summary")),
                            intValue(sceneMap.get("order"), indexedScene.index() + 1),
                            map(sceneMap.get("planning")),
                            chapter
                    );
                    if (targetSceneId.equals(scene.id())) {
                        targetChapter = chapter;
                        targetScene = scene;
                    }
                    flattened.add(scene);
                }
            }
            if (targetScene == null || targetChapter == null) {
                throw new BusinessException("场景不存在，无法编译上下文");
            }
            int targetIndex = flattened.indexOf(targetScene);
            return new OutlinePosition(
                    overallPlanning,
                    targetChapter,
                    targetScene,
                    List.copyOf(flattened.subList(0, targetIndex))
            );
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("大纲内容异常，无法编译场景上下文", ex);
        }
    }

    private void addPlanningCandidates(List<Candidate> candidates, RequiredContext required) {
        OutlinePosition position = required.position();
        addJsonCandidate(candidates, "planning.scene", "scene_planning", position.scene().id(),
                "当前场景规划", 1000, 240, position.scene().planning());
        addJsonCandidate(candidates, "planning.chapter", "chapter_planning", position.chapter().id(),
                "当前章节规划", 990, 170, position.chapter().planning());
        addJsonCandidate(candidates, "planning.overall", "overall_planning", required.outline().getId(),
                "全书规划", 980, 180, position.overallPlanning());
    }

    private UUID addWorldCandidate(List<Candidate> candidates, RequiredContext required, List<String> warnings) {
        List<String> bindings = List.of(
                text(required.manuscript().getWorldId()),
                text(required.outline().getWorldId()),
                text(required.story().getWorldId())
        );
        for (String binding : bindings) {
            if (binding.isBlank()) {
                continue;
            }
            UUID worldId;
            try {
                worldId = UUID.fromString(binding);
            } catch (IllegalArgumentException ex) {
                warnings.add("ignored invalid world binding: " + binding);
                continue;
            }
            Optional<World> found = worldRepository.findById(worldId)
                    .filter(world -> sameOwner(required.story(), world));
            if (found.isEmpty()) {
                warnings.add("bound world was unavailable: " + worldId);
                continue;
            }
            World world = found.get();
            String content = "名称=" + firstNonBlank(world.getName(), "未命名世界")
                    + "\n创作意图=" + text(world.getCreativeIntent())
                    + "\n主题=" + canonicalJsonText(world.getThemesJson())
                    + "\n模块=" + canonicalJsonText(world.getModulesJson())
                    + "\n备注=" + text(world.getNotes());
            candidates.add(new Candidate("world", "world", worldId.toString(), "绑定世界", 940, 340, content));
            return worldId;
        }
        return null;
    }

    private void addStyleCandidates(List<Candidate> candidates, Story story, String sceneType, String queryText) {
        Optional<StyleProfile> active = styleProfileRepository.findFirstByStoryAndActiveTrue(story);
        if (active.isPresent()) {
            StyleProfile profile = active.get();
            String profileText = "画像=" + firstNonBlank(profile.getName(), "未命名画像")
                    + "\n类型=" + firstNonBlank(profile.getProfileType(), "global")
                    + "\n维度=" + canonicalJsonText(profile.getDimensionsJson())
                    + "\n分析=" + canonicalJsonText(profile.getAiAnalysisJson())
                    + "\n样文=" + text(profile.getSampleText());
            candidates.add(new Candidate("style.profile", "style_profile", id(profile.getId()),
                    "激活风格画像", 700, 220, profileText));
            if (!sceneType.isBlank()) {
                profile.getSceneOverrides().stream()
                        .filter(override -> sceneType.equalsIgnoreCase(text(override.getSceneType())))
                        .sorted(Comparator.comparing(override -> id(override.getId())))
                        .findFirst()
                        .ifPresent(override -> candidates.add(new Candidate(
                                "style.override",
                                "style_scene_override",
                                id(override.getId()),
                                "场景类型覆盖 · " + sceneType,
                                690,
                                110,
                                canonicalJsonText(override.getOverrideJson())
                        )));
            }
        }

        List<CharacterVoice> voices = characterVoiceRepository.findByStoryOrderByCreatedAtDesc(story);
        List<CharacterVoice> relevant = voices.stream()
                .filter(voice -> voice.getCharacterCard() != null
                        && containsIgnoreCase(queryText, voice.getCharacterCard().getName()))
                .sorted(Comparator
                        .comparing((CharacterVoice voice) -> text(voice.getCharacterCard().getName()).toLowerCase(Locale.ROOT))
                        .thenComparing(voice -> id(voice.getId())))
                .limit(2)
                .toList();
        int index = 0;
        for (CharacterVoice voice : relevant) {
            String characterName = voice.getCharacterCard() == null
                    ? "未命名角色"
                    : firstNonBlank(voice.getCharacterCard().getName(), "未命名角色");
            String voiceText = "角色=" + characterName
                    + "\n说话模式=" + text(voice.getSpeechPattern())
                    + "\n词汇层级=" + text(voice.getVocabularyLevel())
                    + "\n方言=" + text(voice.getDialect())
                    + "\n口头语=" + canonicalJsonText(voice.getCatchphrasesJson())
                    + "\n情绪范围=" + canonicalJsonText(voice.getEmotionalRangeJson())
                    + "\n对白样例=" + canonicalJsonText(voice.getSampleDialoguesJson());
            candidates.add(new Candidate("voice." + index++, "character_voice", id(voice.getId()),
                    "角色声音 · " + characterName, 680, 115, voiceText));
        }
    }

    private List<SceneSlot> recentScenes(List<SceneSlot> priorScenes, Map<String, String> sections) {
        List<SceneSlot> recent = new ArrayList<>();
        for (int i = priorScenes.size() - 1; i >= 0 && recent.size() < RECENT_SCENE_COUNT; i--) {
            SceneSlot scene = priorScenes.get(i);
            if (!plainSection(sections.get(id(scene.id()))).isBlank()) {
                recent.add(scene);
            }
        }
        Collections.reverse(recent);
        return recent;
    }

    private void addRecentSceneCandidates(List<Candidate> candidates,
                                          List<SceneSlot> recentScenes,
                                          Map<String, String> sections) {
        for (int i = 0; i < recentScenes.size(); i++) {
            SceneSlot scene = recentScenes.get(i);
            String tail = tail(plainSection(sections.get(id(scene.id()))), 330);
            candidates.add(new Candidate(
                    "recent." + i,
                    "recent_scene",
                    id(scene.id()),
                    "最近正文 · 第" + scene.chapter().order() + "章/第" + scene.order() + "节",
                    800 + i,
                    380,
                    scene.chapter().title() + " / " + scene.title() + "\n" + tail
            ));
        }
    }

    private LorebookSelection selectLorebook(UUID storyId, String queryText, Set<String> queryTerms) {
        List<Map<String, Object>> enabled = contextPersistenceService.listLorebook(storyId).stream()
                .filter(entry -> boolValue(entry.get("enabled"), true))
                .<Map<String, Object>>map(LinkedHashMap::new)
                .toList();
        List<Map<String, Object>> relationships = contextPersistenceService.listRelationships(storyId).stream()
                .<Map<String, Object>>map(LinkedHashMap::new)
                .sorted(relationshipComparator())
                .toList();
        Map<String, Integer> relevance = new HashMap<>();
        for (Map<String, Object> entry : enabled) {
            int semantic = lorebookRelevance(entry, queryText, queryTerms);
            if (semantic > 0) {
                relevance.put(text(entry.get("id")), semantic * 1000 + intValue(entry.get("priority"), 0));
            }
        }
        List<Map<String, Object>> selected = enabled.stream()
                .filter(entry -> relevance.containsKey(text(entry.get("id"))))
                .sorted(Comparator
                        .comparingInt((Map<String, Object> entry) -> relevance.get(text(entry.get("id")))).reversed()
                        .thenComparing(
                                (Map<String, Object> entry) -> text(entry.get("updatedAt")),
                                Comparator.reverseOrder()
                        )
                        .thenComparing(entry -> text(entry.get("id"))))
                .limit(MAX_LOREBOOK_ENTRIES)
                .toList();
        return new LorebookSelection(selected, relationships);
    }

    private int lorebookRelevance(Map<String, Object> entry, String queryText, Set<String> queryTerms) {
        String displayName = text(entry.get("displayName"));
        String content = text(entry.get("content"));
        int score = 0;
        if (containsIgnoreCase(queryText, displayName)) {
            score += 100;
        }
        for (Object keyword : list(entry.get("keywords"))) {
            String value = text(keyword);
            if (!value.isBlank() && containsIgnoreCase(queryText, value)) {
                score += 40;
            }
        }
        String haystack = (displayName + " " + content).toLowerCase(Locale.ROOT);
        int overlaps = 0;
        for (String term : queryTerms) {
            if (haystack.contains(term) && overlaps++ < 20) {
                score += 2;
            }
        }
        return score;
    }

    private void addLorebookCandidates(List<Candidate> candidates, List<Map<String, Object>> entries) {
        int index = 0;
        for (Map<String, Object> entry : entries) {
            String label = firstNonBlank(text(entry.get("displayName")), "未命名条目");
            String content = "类别=" + firstNonBlank(text(entry.get("category")), "custom")
                    + "\n插入位置=" + firstNonBlank(text(entry.get("insertionPosition")), "before_scene")
                    + "\n内容=" + text(entry.get("content"));
            int entryLimit = Math.min(140, Math.max(60, intValue(entry.get("tokenBudget"), 140)));
            candidates.add(new Candidate("lorebook." + index++, "lorebook", text(entry.get("id")),
                    "相关设定 · " + label, 920, entryLimit, content));
        }
    }

    private void addGraphCandidate(List<Candidate> candidates, LorebookSelection selection) {
        Set<String> ids = selection.entries().stream().map(entry -> text(entry.get("id"))).collect(
                LinkedHashSet::new, Set::add, Set::addAll
        );
        List<String> relations = relationLabels(selection, ids);
        if (!relations.isEmpty()) {
            candidates.add(new Candidate("lorebook.graph", "lorebook_graph", "story-graph",
                    "相关图谱关系", 910, 120, String.join("\n", relations)));
        }
    }

    private List<SceneSlot> historicalAnchors(List<SceneSlot> priorScenes,
                                              List<SceneSlot> recentScenes,
                                              Map<String, String> sections,
                                              Set<String> queryTerms) {
        Set<UUID> recentIds = recentScenes.stream().map(SceneSlot::id).collect(HashSet::new, Set::add, Set::addAll);
        return priorScenes.stream()
                .filter(scene -> !recentIds.contains(scene.id()))
                .map(scene -> new ScoredScene(scene, historyRelevance(plainSection(sections.get(id(scene.id()))), queryTerms)))
                .filter(scored -> scored.score() > 0)
                .sorted(Comparator.comparingInt(ScoredScene::score).reversed()
                        .thenComparing((ScoredScene scored) -> scored.scene().chapter().order(), Comparator.reverseOrder())
                        .thenComparing((ScoredScene scored) -> scored.scene().order(), Comparator.reverseOrder())
                        .thenComparing(scored -> id(scored.scene().id())))
                .limit(MAX_HISTORICAL_ANCHORS)
                .map(ScoredScene::scene)
                .toList();
    }

    private void addAnchorCandidates(List<Candidate> candidates,
                                     List<SceneSlot> anchors,
                                     Map<String, String> sections,
                                     Set<String> queryTerms) {
        for (int i = 0; i < anchors.size(); i++) {
            SceneSlot scene = anchors.get(i);
            String text = plainSection(sections.get(id(scene.id())));
            String window = anchorWindow(text, queryTerms, 260);
            candidates.add(new Candidate(
                    "anchor." + i,
                    "history_anchor",
                    id(scene.id()),
                    "历史锚点 · 第" + scene.chapter().order() + "章/第" + scene.order() + "节",
                    600 - i,
                    300,
                    scene.chapter().title() + " / " + scene.title() + "\n" + window
            ));
        }
    }

    private RenderedContext render(List<Candidate> candidates, int tokenBudget) {
        candidates.sort(Comparator.comparingInt(Candidate::priority).reversed().thenComparing(Candidate::slot));
        String contextHeader = "SCENE_DRAFT_CONTEXT " + COMPILER_VERSION;
        int usedBudget = estimateTokens(contextHeader);
        List<SelectedCandidate> selected = new ArrayList<>();
        for (Candidate candidate : candidates) {
            int remaining = tokenBudget - usedBudget;
            String header = "\n\n[" + candidate.label() + "]\n";
            int headerTokens = estimateTokens(header);
            int fragmentBudget = Math.min(candidate.maxTokens(), remaining - headerTokens);
            if (fragmentBudget < 8 || candidate.content().isBlank()) {
                continue;
            }
            String included = truncateToTokens(candidate.content(), fragmentBudget).trim();
            if (included.isBlank()) {
                continue;
            }
            int estimatedTokens = estimateTokens(header + included);
            selected.add(new SelectedCandidate(candidate, included, estimatedTokens));
            usedBudget += estimatedTokens;
        }

        selected.sort(Comparator
                .comparingInt((SelectedCandidate item) -> outputOrder(item.candidate()))
                .thenComparing(item -> item.candidate().slot()));
        StringBuilder content = new StringBuilder(contextHeader);
        List<SceneDraftContextManifest.Source> sources = new ArrayList<>();
        Map<String, String> fragmentsBySlot = new LinkedHashMap<>();
        for (SelectedCandidate item : selected) {
            Candidate candidate = item.candidate();
            String header = "\n\n[" + candidate.label() + "]\n";
            content.append(header).append(item.included());
            sources.add(new SceneDraftContextManifest.Source(
                    candidate.slot(),
                    candidate.sourceType(),
                    candidate.sourceId(),
                    candidate.label(),
                    sourceReason(candidate),
                    candidate.priority(),
                    item.estimatedTokens(),
                    item.included().length() < candidate.content().trim().length(),
                    sha256(item.included())
            ));
            fragmentsBySlot.put(candidate.slot(), candidate.label() + "：\n" + item.included());
        }
        String compiled = truncateToTokens(content.toString(), tokenBudget).trim();
        return new RenderedContext(
                compiled,
                List.copyOf(sources),
                Collections.unmodifiableMap(new LinkedHashMap<>(fragmentsBySlot))
        );
    }

    private int outputOrder(Candidate candidate) {
        return switch (candidate.sourceType()) {
            case "overall_planning" -> 100;
            case "chapter_planning" -> 110;
            case "scene_planning" -> 120;
            case "world" -> 200;
            case "lorebook" -> 300;
            case "lorebook_graph" -> 390;
            case "recent_scene" -> 400;
            case "style_profile" -> 500;
            case "style_scene_override" -> 510;
            case "character_voice" -> 520;
            case "history_anchor" -> 600;
            default -> 900;
        };
    }

    private String sourceReason(Candidate candidate) {
        return switch (candidate.sourceType()) {
            case "scene_planning" -> "当前场景的直接创作约束";
            case "chapter_planning" -> "当前章节的结构与张力约束";
            case "overall_planning" -> "全书核心承诺与长期约束";
            case "world" -> "稿件、大纲或故事绑定的世界观";
            case "style_profile" -> "当前故事已激活的风格画像";
            case "style_scene_override" -> "当前 sceneType 命中的风格覆盖";
            case "character_voice" -> "当前场景标题、摘要或规划中明确相关的角色声音";
            case "recent_scene" -> "目标场景之前最近的跨章正文尾部";
            case "lorebook" -> "按当前场景规划、角色、伏笔或关键词命中的 Lorebook";
            case "lorebook_graph" -> "已选 Lorebook 条目之间的已保存关系";
            case "history_anchor" -> "目标场景之前与当前规划词法相关的历史窗口";
            default -> "scene-draft-v2 上下文来源";
        };
    }

    private String queryText(RequiredContext required, String sceneType) {
        OutlinePosition position = required.position();
        return String.join(" ",
                position.scene().title(),
                position.scene().summary(),
                canonicalJson(position.scene().planning()),
                sceneType
        );
    }

    private Set<String> queryTerms(String query) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        Matcher matcher = WORD_PATTERN.matcher(firstNonBlank(query, "").toLowerCase(Locale.ROOT));
        while (matcher.find() && terms.size() < 120) {
            String word = matcher.group();
            if (word.codePoints().allMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN)) {
                if (word.length() <= 8 && !STOP_TERMS.contains(word)) {
                    terms.add(word);
                }
                for (int i = 0; i + 2 <= word.length() && terms.size() < 120; i++) {
                    String bi = word.substring(i, i + 2);
                    if (!STOP_TERMS.contains(bi)) {
                        terms.add(bi);
                    }
                }
            } else if (!STOP_TERMS.contains(word)) {
                terms.add(word);
            }
        }
        return terms;
    }

    private String relevantCharacterNames(Story story, String baseQueryText) {
        if (story == null || baseQueryText == null || baseQueryText.isBlank()) {
            return "";
        }
        return characterCardRepository.findByStory(story).stream()
                .map(CharacterCard::getName)
                .filter(name -> name != null && !name.isBlank())
                .filter(name -> containsIgnoreCase(baseQueryText, name))
                .distinct()
                .sorted()
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }

    private Set<String> lorebookAnchorTerms(List<Map<String, Object>> entries) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (Map<String, Object> entry : entries) {
            terms.addAll(queryTerms(text(entry.get("displayName"))));
            for (Object keyword : list(entry.get("keywords"))) {
                terms.addAll(queryTerms(text(keyword)));
            }
        }
        return terms;
    }

    private int historyRelevance(String content, Set<String> terms) {
        if (content.isBlank()) {
            return 0;
        }
        String lower = content.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : terms) {
            int index = lower.indexOf(term);
            if (index >= 0) {
                score += Math.min(8, Math.max(1, term.length() - 1));
                if (lower.indexOf(term, index + term.length()) >= 0) {
                    score += 1;
                }
            }
        }
        return score;
    }

    private String anchorWindow(String content, Set<String> terms, int maxChars) {
        if (content.length() <= maxChars) {
            return content;
        }
        String lower = content.toLowerCase(Locale.ROOT);
        int match = -1;
        for (String term : terms.stream().sorted(Comparator.comparingInt(String::length).reversed()).toList()) {
            match = lower.indexOf(term);
            if (match >= 0) {
                break;
            }
        }
        if (match < 0) {
            return tail(content, maxChars);
        }
        int start = Math.max(0, match - maxChars / 3);
        int end = Math.min(content.length(), start + maxChars);
        return content.substring(start, end);
    }

    private Map<String, String> readSections(Manuscript manuscript) {
        if (manuscript == null || manuscript.getSectionsJson() == null || manuscript.getSectionsJson().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(manuscript.getSectionsJson(), new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new BusinessException("稿件正文数据异常，无法编译场景上下文", ex);
        }
    }

    private void addJsonCandidate(List<Candidate> candidates,
                                  String slot,
                                  String sourceType,
                                  UUID sourceId,
                                  String label,
                                  int priority,
                                  int maxTokens,
                                  Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        candidates.add(new Candidate(
                slot,
                sourceType,
                firstNonBlank(id(sourceId), slot),
                label,
                priority,
                maxTokens,
                canonicalJson(value)
        ));
    }

    private List<String> filterRelations(LorebookSelection selection, Set<String> includedIds) {
        return relationLabels(selection, includedIds);
    }

    private List<String> relationLabels(LorebookSelection selection, Set<String> includedIds) {
        Map<String, String> names = new HashMap<>();
        for (Map<String, Object> entry : selection.entries()) {
            names.put(text(entry.get("id")), firstNonBlank(text(entry.get("displayName")), text(entry.get("id"))));
        }
        List<String> labels = new ArrayList<>();
        List<Map<String, Object>> relationships = selection.relationships().stream()
                .sorted(relationshipComparator())
                .toList();
        for (Map<String, Object> relationship : relationships) {
            String source = text(relationship.get("source"));
            String target = text(relationship.get("target"));
            if (!includedIds.contains(source) || !includedIds.contains(target)) {
                continue;
            }
            labels.add(names.getOrDefault(source, source) + " --"
                    + firstNonBlank(text(relationship.get("relationType")), "related_to")
                    + "--> " + names.getOrDefault(target, target));
        }
        return labels;
    }

    private Comparator<Map<String, Object>> relationshipComparator() {
        return Comparator
                .comparing((Map<String, Object> relation) -> text(relation.get("source")))
                .thenComparing(relation -> text(relation.get("target")))
                .thenComparing(relation -> text(relation.get("relationType")))
                .thenComparing(relation -> text(relation.get("id")));
    }

    private List<String> activeCharacters(List<Map<String, Object>> lorebook,
                                          List<SceneDraftContextManifest.Source> sources) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Map<String, Object> entry : lorebook) {
            if ("character".equalsIgnoreCase(text(entry.get("category")))) {
                names.add(firstNonBlank(text(entry.get("displayName")), "角色"));
            }
        }
        for (SceneDraftContextManifest.Source source : sources) {
            if ("character_voice".equals(source.sourceType())) {
                names.add(source.label().replaceFirst("^角色声音 · ", ""));
            }
        }
        return names.stream().limit(5).toList();
    }

    private Set<String> sourceIds(List<SceneDraftContextManifest.Source> sources, String sourceType) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (SceneDraftContextManifest.Source source : sources) {
            if (sourceType.equals(source.sourceType())) {
                ids.add(source.sourceId());
            }
        }
        return ids;
    }

    private boolean sameOwner(Story story, World world) {
        if (story.getUser() == null || world.getUser() == null) {
            return false;
        }
        return story.getUser().getId() != null && story.getUser().getId().equals(world.getUser().getId());
    }

    private String canonicalJsonText(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return "";
        }
        try {
            return canonicalJson(objectMapper.readValue(rawJson, Object.class));
        } catch (Exception ignored) {
            return rawJson.trim();
        }
    }

    private String canonicalJson(Object value) {
        try {
            return objectMapper.writeValueAsString(canonicalize(value));
        } catch (Exception ex) {
            throw new BusinessException("上下文内容无法序列化", ex);
        }
    }

    private Object canonicalize(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> sorted = new TreeMap<>();
            raw.forEach((key, item) -> sorted.put(String.valueOf(key), canonicalize(item)));
            return sorted;
        }
        if (value instanceof List<?> raw) {
            return raw.stream().map(this::canonicalize).toList();
        }
        return value;
    }

    static int estimateTokens(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        int quarterTokens = 0;
        for (int index = 0; index < value.length(); ) {
            int codePoint = value.codePointAt(index);
            boolean ascii = codePoint <= 0x7f;
            quarterTokens += ascii ? 1 : 4;
            index += Character.charCount(codePoint);
        }
        return Math.max(1, (quarterTokens + 3) / 4);
    }

    private String truncateToTokens(String value, int maxTokens) {
        if (value == null || value.isBlank() || maxTokens <= 0) {
            return "";
        }
        int maxQuarterTokens = maxTokens * 4;
        int used = 0;
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < value.length(); ) {
            int codePoint = value.codePointAt(index);
            int cost = codePoint <= 0x7f ? 1 : 4;
            if (used + cost > maxQuarterTokens) {
                break;
            }
            result.appendCodePoint(codePoint);
            used += cost;
            index += Character.charCount(codePoint);
        }
        return result.toString();
    }

    private String plainSection(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return HTML_TAG_PATTERN.matcher(value)
                .replaceAll(" ")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String tail(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value == null ? "" : value;
        }
        return value.substring(value.length() - maxChars);
    }

    private int normalizeBudget(int requested) {
        if (requested <= 0) {
            return DEFAULT_TOKEN_BUDGET;
        }
        return Math.min(MAX_TOKEN_BUDGET, Math.max(128, requested));
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(firstNonBlank(value, "").getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private boolean containsIgnoreCase(String source, String part) {
        return source != null && part != null && !part.isBlank()
                && source.toLowerCase(Locale.ROOT).contains(part.toLowerCase(Locale.ROOT));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String id(UUID value) {
        return value == null ? "" : value.toString();
    }

    private UUID uuid(Object value) {
        try {
            return value == null ? null : UUID.fromString(value.toString());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private boolean boolValue(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value == null ? fallback : Boolean.parseBoolean(value.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> raw) {
            return new LinkedHashMap<>((Map<String, Object>) raw);
        }
        return Map.of();
    }

    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?>) {
                result.add(map(item));
            }
        }
        return result;
    }

    private List<Object> list(Object value) {
        return value instanceof List<?> raw ? new ArrayList<>(raw) : List.of();
    }

    private List<IndexedMap> indexed(List<Map<String, Object>> values) {
        List<IndexedMap> indexed = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            indexed.add(new IndexedMap(i, values.get(i)));
        }
        return indexed;
    }

    private record RequiredContext(Manuscript manuscript, Outline outline, Story story, OutlinePosition position) {
    }

    private record OutlinePosition(Map<String, Object> overallPlanning,
                                   ChapterSlot chapter,
                                   SceneSlot scene,
                                   List<SceneSlot> priorScenes) {
    }

    private record ChapterSlot(UUID id,
                               String title,
                               String summary,
                               int order,
                               Map<String, Object> planning) {
    }

    private record SceneSlot(UUID id,
                             String title,
                             String summary,
                             int order,
                             Map<String, Object> planning,
                             ChapterSlot chapter) {
    }

    private record Candidate(String slot,
                             String sourceType,
                             String sourceId,
                             String label,
                             int priority,
                             int maxTokens,
                             String content) {
    }

    private record SelectedCandidate(Candidate candidate,
                                     String included,
                                     int estimatedTokens) {
    }

    private record RenderedContext(String content,
                                   List<SceneDraftContextManifest.Source> sources,
                                   Map<String, String> fragmentsBySlot) {
    }

    private record LorebookSelection(List<Map<String, Object>> entries,
                                     List<Map<String, Object>> relationships) {
    }

    private record ScoredScene(SceneSlot scene, int score) {
    }

    private record IndexedMap(int index, Map<String, Object> value) {
    }
}
