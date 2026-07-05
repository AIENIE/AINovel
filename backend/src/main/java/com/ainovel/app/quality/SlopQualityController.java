package com.ainovel.app.quality;

import com.ainovel.app.common.JsonColumnCodec;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.quality.dto.SlopQualityRunDto;
import com.ainovel.app.quality.repo.SlopQualityRunRepository;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.story.model.CharacterCard;
import com.ainovel.app.story.model.Outline;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.story.repo.CharacterCardRepository;
import com.ainovel.app.style.StyleContextProvider;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Tag(name = "V2", description = "AINovel v2 and quality APIs")
@RestController
@RequestMapping("/v2")
public class SlopQualityController {
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    private final ResourceAccessGuard accessGuard;
    private final SlopQualityRunRepository runRepository;
    private final CharacterCardRepository characterCardRepository;
    private final SlopDiagnosticService diagnosticService;
    private final StyleContextProvider styleContextProvider;
    private final ObjectMapper objectMapper;
    private final JsonColumnCodec jsonColumnCodec;

    public SlopQualityController(ResourceAccessGuard accessGuard,
                                 SlopQualityRunRepository runRepository,
                                 CharacterCardRepository characterCardRepository,
                                 SlopDiagnosticService diagnosticService,
                                 StyleContextProvider styleContextProvider,
                                 ObjectMapper objectMapper,
                                 JsonColumnCodec jsonColumnCodec) {
        this.accessGuard = accessGuard;
        this.runRepository = runRepository;
        this.characterCardRepository = characterCardRepository;
        this.diagnosticService = diagnosticService;
        this.styleContextProvider = styleContextProvider;
        this.objectMapper = objectMapper;
        this.jsonColumnCodec = jsonColumnCodec;
    }

    @Operation(summary = "v2 API endpoint")

    @GetMapping("/manuscripts/{manuscriptId}/quality-runs")
    public List<SlopQualityRunDto> listRuns(@AuthenticationPrincipal UserDetails principal,
                                            @PathVariable UUID manuscriptId,
                                            @RequestParam(required = false) UUID sceneId) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedManuscript(manuscriptId, user);
        if (sceneId != null) {
            return runRepository.findTop20ByManuscriptIdAndSceneIdOrderByCreatedAtDesc(manuscriptId, sceneId)
                    .stream()
                    .map(SlopQualityMapper::toDto)
                    .toList();
        }
        return runRepository.findTop20ByManuscriptIdOrderByCreatedAtDesc(manuscriptId)
                .stream()
                .map(SlopQualityMapper::toDto)
                .toList();
    }

    @Operation(summary = "v2 API endpoint")
    @PostMapping("/manuscripts/{manuscriptId}/scenes/{sceneId}/quality-runs")
    public SlopQualityRunDto analyzeScene(@AuthenticationPrincipal UserDetails principal,
                                          @PathVariable UUID manuscriptId,
                                          @PathVariable UUID sceneId) {
        User user = accessGuard.currentUser(principal);
        Manuscript manuscript = accessGuard.requireOwnedManuscript(manuscriptId, user);
        return SlopQualityMapper.toDto(diagnosticService.analyze(user, buildRequest(manuscript, sceneId)));
    }

    private SlopQualityRequest buildRequest(Manuscript manuscript, UUID sceneId) {
        Outline outline = manuscript.getOutline();
        Story story = outline.getStory();
        Map<String, String> sections = readSectionMap(manuscript.getSectionsJson());
        SceneContext scene = resolveScene(outline, sceneId);
        return new SlopQualityRequest(
                story.getId(),
                manuscript.getId(),
                sceneId,
                safe(story.getTitle(), "未命名故事"),
                safe(story.getGenre(), "未指定"),
                safe(story.getTone(), "沉浸、连贯"),
                scene.chapterTitle(),
                scene.sceneTitle(),
                scene.sceneSummary(),
                previousContext(scene, sections),
                characterContext(characterCardRepository.findByStory(story)),
                styleContextProvider.buildSlopContext(story),
                stripHtml(sections.get(sceneId.toString()))
        );
    }

    private SceneContext resolveScene(Outline outline, UUID sceneId) {
        Map<String, Object> root = readObjectMap(outline.getContentJson());
        List<Map<String, Object>> chapters = listOfMap(root.get("chapters"));
        for (int chapterIndex = 0; chapterIndex < chapters.size(); chapterIndex++) {
            Map<String, Object> chapter = chapters.get(chapterIndex);
            List<Map<String, Object>> scenes = listOfMap(chapter.get("scenes"));
            List<UUID> previousSceneIds = new java.util.ArrayList<>();
            for (int sceneIndex = 0; sceneIndex < scenes.size(); sceneIndex++) {
                Map<String, Object> scene = scenes.get(sceneIndex);
                UUID id = uuid(scene.get("id"));
                if (id != null && id.equals(sceneId)) {
                    return new SceneContext(
                            safe(str(chapter.get("title"), ""), "未命名章节"),
                            safe(str(scene.get("title"), ""), "未命名场景"),
                            safe(str(scene.get("summary"), ""), ""),
                            previousSceneIds
                    );
                }
                if (id != null) {
                    previousSceneIds.add(id);
                }
            }
        }
        throw new RuntimeException("场景不存在，无法进行文本 slop 诊断");
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
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                out.add(new HashMap<>((Map<String, Object>) map));
            }
        }
        return out;
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

    private record SceneContext(
            String chapterTitle,
            String sceneTitle,
            String sceneSummary,
            List<UUID> previousSceneIds
    ) {
    }
}
