package com.ainovel.app.manuscript;

import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.common.RefineRequest;
import com.ainovel.app.manuscript.dto.*;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.quality.SlopQualityGate;
import com.ainovel.app.quality.SlopQualityRequest;
import com.ainovel.app.quality.SlopQualityResult;
import com.ainovel.app.quality.PlotQualityRequest;
import com.ainovel.app.quality.PlotQualityService;
import com.ainovel.app.material.MaterialRetrievalService;
import com.ainovel.app.material.dto.MaterialSearchRequest;
import com.ainovel.app.material.dto.MaterialSearchResultDto;
import com.ainovel.app.prompt.AssembledPrompt;
import com.ainovel.app.prompt.PromptAssemblyService;
import com.ainovel.app.prompt.PromptReference;
import com.ainovel.app.prompt.SceneGenerationPromptInput;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.story.model.Outline;
import com.ainovel.app.story.model.CharacterCard;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.story.dto.OutlineSaveRequest;
import com.ainovel.app.story.repo.CharacterCardRepository;
import com.ainovel.app.story.repo.OutlineRepository;
import com.ainovel.app.style.StyleContextProvider;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class ManuscriptService {
    private static final int MIN_SECTION_HAN = 2800;
    private static final int MAX_SECTION_HAN = 3200;
    private static final int MAX_GENERATION_ATTEMPTS = 3;
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    @Autowired
    private ManuscriptRepository manuscriptRepository;
    @Autowired
    private OutlineRepository outlineRepository;
    @Autowired
    private CharacterCardRepository characterCardRepository;
    @Autowired
    private AiService aiService;
    @Autowired
    private ResourceAccessGuard accessGuard;
    @Autowired
    private SlopQualityGate slopQualityGate;
    @Autowired
    private PlotQualityService plotQualityService;
    @Autowired
    private PromptAssemblyService promptAssemblyService;
    @Autowired
    private MaterialRetrievalService materialRetrievalService;
    @Autowired
    private StyleContextProvider styleContextProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<ManuscriptDto> listByOutline(UUID outlineId) {
        Outline outline = outlineRepository.findByIdWithStoryUser(outlineId).orElseThrow(() -> new RuntimeException("大纲不存在"));
        accessGuard.assertOwner(outline.getStory().getUser());
        return manuscriptRepository.findByOutline(outline).stream().map(this::toDto).toList();
    }

    @Transactional
    public ManuscriptDto create(UUID outlineId, ManuscriptCreateRequest request) {
        Outline outline = outlineRepository.findByIdWithStoryUser(outlineId).orElseThrow(() -> new RuntimeException("大纲不存在"));
        accessGuard.assertOwner(outline.getStory().getUser());
        Manuscript manuscript = new Manuscript();
        manuscript.setOutline(outline);
        manuscript.setTitle(request.title());
        manuscript.setWorldId(request.worldId());
        manuscript.setSectionsJson(writeJson(new HashMap<String, String>()));
        manuscript.setCharacterLogsJson(writeJson(new ArrayList<>()));
        manuscriptRepository.save(manuscript);
        return toDto(manuscript);
    }

    public ManuscriptDto get(UUID id) {
        Manuscript manuscript = manuscriptRepository.findWithStoryById(id).orElseThrow(() -> new RuntimeException("稿件不存在"));
        accessGuard.assertOwner(ownerOf(manuscript));
        return toDto(manuscript);
    }

    @Transactional
    public void delete(UUID id) {
        Manuscript manuscript = manuscriptRepository.findWithStoryById(id).orElseThrow(() -> new RuntimeException("稿件不存在"));
        accessGuard.assertOwner(ownerOf(manuscript));
        manuscriptRepository.delete(manuscript);
    }

    @Transactional
    public ManuscriptDto generateForScene(UUID manuscriptId, UUID sceneId) {
        Manuscript manuscript = manuscriptRepository.findWithStoryById(manuscriptId).orElseThrow(() -> new RuntimeException("稿件不存在"));
        accessGuard.assertOwner(ownerOf(manuscript));
        Map<String, String> sections = readSectionMap(manuscript.getSectionsJson());
        String generatedHtml = generateSceneSectionHtml(manuscript, sceneId, sections);
        sections.put(sceneId.toString(), generatedHtml);
        manuscript.setSectionsJson(writeJson(sections));
        manuscriptRepository.save(manuscript);
        return toDto(manuscript);
    }

    @Transactional
    public ManuscriptDto updateSection(UUID manuscriptId, UUID sceneId, SectionUpdateRequest request) {
        Manuscript manuscript = manuscriptRepository.findWithStoryById(manuscriptId).orElseThrow(() -> new RuntimeException("稿件不存在"));
        accessGuard.assertOwner(ownerOf(manuscript));
        Map<String, String> sections = readSectionMap(manuscript.getSectionsJson());
        sections.put(sceneId.toString(), request.content());
        manuscript.setSectionsJson(writeJson(sections));
        manuscriptRepository.save(manuscript);
        return toDto(manuscript);
    }

    @Transactional
    public ManuscriptDto generateForScene(UUID sceneId) {
        Manuscript manuscript = manuscriptRepository.findAll().stream()
                .filter(m -> isCurrentUserOwner(ownerOf(m)))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("请先创建稿件"));
        return generateForScene(manuscript.getId(), sceneId);
    }

    @Transactional
    public ManuscriptDto updateSection(UUID sectionId, SectionUpdateRequest request) {
        Manuscript manuscript = manuscriptRepository.findAll().stream()
                .filter(m -> isCurrentUserOwner(ownerOf(m)))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("请先创建稿件"));
        return updateSection(manuscript.getId(), sectionId, request);
    }

    @Transactional
    public List<CharacterChangeLogDto> analyzeCharacterChanges(UUID manuscriptId, AnalyzeCharacterChangeRequest request) {
        Manuscript manuscript = manuscriptRepository.findWithStoryById(manuscriptId).orElseThrow();
        accessGuard.assertOwner(ownerOf(manuscript));
        List<Map<String, Object>> logs = readLogs(manuscript.getCharacterLogsJson());
        UUID logId = UUID.randomUUID();
        UUID characterId = parseUuidOrRandom(
                request.characterIds() != null && !request.characterIds().isEmpty()
                        ? request.characterIds().get(0)
                        : null
        );
        Map<String, Object> item = new HashMap<>();
        item.put("id", logId.toString());
        item.put("characterId", characterId.toString());
        item.put("summary", "检测到角色变化：" + (request.sectionContent() == null ? "" : request.sectionContent().substring(0, Math.min(20, request.sectionContent().length()))));
        item.put("createdAt", Instant.now().toString());
        logs.add(item);
        manuscript.setCharacterLogsJson(writeJson(logs));
        manuscriptRepository.save(manuscript);
        return mapLogs(logs);
    }

    public List<CharacterChangeLogDto> listCharacterLogs(UUID manuscriptId) {
        Manuscript manuscript = manuscriptRepository.findWithStoryById(manuscriptId).orElseThrow();
        accessGuard.assertOwner(ownerOf(manuscript));
        return mapLogs(readLogs(manuscript.getCharacterLogsJson()));
    }

    public List<CharacterChangeLogDto> listCharacterLogs(UUID manuscriptId, UUID characterId) {
        return listCharacterLogs(manuscriptId).stream()
                .filter(l -> l.characterId().equals(characterId))
                .toList();
    }

    public String generateDialogue(RefineRequest request) {
        return "【记忆对话】" + request.text();
    }

    private ManuscriptDto toDto(Manuscript manuscript) {
        return new ManuscriptDto(
                manuscript.getId(),
                manuscript.getOutline().getId(),
                manuscript.getTitle(),
                manuscript.getWorldId(),
                readSectionMap(manuscript.getSectionsJson()),
                manuscript.getUpdatedAt()
        );
    }

    private Map<String, String> readSectionMap(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private List<Map<String, Object>> readLogs(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<CharacterChangeLogDto> mapLogs(List<Map<String, Object>> logs) {
        List<CharacterChangeLogDto> result = new ArrayList<>();
        for (Map<String, Object> log : logs) {
            try {
                UUID id = UUID.fromString(String.valueOf(log.get("id")));
                UUID characterId = UUID.fromString(String.valueOf(log.get("characterId")));
                String summary = String.valueOf(log.get("summary"));
                Instant createdAt = Instant.parse(String.valueOf(log.get("createdAt")));
                result.add(new CharacterChangeLogDto(id, characterId, summary, createdAt));
            } catch (Exception ignored) {
                // Skip malformed historical data.
            }
        }
        return result;
    }

    private String writeJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj);} catch (Exception e) { return "{}";}
    }

    private User ownerOf(Manuscript manuscript) {
        return manuscript.getOutline().getStory().getUser();
    }

    private boolean isCurrentUserOwner(User user) {
        try {
            accessGuard.assertOwner(user);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private UUID parseUuidOrRandom(String raw) {
        if (raw == null || raw.isBlank()) {
            return UUID.randomUUID();
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (Exception ignored) {
            return UUID.randomUUID();
        }
    }

    private String generateSceneSectionHtml(Manuscript manuscript, UUID sceneId, Map<String, String> existingSections) {
        SceneContext sceneContext = resolveSceneContext(manuscript.getOutline(), sceneId);
        Story story = manuscript.getOutline().getStory();
        User owner = ownerOf(manuscript);
        List<CharacterCard> characters = characterCardRepository.findByStory(story);

        String previousDraft = null;
        int previousCount = 0;

        for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
            AssembledPrompt prompt = buildScenePrompt(story, sceneContext, characters, existingSections, previousDraft, previousCount, attempt, owner);
            String raw = aiService.chat(owner, new AiChatRequest(
                    prompt.messages(),
                    null,
                    null
            )).content();
            String normalized = normalizeGeneratedText(raw);
            int hanCount = countHanCharacters(normalized);
            if (hanCount > MAX_SECTION_HAN) {
                normalized = trimToHanLimit(normalized, MAX_SECTION_HAN);
                hanCount = countHanCharacters(normalized);
            }
            if (hanCount >= MIN_SECTION_HAN && hanCount <= MAX_SECTION_HAN) {
                SlopQualityResult qualityResult = slopQualityGate.evaluateAndRepair(
                        owner,
                        buildQualityRequest(manuscript, story, sceneContext, characters, existingSections, normalized)
                );
                recordPlotQuality(owner, manuscript, story, sceneContext, characters, existingSections, qualityResult.acceptedText());
                return toEditorHtml(qualityResult.acceptedText());
            }
            previousDraft = normalized;
            previousCount = hanCount;
        }

        throw new RuntimeException("场景正文生成失败：重试 " + MAX_GENERATION_ATTEMPTS
                + " 次后仍未达到 " + MIN_SECTION_HAN + "-" + MAX_SECTION_HAN
                + " 汉字（当前 " + previousCount + "）。请稍后重试。");
    }

    private SlopQualityRequest buildQualityRequest(Manuscript manuscript,
                                                   Story story,
                                                   SceneContext scene,
                                                   List<CharacterCard> characters,
                                                   Map<String, String> existingSections,
                                                   String candidateText) {
        return new SlopQualityRequest(
                story.getId(),
                manuscript.getId(),
                scene.sceneId(),
                safeText(story.getTitle(), "未命名故事"),
                safeText(story.getGenre(), "未指定"),
                safeText(story.getTone(), "沉浸、连贯"),
                scene.chapterTitle(),
                scene.sceneTitle(),
                scene.sceneSummary(),
                buildPreviousContext(scene, existingSections),
                buildCharacterContext(characters),
                styleContextProvider.buildSlopContext(story),
                candidateText
        );
    }

    private void recordPlotQuality(User owner,
                                   Manuscript manuscript,
                                   Story story,
                                   SceneContext scene,
                                   List<CharacterCard> characters,
                                   Map<String, String> existingSections,
                                   String acceptedText) {
        try {
            plotQualityService.analyze(owner, new PlotQualityRequest(
                    story.getId(),
                    manuscript.getId(),
                    scene.sceneId(),
                    safeText(story.getTitle(), "未命名故事"),
                    safeText(story.getGenre(), "未指定"),
                    safeText(story.getTone(), "沉浸、连贯"),
                    scene.chapterTitle(),
                    scene.chapterOrder(),
                    scene.sceneTitle(),
                    scene.sceneOrder(),
                    scene.sceneSummary(),
                    outlinePlanning(manuscript.getOutline()),
                    buildPreviousContext(scene, existingSections),
                    buildCharacterContext(characters),
                    acceptedText
            ));
        } catch (RuntimeException ignored) {
            // 剧情层诊断不能阻断正文生成；用户可稍后手动重新诊断。
        }
    }

    private String outlinePlanning(Outline outline) {
        if (outline == null || outline.getContentJson() == null || outline.getContentJson().isBlank()) {
            return "暂无结构规划。";
        }
        try {
            Map<String, Object> root = objectMapper.readValue(outline.getContentJson(), new TypeReference<>() {});
            Object planning = root.get("planning");
            return planning == null ? "暂无结构规划。" : objectMapper.writeValueAsString(planning);
        } catch (Exception ex) {
            return "暂无结构规划。";
        }
    }

    private SceneContext resolveSceneContext(Outline outline, UUID sceneId) {
        if (outline == null) {
            throw new RuntimeException("大纲不存在，无法生成正文");
        }
        List<OutlineSaveRequest.ChapterPayload> chapters;
        try {
            Map<String, Object> root = objectMapper.readValue(
                    Optional.ofNullable(outline.getContentJson()).filter(v -> !v.isBlank()).orElse("{\"chapters\":[]}"),
                    new TypeReference<>() {}
            );
            chapters = objectMapper.convertValue(
                    root.getOrDefault("chapters", List.of()),
                    new TypeReference<List<OutlineSaveRequest.ChapterPayload>>() {}
            );
        } catch (Exception ex) {
            throw new RuntimeException("大纲内容异常，无法生成正文");
        }

        for (OutlineSaveRequest.ChapterPayload chapter : chapters) {
            List<OutlineSaveRequest.ScenePayload> scenes = chapter.scenes() == null ? List.of() : chapter.scenes();
            for (int i = 0; i < scenes.size(); i++) {
                OutlineSaveRequest.ScenePayload scene = scenes.get(i);
                if (scene.id() != null && scene.id().equals(sceneId)) {
                    List<UUID> previousSceneIds = new ArrayList<>();
                    List<String> siblingTitles = new ArrayList<>();
                    for (int j = 0; j < scenes.size(); j++) {
                        OutlineSaveRequest.ScenePayload sibling = scenes.get(j);
                        siblingTitles.add(safeText(sibling.title(), "第" + (j + 1) + "节"));
                        if (j < i && sibling.id() != null) {
                            previousSceneIds.add(sibling.id());
                        }
                    }
                    return new SceneContext(
                            scene.id(),
                            safeText(chapter.title(), "未命名章节"),
                            safeText(chapter.summary(), ""),
                            chapter.order() == null ? 0 : chapter.order(),
                            safeText(scene.title(), "未命名场景"),
                            safeText(scene.summary(), ""),
                            scene.order() == null ? i + 1 : scene.order(),
                            previousSceneIds,
                            siblingTitles
                    );
                }
            }
        }
        throw new RuntimeException("场景不存在，无法生成正文");
    }

    private String buildCharacterContext(List<CharacterCard> characters) {
        if (characters == null || characters.isEmpty()) {
            return "暂无角色卡。";
        }
        StringBuilder characterInfo = new StringBuilder();
        for (CharacterCard card : characters) {
            if (card == null) {
                continue;
            }
            characterInfo.append("- ").append(safeText(card.getName(), "角色")).append("：")
                    .append(safeText(card.getSynopsis(), ""))
                    .append(" 背景：").append(safeText(truncate(card.getDetails(), 180), ""))
                    .append(" 关系：").append(safeText(truncate(card.getRelationships(), 160), ""))
                    .append('\n');
        }
        return characterInfo.toString();
    }

    private String buildPreviousContext(SceneContext scene, Map<String, String> existingSections) {
        if (scene.previousSceneIds() == null || scene.previousSceneIds().isEmpty()) {
            return "暂无可用前文。";
        }
        StringBuilder continuity = new StringBuilder();
        int kept = 0;
        for (int i = scene.previousSceneIds().size() - 1; i >= 0 && kept < 2; i--) {
            UUID previousId = scene.previousSceneIds().get(i);
            String content = existingSections.get(previousId.toString());
            if (content == null || content.isBlank()) {
                continue;
            }
            String plain = truncate(stripHtml(content), 500);
            if (plain.isBlank()) {
                continue;
            }
            continuity.append("前文片段 ").append(kept + 1).append("：").append(plain).append("\n");
            kept++;
        }
        return continuity.length() == 0 ? "暂无可用前文。" : continuity.toString();
    }

    private AssembledPrompt buildScenePrompt(Story story,
                                             SceneContext scene,
                                             List<CharacterCard> characters,
                                             Map<String, String> existingSections,
                                             String previousDraft,
                                             int previousCount,
                                             int attempt,
                                             User owner) {
        String retryInstruction = "";
        if (attempt > 1) {
            String direction = previousCount < MIN_SECTION_HAN ? "扩写" : "压缩";
            retryInstruction = """

                    这是第 %d 次重试。上一版字数为 %d 汉字，请在保持剧情一致的前提下进行%s，并严格输出 %d-%d 汉字。
                    上一版草稿（可重写，不要直接复制）：
                    %s
                    """.formatted(attempt, previousCount, direction, MIN_SECTION_HAN, MAX_SECTION_HAN, truncate(previousDraft, 1200));
        }
        SceneGenerationPromptInput input = new SceneGenerationPromptInput(
                safeText(story == null ? null : story.getTitle(), "未命名故事"),
                safeText(story == null ? null : story.getGenre(), "未指定"),
                safeText(story == null ? null : story.getTone(), "沉浸、连贯"),
                safeText(story == null ? null : story.getSynopsis(), ""),
                scene.chapterTitle(),
                scene.chapterSummary(),
                scene.chapterOrder(),
                scene.sceneTitle(),
                scene.sceneSummary(),
                scene.sceneOrder(),
                buildCharacterContext(characters),
                buildPreviousContext(scene, existingSections),
                materialReferences(owner, story, scene),
                recentAvoidExpressions(scene, existingSections),
                MIN_SECTION_HAN,
                MAX_SECTION_HAN,
                retryInstruction,
                128000
        );
        return promptAssemblyService.assembleSceneDraft(input);
    }

    private List<PromptReference> materialReferences(User owner, Story story, SceneContext scene) {
        String query = String.join(" ",
                safeText(story == null ? null : story.getTitle(), ""),
                safeText(story == null ? null : story.getGenre(), ""),
                scene.chapterTitle(),
                scene.chapterSummary(),
                scene.sceneTitle(),
                scene.sceneSummary()
        ).trim();
        if (query.isBlank()) {
            return List.of();
        }
        List<MaterialSearchResultDto> results = materialRetrievalService.search(owner, new MaterialSearchRequest(query, 8));
        List<PromptReference> references = new ArrayList<>();
        for (MaterialSearchResultDto result : results) {
            references.add(new PromptReference(
                    "material",
                    result.materialId(),
                    result.title(),
                    result.snippet(),
                    result.score(),
                    result.chunkSeq() == null ? 0 : result.chunkSeq()
            ));
        }
        return references;
    }

    private List<String> recentAvoidExpressions(SceneContext scene, Map<String, String> existingSections) {
        List<String> candidates = List.of(
                "嘴角微微上扬",
                "空气仿佛凝固",
                "时间像是停了下来",
                "眼神变得坚定",
                "心中涌起一股",
                "说不出的感觉",
                "命运的齿轮",
                "一切都在此刻改变",
                "仿佛整个世界"
        );
        String recent = buildPreviousContext(scene, existingSections);
        List<String> avoid = new ArrayList<>();
        for (String candidate : candidates) {
            if (recent.contains(candidate)) {
                avoid.add(candidate);
            }
        }
        return avoid.size() > 8 ? avoid.subList(0, 8) : avoid;
    }

    private String normalizeGeneratedText(String raw) {
        if (raw == null) return "";
        String text = raw.trim();
        if (text.startsWith("```")) {
            int firstLineEnd = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstLineEnd > 0 && lastFence > firstLineEnd) {
                text = text.substring(firstLineEnd + 1, lastFence).trim();
            }
        }
        if (text.startsWith("{") && text.endsWith("}")) {
            try {
                Map<String, Object> root = objectMapper.readValue(text, new TypeReference<>() {});
                Object content = root.get("content");
                if (content == null) content = root.get("text");
                if (content == null) content = root.get("manuscript");
                if (content instanceof String str && !str.isBlank()) {
                    text = str.trim();
                }
            } catch (Exception ignored) {
            }
        }
        text = text.replace("\r\n", "\n").replace("\r", "\n").trim();
        while (text.contains("\n\n\n")) {
            text = text.replace("\n\n\n", "\n\n");
        }
        return text;
    }

    private int countHanCharacters(String text) {
        if (text == null || text.isBlank()) return 0;
        int count = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN) {
                count++;
            }
            i += Character.charCount(cp);
        }
        return count;
    }

    private String trimToHanLimit(String text, int maxHan) {
        if (text == null || text.isBlank()) return "";
        StringBuilder out = new StringBuilder();
        int hanCount = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN) {
                if (hanCount >= maxHan) break;
                hanCount++;
            }
            out.appendCodePoint(cp);
            i += Character.charCount(cp);
        }
        return out.toString().trim();
    }

    private String toEditorHtml(String text) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isBlank()) {
            return "<p></p>";
        }
        String[] blocks = normalized.split("\\n\\s*\\n");
        StringBuilder html = new StringBuilder();
        for (String block : blocks) {
            String paragraph = escapeHtml(block.trim()).replace("\n", "<br />");
            if (paragraph.isBlank()) continue;
            html.append("<p>").append(paragraph).append("</p>");
        }
        if (html.length() == 0) {
            return "<p></p>";
        }
        return html.toString();
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String stripHtml(String text) {
        if (text == null || text.isBlank()) return "";
        return HTML_TAG_PATTERN.matcher(text)
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

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength);
    }

    private String safeText(String text, String fallback) {
        if (text == null) return fallback;
        String normalized = text.trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    private record SceneContext(
            UUID sceneId,
            String chapterTitle,
            String chapterSummary,
            Integer chapterOrder,
            String sceneTitle,
            String sceneSummary,
            Integer sceneOrder,
            List<UUID> previousSceneIds,
            List<String> siblingSceneTitles
    ) {
    }
}
