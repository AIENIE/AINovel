package com.ainovel.app.quality;

import com.ainovel.app.common.JsonColumnCodec;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.quality.dto.PlotQualityRunDto;
import com.ainovel.app.quality.model.PlotQualityRun;
import com.ainovel.app.quality.repo.PlotQualityRunRepository;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.story.model.CharacterCard;
import com.ainovel.app.story.model.Outline;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.story.repo.CharacterCardRepository;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.regex.Pattern;

@Tag(name = "V2", description = "AINovel v2 and quality APIs")
@RestController
@RequestMapping("/v2")
public class PlotQualityController {
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private final ResourceAccessGuard accessGuard;
    private final ManuscriptRepository manuscriptRepository;
    private final CharacterCardRepository characterCardRepository;
    private final PlotQualityRunRepository runRepository;
    private final PlotQualityService plotQualityService;
    private final ObjectMapper objectMapper;
    private final JsonColumnCodec jsonColumnCodec;

    public PlotQualityController(ResourceAccessGuard accessGuard,
                                 ManuscriptRepository manuscriptRepository,
                                 CharacterCardRepository characterCardRepository,
                                 PlotQualityRunRepository runRepository,
                                 PlotQualityService plotQualityService,
                                 ObjectMapper objectMapper,
                                 JsonColumnCodec jsonColumnCodec) {
        this.accessGuard = accessGuard;
        this.manuscriptRepository = manuscriptRepository;
        this.characterCardRepository = characterCardRepository;
        this.runRepository = runRepository;
        this.plotQualityService = plotQualityService;
        this.objectMapper = objectMapper;
        this.jsonColumnCodec = jsonColumnCodec;
    }

    @Operation(summary = "v2 API endpoint")

    @GetMapping("/manuscripts/{manuscriptId}/plot-quality-runs")
    public List<PlotQualityRunDto> listRuns(@AuthenticationPrincipal UserDetails principal,
                                            @PathVariable UUID manuscriptId,
                                            @RequestParam(required = false) UUID sceneId) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedManuscript(manuscriptId, user);
        List<PlotQualityRun> runs = sceneId == null
                ? runRepository.findTop20ByManuscriptIdOrderByCreatedAtDesc(manuscriptId)
                : runRepository.findTop20ByManuscriptIdAndSceneIdOrderByCreatedAtDesc(manuscriptId, sceneId);
        return runs.stream().map(PlotQualityMapper::toDto).toList();
    }

    @Operation(summary = "v2 API endpoint")

    @PostMapping("/manuscripts/{manuscriptId}/scenes/{sceneId}/plot-quality-runs")
    public PlotQualityRunDto analyzeScene(@AuthenticationPrincipal UserDetails principal,
                                          @PathVariable UUID manuscriptId,
                                          @PathVariable UUID sceneId) {
        User user = accessGuard.currentUser(principal);
        Manuscript manuscript = accessGuard.requireOwnedManuscript(manuscriptId, user);
        return PlotQualityMapper.toDto(plotQualityService.analyze(user, buildRequest(manuscript, sceneId)));
    }

    @Operation(summary = "v2 API endpoint")

    @GetMapping("/manuscripts/{manuscriptId}/plot-quality-trends")
    public PlotQualityTrend trend(@AuthenticationPrincipal UserDetails principal,
                                  @PathVariable UUID manuscriptId) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedManuscript(manuscriptId, user);
        return plotQualityService.buildTrend(manuscriptId);
    }

    @Operation(summary = "v2 API endpoint")

    @PostMapping("/manuscripts/{manuscriptId}/plot-quality-runs/{runId}/revision-candidate")
    public PlotQualityRunDto generateRevisionCandidate(@AuthenticationPrincipal UserDetails principal,
                                                       @PathVariable UUID manuscriptId,
                                                       @PathVariable UUID runId) {
        User user = accessGuard.currentUser(principal);
        Manuscript manuscript = accessGuard.requireOwnedManuscript(manuscriptId, user);
        return PlotQualityMapper.toDto(plotQualityService.generateRevisionCandidate(user, manuscript, runId));
    }

    @Operation(summary = "v2 API endpoint")

    @PostMapping("/manuscripts/{manuscriptId}/plot-quality-runs/{runId}/apply-revision")
    public PlotQualityRunDto applyRevision(@AuthenticationPrincipal UserDetails principal,
                                           @PathVariable UUID manuscriptId,
                                           @PathVariable UUID runId) {
        User user = accessGuard.currentUser(principal);
        Manuscript manuscript = accessGuard.requireOwnedManuscript(manuscriptId, user);
        PlotQualityRun run = plotQualityService.applyRevision(user, manuscript, runId);
        manuscriptRepository.save(manuscript);
        return PlotQualityMapper.toDto(run);
    }

    public PlotQualityRequest buildRequest(Manuscript manuscript, UUID sceneId) {
        Outline outline = manuscript.getOutline();
        Story story = outline.getStory();
        Map<String, String> sections = readSectionMap(manuscript.getSectionsJson());
        SceneContext scene = resolveScene(outline, sceneId);
        return new PlotQualityRequest(
                story.getId(),
                manuscript.getId(),
                sceneId,
                safe(story.getTitle(), "未命名故事"),
                safe(story.getGenre(), "未指定"),
                safe(story.getTone(), "沉浸、连贯"),
                scene.chapterTitle(),
                scene.chapterOrder(),
                scene.sceneTitle(),
                scene.sceneOrder(),
                scene.sceneSummary(),
                outlinePlanning(outline),
                previousContext(scene, sections),
                characterContext(characterCardRepository.findByStory(story)),
                stripHtml(sections.get(sceneId.toString()))
        );
    }

    private SceneContext resolveScene(Outline outline, UUID sceneId) {
        Map<String, Object> root = readObjectMap(outline.getContentJson());
        List<Map<String, Object>> chapters = listOfMap(root.get("chapters"));
        for (int chapterIndex = 0; chapterIndex < chapters.size(); chapterIndex++) {
            Map<String, Object> chapter = chapters.get(chapterIndex);
            List<Map<String, Object>> scenes = listOfMap(chapter.get("scenes"));
            List<UUID> previousSceneIds = new ArrayList<>();
            for (int sceneIndex = 0; sceneIndex < scenes.size(); sceneIndex++) {
                Map<String, Object> scene = scenes.get(sceneIndex);
                UUID id = uuid(scene.get("id"));
                if (id != null && id.equals(sceneId)) {
                    return new SceneContext(
                            safe(str(chapter.get("title"), ""), "未命名章节"),
                            intVal(chapter.get("order"), chapterIndex + 1),
                            safe(str(scene.get("title"), ""), "未命名场景"),
                            intVal(scene.get("order"), sceneIndex + 1),
                            safe(str(scene.get("summary"), ""), ""),
                            previousSceneIds
                    );
                }
                if (id != null) {
                    previousSceneIds.add(id);
                }
            }
        }
        throw new RuntimeException("场景不存在，无法进行剧情诊断");
    }

    private String outlinePlanning(Outline outline) {
        Map<String, Object> root = readObjectMap(outline.getContentJson());
        Object planning = root.get("planning");
        if (planning == null) {
            return "暂无结构规划。";
        }
        try {
            return objectMapper.writeValueAsString(planning);
        } catch (Exception ex) {
            return String.valueOf(planning);
        }
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
        List<Map<String, Object>> out = new ArrayList<>();
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

    private int intVal(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ex) {
            return fallback;
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
            int chapterOrder,
            String sceneTitle,
            int sceneOrder,
            String sceneSummary,
            List<UUID> previousSceneIds
    ) {
    }
}
