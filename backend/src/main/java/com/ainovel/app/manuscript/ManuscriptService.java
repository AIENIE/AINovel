package com.ainovel.app.manuscript;

import com.ainovel.app.common.BusinessException;
import com.ainovel.app.common.JsonColumnCodec;
import com.ainovel.app.common.RefineRequest;
import com.ainovel.app.manuscript.attribution.SceneGenerationAttributionService;
import com.ainovel.app.manuscript.attribution.SceneGenerationManifest;
import com.ainovel.app.manuscript.attribution.SceneGenerationSnapshotStore;
import com.ainovel.app.manuscript.attribution.SceneContentEditedEvent;
import com.ainovel.app.manuscript.context.SceneDraftContextManifest;
import com.ainovel.app.manuscript.dto.*;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.story.model.Outline;
import com.ainovel.app.story.repo.OutlineRepository;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class ManuscriptService {
    private static final String SCENE_DRAFT_PROMPT_VERSION = "scene-draft-v2";
    @Autowired
    private ManuscriptRepository manuscriptRepository;
    @Autowired
    private OutlineRepository outlineRepository;
    @Autowired
    private ResourceAccessGuard accessGuard;
    @Autowired
    private JsonColumnCodec jsonColumnCodec;
    @Autowired
    private SceneGenerationService sceneGenerationService;
    @Autowired
    private SceneGenerationSnapshotStore sceneGenerationSnapshotStore;
    @Autowired
    private SceneGenerationAttributionService sceneGenerationAttributionService;
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    public List<ManuscriptDto> listByOutline(UUID outlineId) {
        Outline outline = outlineRepository.findByIdWithStoryUser(outlineId).orElseThrow(() -> new BusinessException("大纲不存在"));
        accessGuard.assertOwner(outline.getStory().getUser());
        return manuscriptRepository.findByOutline(outline).stream().map(this::toDto).toList();
    }

    @Transactional
    public ManuscriptDto create(UUID outlineId, ManuscriptCreateRequest request) {
        Outline outline = outlineRepository.findByIdWithStoryUser(outlineId).orElseThrow(() -> new BusinessException("大纲不存在"));
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
        Manuscript manuscript = manuscriptRepository.findWithStoryById(id).orElseThrow(() -> new BusinessException("稿件不存在"));
        accessGuard.assertOwner(ownerOf(manuscript));
        return toDto(manuscript);
    }

    @Transactional
    public void delete(UUID id) {
        Manuscript manuscript = manuscriptRepository.findWithStoryById(id).orElseThrow(() -> new BusinessException("稿件不存在"));
        accessGuard.assertOwner(ownerOf(manuscript));
        manuscriptRepository.delete(manuscript);
    }

    @Transactional
    public ManuscriptDto generateForScene(UUID manuscriptId, UUID sceneId) {
        return generateForScene(manuscriptId, sceneId, GenerationMode.FAST);
    }

    @Transactional
    public ManuscriptDto generateForScene(UUID manuscriptId, UUID sceneId, GenerationMode mode) {
        Manuscript manuscript = manuscriptRepository.findWithStoryById(manuscriptId).orElseThrow(() -> new BusinessException("稿件不存在"));
        User owner = ownerOf(manuscript);
        accessGuard.assertOwner(owner);
        sceneGenerationSnapshotStore.ensureGenerationBaseline(manuscript, owner);
        Map<String, String> sections = readSectionMap(manuscript.getSectionsJson());
        SceneGenerationService.GenerationResult generation = sceneGenerationService.generateSceneSection(
                manuscript,
                sceneId,
                sections,
                mode
        );
        String generatedHtml = generation.html();
        sections.put(sceneId.toString(), generatedHtml);
        manuscript.setSectionsJson(writeJson(sections));
        manuscriptRepository.save(manuscript);
        Map<String, Object> manifest = buildGenerationManifest(sceneId, mode, generation.metadata());
        UUID generationVersionId = sceneGenerationSnapshotStore.createGenerationSnapshot(
                manuscript,
                owner,
                sceneId,
                manifest
        );
        SceneGenerationRunDto generatedRun = sceneGenerationAttributionService.recordGeneration(
                manuscript,
                sceneId,
                generationVersionId,
                mode,
                manifest,
                generatedHtml
        );
        return toDto(manuscript, new SceneGenerationRunSummaryDto(
                generatedRun.id(),
                generatedRun.generationVersionId(),
                generatedRun.status(),
                generatedRun.createdAt()
        ));
    }

    @Transactional
    public ManuscriptDto updateSection(UUID manuscriptId, UUID sceneId, SectionUpdateRequest request) {
        Manuscript manuscript = manuscriptRepository.findWithStoryById(manuscriptId).orElseThrow(() -> new BusinessException("稿件不存在"));
        accessGuard.assertOwner(ownerOf(manuscript));
        Map<String, String> sections = readSectionMap(manuscript.getSectionsJson());
        String content = request.content() == null ? "" : request.content();
        String previousContent = sections.getOrDefault(sceneId.toString(), "");
        if (Objects.equals(previousContent, content)) {
            return toDto(manuscript);
        }
        sections.put(sceneId.toString(), content);
        manuscript.setSectionsJson(writeJson(sections));
        manuscriptRepository.saveAndFlush(manuscript);
        eventPublisher.publishEvent(new SceneContentEditedEvent(manuscriptId, sceneId, content));
        Manuscript persisted = manuscriptRepository.findWithStoryById(manuscript.getId()).orElse(manuscript);
        return toDto(persisted);
    }

    public List<SceneGenerationRunDto> listSceneGenerationRuns(UUID manuscriptId, UUID sceneId, int limit) {
        Manuscript manuscript = manuscriptRepository.findWithStoryById(manuscriptId)
                .orElseThrow(() -> new BusinessException("稿件不存在"));
        accessGuard.assertOwner(ownerOf(manuscript));
        return sceneGenerationAttributionService.listRuns(manuscriptId, sceneId, limit);
    }

    @Transactional
    public SceneGenerationRunDto patchSceneGenerationFeedback(
            UUID manuscriptId,
            UUID sceneId,
            UUID runId,
            SceneGenerationFeedbackPatchRequest request
    ) {
        Manuscript manuscript = manuscriptRepository.findWithStoryById(manuscriptId)
                .orElseThrow(() -> new BusinessException("稿件不存在"));
        accessGuard.assertOwner(ownerOf(manuscript));
        return sceneGenerationAttributionService.patchFeedback(manuscriptId, sceneId, runId, request);
    }

    @Transactional
    public ManuscriptDto generateForScene(UUID sceneId) {
        Manuscript manuscript = manuscriptRepository.findAll().stream()
                .filter(m -> isCurrentUserOwner(ownerOf(m)))
                .findFirst()
                .orElseThrow(() -> new BusinessException("请先创建稿件"));
        return generateForScene(manuscript.getId(), sceneId, GenerationMode.FAST);
    }

    @Transactional
    public ManuscriptDto updateSection(UUID sectionId, SectionUpdateRequest request) {
        Manuscript manuscript = manuscriptRepository.findAll().stream()
                .filter(m -> isCurrentUserOwner(ownerOf(m)))
                .findFirst()
                .orElseThrow(() -> new BusinessException("请先创建稿件"));
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
        return toDto(manuscript, null);
    }

    private ManuscriptDto toDto(Manuscript manuscript, SceneGenerationRunSummaryDto lastGenerationRun) {
        return new ManuscriptDto(
                manuscript.getId(),
                manuscript.getOutline().getId(),
                manuscript.getTitle(),
                manuscript.getWorldId(),
                readSectionMap(manuscript.getSectionsJson()),
                lastGenerationRun,
                manuscript.getUpdatedAt()
        );
    }

    private Map<String, Object> buildGenerationManifest(
            UUID sceneId,
            GenerationMode mode,
            SceneGenerationService.GenerationMetadata generationMetadata
    ) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schemaVersion", 1);
        manifest.put("mode", (mode == null ? GenerationMode.FAST : mode).name().toLowerCase());
        manifest.put("sceneId", sceneId);
        manifest.put("generator", "SceneGenerationService");
        manifest.put("requestedAt", Instant.now());
        if (generationMetadata != null) {
            manifest.put("modelKey", generationMetadata.modelKey());
            manifest.put("promptVersion", SCENE_DRAFT_PROMPT_VERSION);
            manifest.put("promptHash", generationMetadata.promptHash());
            manifest.put("attemptCount", generationMetadata.attemptCount());
            SceneDraftContextManifest context = generationMetadata.contextManifest();
            if (context != null) {
                manifest.put("contextHash", context.contextHash());
                manifest.put("tokenBudget", context.tokenBudget());
                manifest.put("tokenUsed", context.tokenUsed());
                manifest.put("sources", context.sources().stream().map(this::contextSourceMetadata).toList());
            }
        }
        return SceneGenerationManifest.sanitize(manifest);
    }

    private Map<String, Object> contextSourceMetadata(SceneDraftContextManifest.Source source) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sourceType", source.sourceType());
        metadata.put("sourceId", source.sourceId());
        metadata.put("label", source.label());
        metadata.put("reason", source.reason());
        metadata.put("estimatedTokens", source.estimatedTokens());
        metadata.put("truncated", source.truncated());
        return metadata;
    }

    private Map<String, String> readSectionMap(String json) {
        return jsonColumnCodec.read(json, new TypeReference<>() {}, new HashMap<>());
    }

    private List<Map<String, Object>> readLogs(String json) {
        return jsonColumnCodec.read(json, new TypeReference<>() {}, new ArrayList<>());
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
        return jsonColumnCodec.write(obj, "{}");
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
}
