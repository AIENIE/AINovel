package com.ainovel.app.manuscript;

import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.AiUsageContext;
import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.common.BusinessException;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.quality.SlopQualityGate;
import com.ainovel.app.quality.SlopQualityRequest;
import com.ainovel.app.quality.SlopQualityResult;
import com.ainovel.app.story.dto.OutlineSaveRequest;
import com.ainovel.app.story.model.CharacterCard;
import com.ainovel.app.story.model.Outline;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.story.repo.CharacterCardRepository;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class SceneGenerationService {
    private static final int MIN_SECTION_HAN = 2800;
    private static final int MAX_SECTION_HAN = 3200;
    private static final int MAX_GENERATION_ATTEMPTS = 3;
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    @Autowired
    private CharacterCardRepository characterCardRepository;
    @Autowired
    private AiService aiService;
    @Autowired
    private SlopQualityGate slopQualityGate;
    @Autowired
    private ScenePlotQualitySupport scenePlotQualitySupport;
    @Autowired
    private SceneGenerationPromptBuilder sceneGenerationPromptBuilder;
    @Autowired
    private ObjectMapper objectMapper;

    public record EvaluationPair(String fastText, String craftedText) {
    }

    public String generateSceneSectionHtml(Manuscript manuscript, UUID sceneId, Map<String, String> existingSections) {
        return generateSceneSectionHtml(manuscript, sceneId, existingSections, GenerationMode.FAST);
    }

    public String generateSceneSectionHtml(Manuscript manuscript, UUID sceneId, Map<String, String> existingSections,
                                            GenerationMode mode) {
        SceneGenerationContext sceneContext = resolveSceneContext(manuscript.getOutline(), sceneId);
        Story story = manuscript.getOutline().getStory();
        User owner = ownerOf(manuscript);
        List<CharacterCard> characters = characterCardRepository.findByStory(story);
        String characterContext = buildCharacterContext(characters);
        String previousContext = buildPreviousContext(sceneContext, existingSections);
        String outlinePlanning = outlinePlanning(manuscript.getOutline());

        String previousDraft = null;
        int previousCount = 0;

        for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
            var prompt = sceneGenerationPromptBuilder.build(
                    owner,
                    story,
                    sceneContext,
                    characterContext,
                    previousContext,
                    previousDraft,
                    previousCount,
                    attempt,
                    MIN_SECTION_HAN,
                    MAX_SECTION_HAN,
                    mode
            );
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
                        scenePlotQualitySupport.buildQualityRequest(
                                manuscript,
                                story,
                                sceneContext,
                                previousContext,
                                characterContext,
                                normalized
                        )
                );
                scenePlotQualitySupport.recordPlotQuality(
                        owner,
                        manuscript,
                        story,
                        sceneContext,
                        outlinePlanning,
                        previousContext,
                        characterContext,
                        qualityResult.acceptedText()
                );
                return toEditorHtml(qualityResult.acceptedText());
            }
            previousDraft = normalized;
            previousCount = hanCount;
        }

        throw new RuntimeException("场景正文生成失败：重试 " + MAX_GENERATION_ATTEMPTS
                + " 次后仍未达到 " + MIN_SECTION_HAN + "-" + MAX_SECTION_HAN
                + " 汉字（当前 " + previousCount + "）。请稍后重试。");
    }

    /**
     * Produces a fast/crafted pair for blind evaluation without changing manuscript sections,
     * versions, or plot-quality records. All billable calls share the sample reference.
     */
    public EvaluationPair generateEvaluationPair(Manuscript manuscript, UUID sceneId, UUID evaluationSampleId) {
        Map<String, String> sections = existingSections(manuscript);
        AiUsageContext baseContext = new AiUsageContext("G2_EVALUATION", String.valueOf(evaluationSampleId), "pair");
        String fastText = generateEvaluationCandidate(manuscript, sceneId, sections, GenerationMode.FAST,
                baseContext.forOperation("fast"));
        String craftedText = generateEvaluationCandidate(manuscript, sceneId, sections, GenerationMode.CRAFTED,
                baseContext.forOperation("crafted"));
        return new EvaluationPair(fastText, craftedText);
    }

    private String generateEvaluationCandidate(Manuscript manuscript,
                                               UUID sceneId,
                                               Map<String, String> existingSections,
                                               GenerationMode mode,
                                               AiUsageContext usageContext) {
        SceneGenerationContext sceneContext = resolveSceneContext(manuscript.getOutline(), sceneId);
        Story story = manuscript.getOutline().getStory();
        User owner = ownerOf(manuscript);
        String characterContext = buildCharacterContext(characterCardRepository.findByStory(story));
        String previousContext = buildPreviousContext(sceneContext, existingSections);
        String previousDraft = null;
        int previousCount = 0;

        for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
            var prompt = sceneGenerationPromptBuilder.build(
                    owner, story, sceneContext, characterContext, previousContext,
                    previousDraft, previousCount, attempt, MIN_SECTION_HAN, MAX_SECTION_HAN, mode
            );
            String raw = aiService.chat(owner, new AiChatRequest(prompt.messages(), null, null),
                    usageContext.forOperation("draft-" + attempt)).content();
            String normalized = normalizeGeneratedText(raw);
            int hanCount = countHanCharacters(normalized);
            if (hanCount > MAX_SECTION_HAN) {
                normalized = trimToHanLimit(normalized, MAX_SECTION_HAN);
                hanCount = countHanCharacters(normalized);
            }
            if (hanCount >= MIN_SECTION_HAN && hanCount <= MAX_SECTION_HAN) {
                SlopQualityResult qualityResult = slopQualityGate.evaluateAndRepair(
                        owner,
                        scenePlotQualitySupport.buildQualityRequest(
                                manuscript, story, sceneContext, previousContext, characterContext, normalized,
                                "g2_evaluation"
                        ),
                        usageContext.forOperation("quality")
                );
                return qualityResult.acceptedText();
            }
            previousDraft = normalized;
            previousCount = hanCount;
        }
        throw new RuntimeException("盲测候选生成失败：重试 " + MAX_GENERATION_ATTEMPTS
                + " 次后仍未达到 " + MIN_SECTION_HAN + "-" + MAX_SECTION_HAN + " 汉字");
    }

    private Map<String, String> existingSections(Manuscript manuscript) {
        if (manuscript == null || manuscript.getSectionsJson() == null || manuscript.getSectionsJson().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(manuscript.getSectionsJson(), new TypeReference<Map<String, String>>() {});
        } catch (Exception ex) {
            throw new BusinessException("稿件正文数据异常，无法创建盲测样本");
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

    private SceneGenerationContext resolveSceneContext(Outline outline, UUID sceneId) {
        if (outline == null) {
            throw new BusinessException("大纲不存在，无法生成正文");
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
            throw new RuntimeException("大纲内容异常，无法生成正文", ex);
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
                    return new SceneGenerationContext(
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
        throw new BusinessException("场景不存在，无法生成正文");
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

    private String buildPreviousContext(SceneGenerationContext scene, Map<String, String> existingSections) {
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

    private User ownerOf(Manuscript manuscript) {
        return manuscript.getOutline().getStory().getUser();
    }

}
