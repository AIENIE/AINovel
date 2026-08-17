package com.ainovel.app.manuscript.context;

import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.story.model.CharacterCard;
import com.ainovel.app.story.model.Outline;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.story.repo.CharacterCardRepository;
import com.ainovel.app.style.model.CharacterVoice;
import com.ainovel.app.style.model.StyleProfile;
import com.ainovel.app.style.model.StyleProfileSceneOverride;
import com.ainovel.app.style.repo.CharacterVoiceRepository;
import com.ainovel.app.style.repo.StyleProfileRepository;
import com.ainovel.app.user.User;
import com.ainovel.app.v2.V2ContextPersistenceService;
import com.ainovel.app.world.model.World;
import com.ainovel.app.world.repo.WorldRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SceneDraftContextCompilerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldCompilePlanningWorldStyleMemoryAndStableManifestWithinBudget() throws Exception {
        WorldRepository worldRepository = mock(WorldRepository.class);
        CharacterCardRepository characterCardRepository = mock(CharacterCardRepository.class);
        StyleProfileRepository styleProfileRepository = mock(StyleProfileRepository.class);
        CharacterVoiceRepository characterVoiceRepository = mock(CharacterVoiceRepository.class);
        V2ContextPersistenceService contextPersistenceService = mock(V2ContextPersistenceService.class);
        SceneDraftContextCompiler compiler = new SceneDraftContextCompiler(
                objectMapper,
                worldRepository,
                characterCardRepository,
                styleProfileRepository,
                characterVoiceRepository,
                contextPersistenceService
        );

        User owner = new User();
        owner.setId(UUID.randomUUID());
        Story story = new Story();
        story.setId(UUID.randomUUID());
        story.setUser(owner);
        story.setTitle("雨城疑案");
        story.setSynopsis("林烬追查旧码头铜扣背后的失踪案。");

        UUID worldId = UUID.randomUUID();
        story.setWorldId(worldId.toString());
        World world = new World();
        world.setId(worldId);
        world.setUser(owner);
        world.setName("潮汐雨城");
        world.setCreativeIntent("所有超自然现象都必须留下可验证代价");
        world.setThemesJson("[\"记忆\",\"代价\"]");
        world.setModulesJson("{\"rules\":{\"tide\":\"午夜退潮\"}}");
        when(worldRepository.findById(worldId)).thenReturn(Optional.of(world));

        StyleProfile profile = new StyleProfile();
        profile.setId(UUID.randomUUID());
        profile.setStory(story);
        profile.setName("冷峻短句");
        profile.setProfileType("global");
        profile.setDimensionsJson("{\"sentenceLength\":\"short\"}");
        profile.setActive(true);
        StyleProfileSceneOverride override = new StyleProfileSceneOverride();
        override.setId(UUID.randomUUID());
        override.setSceneType("dialogue");
        override.setOverrideJson("{\"dialogueRatio\":70}");
        profile.addSceneOverride(override);
        when(styleProfileRepository.findFirstByStoryAndActiveTrue(story)).thenReturn(Optional.of(profile));

        CharacterCard card = new CharacterCard();
        card.setId(UUID.randomUUID());
        card.setStory(story);
        card.setName("林烬");
        CharacterCard unrelatedCard = new CharacterCard();
        unrelatedCard.setId(UUID.randomUUID());
        unrelatedCard.setStory(story);
        unrelatedCard.setName("许槐");
        when(characterCardRepository.findByStory(story)).thenReturn(List.of(card, unrelatedCard));
        CharacterVoice voice = new CharacterVoice();
        voice.setId(UUID.randomUUID());
        voice.setStory(story);
        voice.setCharacterCard(card);
        voice.setSpeechPattern("先复述证据，再给出短促判断");
        voice.setVocabularyLevel("克制");
        voice.setCatchphrasesJson("[\"先看证据\"]");
        CharacterVoice unrelatedVoice = new CharacterVoice();
        unrelatedVoice.setId(UUID.randomUUID());
        unrelatedVoice.setStory(story);
        unrelatedVoice.setCharacterCard(unrelatedCard);
        unrelatedVoice.setSpeechPattern("每句话都以天气作比");
        when(characterVoiceRepository.findByStoryOrderByCreatedAtDesc(story))
                .thenReturn(List.of(unrelatedVoice, voice));

        UUID oldAnchorSceneId = UUID.randomUUID();
        UUID unrelatedSceneId = UUID.randomUUID();
        UUID previousChapterSceneId = UUID.randomUUID();
        UUID currentChapterPreviousId = UUID.randomUUID();
        UUID targetSceneId = UUID.randomUUID();
        UUID futureSceneId = UUID.randomUUID();

        Outline outline = new Outline();
        outline.setId(UUID.randomUUID());
        outline.setStory(story);
        outline.setWorldId(worldId.toString());
        outline.setContentJson(objectMapper.writeValueAsString(Map.of(
                "planning", Map.of("centralQuestion", "铜扣为何出现在旧码头", "stakes", "失踪者会被永久遗忘"),
                "chapters", List.of(
                        chapter(1, "第一章", "铜扣第一次出现", Map.of("purpose", "建立谜面"), List.of(
                                scene(oldAnchorSceneId, 1, "潮痕", "林烬发现铜扣", Map.of("goal", "记录铜扣编号")),
                                scene(unrelatedSceneId, 2, "厨房", "众人短暂休息", Map.of()),
                                scene(previousChapterSceneId, 3, "封门", "周燃封锁旧码头", Map.of("goal", "封锁现场"))
                        )),
                        chapter(2, "第二章", "林烬重返码头", Map.of("purpose", "验证铜扣来源"), List.of(
                                scene(currentChapterPreviousId, 1, "雨巷", "林烬进入雨巷", Map.of("conflict", "被人跟踪")),
                                scene(targetSceneId, 2, "逼问", "林烬用铜扣逼问守门人", Map.of(
                                        "sceneType", "dialogue",
                                        "goal", "确认铜扣主人",
                                        "conflict", "守门人拒绝开口",
                                        "infoRelease", "铜扣来自失踪货船"
                                )),
                                scene(futureSceneId, 3, "追船", "追上失踪货船", Map.of())
                        ))
                )
        )));

        Map<String, String> sections = new LinkedHashMapBuilder()
                .put(oldAnchorSceneId, "<p>旧账页只写着编号七，旁边是一道褪色波纹。</p>")
                .put(unrelatedSceneId, "<p>厨房里的汤已经凉了，众人没有再谈案子。</p>")
                .put(previousChapterSceneId, "<p>上一章收束时，周燃封住码头北门，并留下两名守卫。</p>")
                .put(currentChapterPreviousId, "<p>林烬穿过雨巷，确认跟踪者在第三个路口停下。</p>")
                .put(futureSceneId, "<p>未来场景绝不能进入当前上下文。</p>")
                .build();
        Manuscript manuscript = new Manuscript();
        manuscript.setId(UUID.randomUUID());
        manuscript.setOutline(outline);
        manuscript.setWorldId(worldId.toString());
        manuscript.setSectionsJson(objectMapper.writeValueAsString(sections));

        UUID characterEntryId = UUID.randomUUID();
        UUID clueEntryId = UUID.randomUUID();
        UUID globalRuleId = UUID.randomUUID();
        UUID irrelevantEntryId = UUID.randomUUID();
        UUID disabledMatchedEntryId = UUID.randomUUID();
        Map<String, Object> disabledMatched = lorebook(
                disabledMatchedEntryId, "失踪货船密档", "event", "铜扣编号七的完整登记", List.of("铜扣"), 99, "before_scene"
        );
        disabledMatched.put("enabled", false);
        when(contextPersistenceService.listLorebook(story.getId())).thenReturn(List.of(
                lorebook(characterEntryId, "林烬", "character", "调查员，只相信可复核证据", List.of("林烬"), 10, "before_scene"),
                lorebook(clueEntryId, "潮汐铜扣", "item", "编号七来自失踪货船", List.of("铜扣", "货船", "编号七"), 9, "before_scene"),
                lorebook(globalRuleId, "雨城规则", "concept", "午夜退潮会暴露旧航道", List.of(), 8, "system_prompt"),
                lorebook(irrelevantEntryId, "南方果园", "location", "盛产橘子", List.of("橘子"), 1, "before_scene"),
                disabledMatched
        ));
        Map<String, Object> holdsClue = relationship(characterEntryId, clueEntryId, "holds_clue");
        Map<String, Object> belongsTo = relationship(clueEntryId, characterEntryId, "belongs_to");
        Map<String, Object> unrelatedGraphEdge = relationship(clueEntryId, irrelevantEntryId, "unrelated_to");
        when(contextPersistenceService.listRelationships(story.getId())).thenReturn(
                List.of(holdsClue, belongsTo, unrelatedGraphEdge),
                List.of(unrelatedGraphEdge, belongsTo, holdsClue)
        );

        CompiledSceneDraftContext first = compiler.compile(manuscript, targetSceneId, sections, 9999);
        CompiledSceneDraftContext second = compiler.compile(manuscript, targetSceneId, sections, 9999);

        assertEquals(SceneDraftContextCompiler.COMPILER_VERSION, first.compilerVersion());
        assertEquals(3500, first.manifest().tokenBudget());
        assertTrue(first.manifest().tokenUsed() <= first.manifest().tokenBudget());
        assertEquals(64, first.contextHash().length());
        assertEquals(first.contextHash(), second.contextHash(), "相同输入必须产生稳定 hash");
        assertEquals("dialogue", first.manifest().sceneType());
        assertEquals(worldId, first.manifest().boundWorldId());
        assertTrue(first.manifest().warnings().stream().anyMatch(value -> value.contains("capped")));

        assertTrue(first.content().contains("当前场景规划"));
        assertTrue(first.content().contains("确认铜扣主人"));
        assertTrue(first.content().contains("当前章节规划"));
        assertTrue(first.content().contains("全书规划"));
        assertTrue(first.content().contains("潮汐雨城"));
        assertTrue(first.content().contains("冷峻短句"));
        assertTrue(first.content().contains("场景类型覆盖 · dialogue"));
        assertTrue(first.content().contains("角色声音 · 林烬"));
        assertFalse(first.content().contains("角色声音 · 许槐"));
        assertTrue(first.content().contains("潮汐铜扣"));
        assertTrue(first.content().contains("holds_clue"));
        assertTrue(first.content().contains("旧账页只写着编号七"), "Lorebook 关键词应扩展历史锚点检索");
        assertFalse(first.content().contains("未来场景绝不能进入当前上下文"));

        assertTrue(first.recentSceneContext().contains("周燃封住码头北门"), "最近正文应跨章取上一章尾场");
        assertTrue(first.recentSceneContext().contains("林烬穿过雨巷"));
        assertTrue(first.recentSceneContext().indexOf("周燃封住码头北门")
                < first.recentSceneContext().indexOf("林烬穿过雨巷"), "入选时优先较新场景，呈现时保持时序");
        assertEquals(2, sourceCount(first, "recent_scene"));
        assertTrue(sourceCount(first, "history_anchor") <= SceneDraftContextCompiler.MAX_HISTORICAL_ANCHORS);
        assertTrue(sourceCount(first, "history_anchor") >= 1);
        assertTrue(first.activeCharacters().contains("林烬"));
        assertFalse(first.lorebookEntries().stream()
                .anyMatch(entry -> irrelevantEntryId.equals(entry.get("id"))));
        assertFalse(first.lorebookEntries().stream()
                .anyMatch(entry -> disabledMatchedEntryId.equals(entry.get("id"))));
        assertFalse(first.graphRelations().stream().anyMatch(relation -> relation.contains("unrelated_to")));
        assertTrue(sourcePriority(first, "lorebook") > sourcePriority(first, "recent_scene"));
        assertTrue(sourcePriority(first, "recent_scene") > sourcePriority(first, "style_profile"));
        assertTrue(sourcePriority(first, "style_profile") > sourcePriority(first, "history_anchor"));

        Map<String, String> changedSections = new HashMap<>(sections);
        changedSections.put(currentChapterPreviousId.toString(), "<p>林烬改从钟楼进入码头。</p>");
        CompiledSceneDraftContext changed = compiler.compile(manuscript, targetSceneId, changedSections, 3500);
        assertNotEquals(first.contextHash(), changed.contextHash());
    }

    @Test
    void shouldNotInferSceneTypeFromLegacyPlanningTypeOrApplySceneOverride() throws Exception {
        WorldRepository worldRepository = mock(WorldRepository.class);
        CharacterCardRepository characterCardRepository = mock(CharacterCardRepository.class);
        StyleProfileRepository styleProfileRepository = mock(StyleProfileRepository.class);
        CharacterVoiceRepository characterVoiceRepository = mock(CharacterVoiceRepository.class);
        V2ContextPersistenceService contextPersistenceService = mock(V2ContextPersistenceService.class);
        SceneDraftContextCompiler compiler = new SceneDraftContextCompiler(
                objectMapper,
                worldRepository,
                characterCardRepository,
                styleProfileRepository,
                characterVoiceRepository,
                contextPersistenceService
        );

        User owner = new User();
        owner.setId(UUID.randomUUID());
        Story story = new Story();
        story.setId(UUID.randomUUID());
        story.setUser(owner);
        story.setTitle("旧大纲兼容");
        StyleProfile profile = new StyleProfile();
        profile.setId(UUID.randomUUID());
        profile.setStory(story);
        profile.setName("全局风格");
        StyleProfileSceneOverride override = new StyleProfileSceneOverride();
        override.setId(UUID.randomUUID());
        override.setSceneType("dialogue");
        override.setOverrideJson("{\"dialogueRatio\":80}");
        profile.addSceneOverride(override);
        when(styleProfileRepository.findFirstByStoryAndActiveTrue(story)).thenReturn(Optional.of(profile));
        when(characterCardRepository.findByStory(story)).thenReturn(List.of());
        when(characterVoiceRepository.findByStoryOrderByCreatedAtDesc(story)).thenReturn(List.of());
        when(contextPersistenceService.listLorebook(story.getId())).thenReturn(List.of());
        when(contextPersistenceService.listRelationships(story.getId())).thenReturn(List.of());

        UUID sceneId = UUID.randomUUID();
        Outline outline = new Outline();
        outline.setId(UUID.randomUUID());
        outline.setStory(story);
        outline.setContentJson(objectMapper.writeValueAsString(Map.of(
                "chapters", List.of(chapter(1, "第一章", "", Map.of(), List.of(
                        scene(sceneId, 1, "谈判", "守门人拒绝开口", Map.of(
                                "type", "dialogue",
                                "goal", "问出线索"
                        ))
                )))
        )));
        Manuscript manuscript = new Manuscript();
        manuscript.setId(UUID.randomUUID());
        manuscript.setOutline(outline);

        CompiledSceneDraftContext compiled = compiler.compile(manuscript, sceneId, Map.of(), 3500);

        assertEquals("", compiled.manifest().sceneType());
        assertTrue(compiled.content().contains("激活风格画像"));
        assertFalse(compiled.content().contains("场景类型覆盖"));
        assertEquals(0, sourceCount(compiled, "style_scene_override"));
    }

    private int sourceCount(CompiledSceneDraftContext compiled, String sourceType) {
        return (int) compiled.manifest().sources().stream()
                .filter(source -> sourceType.equals(source.sourceType()))
                .count();
    }

    private int sourcePriority(CompiledSceneDraftContext compiled, String sourceType) {
        return compiled.manifest().sources().stream()
                .filter(source -> sourceType.equals(source.sourceType()))
                .mapToInt(SceneDraftContextManifest.Source::priority)
                .max()
                .orElseThrow();
    }

    private Map<String, Object> chapter(int order,
                                        String title,
                                        String summary,
                                        Map<String, Object> planning,
                                        List<Map<String, Object>> scenes) {
        return Map.of(
                "id", UUID.randomUUID(),
                "order", order,
                "title", title,
                "summary", summary,
                "planning", planning,
                "scenes", scenes
        );
    }

    private Map<String, Object> scene(UUID id,
                                      int order,
                                      String title,
                                      String summary,
                                      Map<String, Object> planning) {
        return Map.of(
                "id", id,
                "order", order,
                "title", title,
                "summary", summary,
                "planning", planning
        );
    }

    private Map<String, Object> lorebook(UUID id,
                                         String displayName,
                                         String category,
                                         String content,
                                         List<String> keywords,
                                         int priority,
                                         String insertionPosition) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("id", id);
        entry.put("displayName", displayName);
        entry.put("category", category);
        entry.put("content", content);
        entry.put("keywords", keywords);
        entry.put("priority", priority);
        entry.put("enabled", true);
        entry.put("insertionPosition", insertionPosition);
        entry.put("tokenBudget", 180);
        return entry;
    }

    private Map<String, Object> relationship(UUID source, UUID target, String relationType) {
        return Map.of(
                "id", UUID.randomUUID(),
                "source", source,
                "target", target,
                "relationType", relationType
        );
    }

    private static final class LinkedHashMapBuilder {
        private final Map<String, String> values = new java.util.LinkedHashMap<>();

        LinkedHashMapBuilder put(UUID sceneId, String content) {
            values.put(sceneId.toString(), content);
            return this;
        }

        Map<String, String> build() {
            return new java.util.LinkedHashMap<>(values);
        }
    }
}
