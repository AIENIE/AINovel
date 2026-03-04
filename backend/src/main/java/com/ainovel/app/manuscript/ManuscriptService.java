package com.ainovel.app.manuscript;

import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.common.RefineRequest;
import com.ainovel.app.manuscript.dto.*;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.story.model.Outline;
import com.ainovel.app.story.model.CharacterCard;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.story.dto.OutlineSaveRequest;
import com.ainovel.app.story.repo.CharacterCardRepository;
import com.ainovel.app.story.repo.OutlineRepository;
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
            String prompt = buildScenePrompt(story, sceneContext, characters, existingSections, previousDraft, previousCount, attempt);
            String raw = aiService.chat(owner, new AiChatRequest(
                    List.of(new AiChatRequest.Message("user", prompt)),
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
                return toEditorHtml(normalized);
            }
            previousDraft = normalized;
            previousCount = hanCount;
        }

        throw new RuntimeException("场景正文生成失败：重试 " + MAX_GENERATION_ATTEMPTS
                + " 次后仍未达到 " + MIN_SECTION_HAN + "-" + MAX_SECTION_HAN
                + " 汉字（当前 " + previousCount + "）。请稍后重试。");
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

    private String buildScenePrompt(Story story,
                                    SceneContext scene,
                                    List<CharacterCard> characters,
                                    Map<String, String> existingSections,
                                    String previousDraft,
                                    int previousCount,
                                    int attempt) {
        String storyTitle = safeText(story == null ? null : story.getTitle(), "未命名故事");
        String storyGenre = safeText(story == null ? null : story.getGenre(), "未指定");
        String storyTone = safeText(story == null ? null : story.getTone(), "沉浸、连贯");
        String storySynopsis = safeText(story == null ? null : story.getSynopsis(), "");

        StringBuilder characterInfo = new StringBuilder();
        if (characters != null && !characters.isEmpty()) {
            for (CharacterCard card : characters) {
                if (card == null) continue;
                characterInfo.append("- ").append(safeText(card.getName(), "角色")).append("：")
                        .append(safeText(card.getSynopsis(), ""))
                        .append(" 背景：").append(safeText(truncate(card.getDetails(), 180), ""))
                        .append(" 关系：").append(safeText(truncate(card.getRelationships(), 160), ""))
                        .append('\n');
            }
        } else {
            characterInfo.append("- 主角：围绕核心冲突持续成长\n");
        }

        StringBuilder continuity = new StringBuilder();
        if (scene.previousSceneIds() != null) {
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
        }

        String siblingSceneText = String.join(" / ", scene.siblingSceneTitles());
        String retryInstruction = "";
        if (attempt > 1) {
            String direction = previousCount < MIN_SECTION_HAN ? "扩写" : "压缩";
            retryInstruction = """

                    这是第 %d 次重试。上一版字数为 %d 汉字，请在保持剧情一致的前提下进行%s，并严格输出 %d-%d 汉字。
                    上一版草稿（可重写，不要直接复制）：
                    %s
                    """.formatted(attempt, previousCount, direction, MIN_SECTION_HAN, MAX_SECTION_HAN, truncate(previousDraft, 1200));
        }

        return """
                你是资深中文长篇小说作者，请为指定场景写出可直接进入正文编辑器的小说内容。
                输出要求：
                1) 只输出小说正文，不要解释、不要标题、不要 markdown、不要代码块。
                2) 字数严格控制在 %d-%d 汉字。
                3) 文风保持「%s」，叙事自然，场景推进明确，人物行动和心理一致。
                4) 必须承接已有剧情，不允许与前文冲突。

                故事信息：
                - 标题：%s
                - 类型：%s
                - 概要：%s

                章节信息：
                - 章节：第%s章《%s》
                - 章节摘要：%s
                - 本节：第%s节《%s》
                - 本节摘要：%s
                - 本章节次序：%s

                角色设定：
                %s

                本章其他节标题：
                %s

                已有前文：
                %s
                %s
                """.formatted(
                MIN_SECTION_HAN, MAX_SECTION_HAN,
                storyTone,
                storyTitle,
                storyGenre,
                truncate(storySynopsis, 800),
                scene.chapterOrder() <= 0 ? "?" : String.valueOf(scene.chapterOrder()),
                scene.chapterTitle(),
                truncate(scene.chapterSummary(), 300),
                scene.sceneOrder() <= 0 ? "?" : String.valueOf(scene.sceneOrder()),
                scene.sceneTitle(),
                truncate(scene.sceneSummary(), 260),
                scene.sceneOrder() <= 0 ? "?" : String.valueOf(scene.sceneOrder()),
                characterInfo,
                siblingSceneText.isBlank() ? "暂无" : siblingSceneText,
                continuity.length() == 0 ? "暂无可用前文，请按场景摘要起笔。" : continuity.toString(),
                retryInstruction
        );
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
