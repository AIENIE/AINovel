package com.ainovel.app.workflow;

import com.ainovel.app.common.BusinessException;
import com.ainovel.app.common.JsonColumnCodec;
import com.ainovel.app.story.model.CharacterCard;
import com.ainovel.app.story.model.Outline;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.story.repo.CharacterCardRepository;
import com.ainovel.app.story.repo.OutlineRepository;
import com.ainovel.app.story.repo.StoryRepository;
import com.ainovel.app.workflow.model.CreationWorkflowRun;
import com.ainovel.app.workflow.model.CreationWorkflowStatus;
import com.ainovel.app.workflow.model.GuidedCreationStep;
import com.ainovel.app.workflow.repo.CreationWorkflowRunRepository;
import com.ainovel.app.world.model.World;
import com.ainovel.app.world.repo.WorldRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class GuidedCreationMaterializer {
    private final CreationWorkflowRunRepository runRepository;
    private final StoryRepository storyRepository;
    private final WorldRepository worldRepository;
    private final CharacterCardRepository characterRepository;
    private final OutlineRepository outlineRepository;
    private final GuidedCreationJsonSupport jsonSupport;
    private final JsonColumnCodec codec;

    public GuidedCreationMaterializer(CreationWorkflowRunRepository runRepository,
                                      StoryRepository storyRepository,
                                      WorldRepository worldRepository,
                                      CharacterCardRepository characterRepository,
                                      OutlineRepository outlineRepository,
                                      GuidedCreationJsonSupport jsonSupport,
                                      JsonColumnCodec codec) {
        this.runRepository = runRepository;
        this.storyRepository = storyRepository;
        this.worldRepository = worldRepository;
        this.characterRepository = characterRepository;
        this.outlineRepository = outlineRepository;
        this.jsonSupport = jsonSupport;
        this.codec = codec;
    }

    @Transactional
    public CreationWorkflowRun confirm(UUID runId,
                                       UUID actorId,
                                       GuidedCreationStep step,
                                       String candidateId,
                                       Map<String, Object> editedPayload,
                                       Long expectedVersion,
                                       boolean automatic) {
        CreationWorkflowRun run = requireRun(runId, actorId);
        Map<String, Object> steps = jsonSupport.readSteps(run);
        Map<String, Object> stepData = jsonSupport.stepData(steps, step);
        if (run.getCurrentStep() != step) {
            if (candidateId.equals(String.valueOf(stepData.get("selectedCandidateId")))) return run;
            throw new BusinessException("当前步骤已变化，请刷新后重试");
        }
        if (expectedVersion != null && expectedVersion != run.getVersion()) {
            throw new BusinessException("向导草稿已更新，请刷新后重试");
        }
        Map<String, Object> selected;
        if (step == GuidedCreationStep.OUTLINE
                && "OUTLINE_PREVIEW".equals(String.valueOf(stepData.get("outlinePhase")))) {
            if (!candidateId.equals(String.valueOf(stepData.get("selectedDirectionId")))) {
                throw new BusinessException("完整大纲预览已经失效，请刷新后重试");
            }
            Object expanded = stepData.get("expandedOutline");
            if (!(expanded instanceof Map<?, ?> rawOutline)) {
                throw new BusinessException("请先展开完整章节大纲");
            }
            selected = new LinkedHashMap<>();
            rawOutline.forEach((key, value) -> selected.put(String.valueOf(key), value));
        } else {
            selected = new LinkedHashMap<>(jsonSupport.requireCandidate(stepData, candidateId));
        }
        if (editedPayload != null) selected.putAll(editedPayload);
        selected.put("candidateId", candidateId);

        Object entityIds = switch (step) {
            case PREMISE -> materializePremise(run, selected);
            case WORLD -> materializeWorld(run, selected);
            case CHARACTERS -> materializeCharacters(run, selected);
            case OUTLINE -> materializeOutline(run, selected);
            case COMPLETED -> throw new BusinessException("向导已经完成");
        };
        jsonSupport.recordSelection(stepData, candidateId, selected, entityIds);
        steps.put(step.name(), stepData);
        advance(run, automatic);
        jsonSupport.writeSteps(run, steps);
        return runRepository.save(run);
    }

    @Transactional
    public CreationWorkflowRun skipWorld(UUID runId, UUID actorId, Long expectedVersion, boolean automatic) {
        CreationWorkflowRun run = requireRun(runId, actorId);
        if (run.getCurrentStep() != GuidedCreationStep.WORLD) {
            throw new BusinessException("只有世界观步骤可以跳过");
        }
        if (expectedVersion != null && expectedVersion != run.getVersion()) {
            throw new BusinessException("向导草稿已更新，请刷新后重试");
        }
        Map<String, Object> steps = jsonSupport.readSteps(run);
        Map<String, Object> stepData = jsonSupport.stepData(steps, GuidedCreationStep.WORLD);
        stepData.put("skipped", true);
        stepData.put("confirmedAt", Instant.now().toString());
        steps.put(GuidedCreationStep.WORLD.name(), stepData);
        advance(run, automatic);
        jsonSupport.writeSteps(run, steps);
        return runRepository.save(run);
    }

    private CreationWorkflowRun requireRun(UUID runId, UUID actorId) {
        CreationWorkflowRun run = runRepository.findByIdForUpdate(runId)
                .orElseThrow(() -> new BusinessException("向导草稿不存在"));
        if (actorId != null && !actorId.equals(run.getUser().getId())) {
            throw new BusinessException("无权访问该向导草稿");
        }
        return run;
    }

    private UUID materializePremise(CreationWorkflowRun run, Map<String, Object> selected) {
        if (run.getStory() != null) return run.getStory().getId();
        Story story = new Story();
        story.setUser(run.getUser());
        story.setTitle(requiredText(selected, "title", 255));
        story.setSynopsis(text(selected, "synopsis", 12000));
        story.setGenre(fallback(text(selected, "genre", 100), run.getGenre()));
        story.setTone(fallback(text(selected, "tone", 100), run.getTone()));
        story.setStatus("draft");
        run.setStory(storyRepository.save(story));
        return run.getStory().getId();
    }

    @SuppressWarnings("unchecked")
    private UUID materializeWorld(CreationWorkflowRun run, Map<String, Object> selected) {
        requireStory(run);
        if (run.getWorld() != null) return run.getWorld().getId();
        World world = new World();
        world.setUser(run.getUser());
        world.setName(requiredText(selected, "name", 255));
        world.setTagline(text(selected, "tagline", 255));
        world.setStatus("draft");
        world.setVersion("0.1.0");
        world.setThemesJson(codec.write(selected.getOrDefault("themes", List.of()), "[]"));
        world.setCreativeIntent(text(selected, "creativeIntent", 12000));
        world.setNotes(text(selected, "notes", 12000));
        Object rawModules = selected.get("modules");
        Map<String, Object> modules = rawModules instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map) : new LinkedHashMap<>();
        world.setModulesJson(codec.write(modules, "{}"));
        Map<String, String> progress = new LinkedHashMap<>();
        modules.keySet().forEach(key -> progress.put(key, "COMPLETED"));
        world.setModuleProgressJson(codec.write(progress, "{}"));
        run.setWorld(worldRepository.save(world));
        run.getStory().setWorldId(run.getWorld().getId().toString());
        storyRepository.save(run.getStory());
        return run.getWorld().getId();
    }

    @SuppressWarnings("unchecked")
    private List<UUID> materializeCharacters(CreationWorkflowRun run, Map<String, Object> selected) {
        Story story = requireStory(run);
        Object rawCharacters = selected.get("characters");
        if (!(rawCharacters instanceof List<?> list) || list.size() < 3 || list.size() > 5) {
            throw new BusinessException("每套角色阵容必须包含 3-5 名角色");
        }
        List<CharacterCard> cards = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) throw new BusinessException("角色格式无效");
            Map<String, Object> character = (Map<String, Object>) raw;
            CharacterCard card = new CharacterCard();
            card.setStory(story);
            card.setName(requiredText(character, "name", 255));
            card.setSynopsis(text(character, "synopsis", 12000));
            card.setDetails(text(character, "details", 12000));
            card.setRelationships(text(character, "relationships", 12000));
            cards.add(card);
        }
        return characterRepository.saveAll(cards).stream().map(CharacterCard::getId).toList();
    }

    @SuppressWarnings("unchecked")
    private UUID materializeOutline(CreationWorkflowRun run, Map<String, Object> selected) {
        Story story = requireStory(run);
        if (run.getOutline() != null) return run.getOutline().getId();
        Object rawChapters = selected.get("chapters");
        if (!(rawChapters instanceof List<?> chapters)
                || chapters.size() != run.getTargetChapterCount()) {
            throw new BusinessException("大纲必须包含 " + run.getTargetChapterCount() + " 章");
        }
        List<Map<String, Object>> normalizedChapters = new ArrayList<>();
        int chapterOrder = 1;
        for (Object item : chapters) {
            if (!(item instanceof Map<?, ?> raw)) throw new BusinessException("章节格式无效");
            Map<String, Object> chapter = new LinkedHashMap<>((Map<String, Object>) raw);
            Object rawScenes = chapter.get("scenes");
            if (!(rawScenes instanceof List<?> scenes) || scenes.size() < 2 || scenes.size() > 4) {
                throw new BusinessException("每章必须包含 2-4 个场景");
            }
            List<Map<String, Object>> normalizedScenes = new ArrayList<>();
            int sceneOrder = 1;
            for (Object sceneItem : scenes) {
                if (!(sceneItem instanceof Map<?, ?> sceneRaw)) throw new BusinessException("场景格式无效");
                Map<String, Object> scene = new LinkedHashMap<>((Map<String, Object>) sceneRaw);
                scene.put("id", UUID.randomUUID().toString());
                scene.put("order", sceneOrder++);
                scene.putIfAbsent("content", "");
                normalizedScenes.add(scene);
            }
            chapter.put("id", UUID.randomUUID().toString());
            chapter.put("order", chapterOrder++);
            chapter.put("scenes", normalizedScenes);
            normalizedChapters.add(chapter);
        }
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("planning", selected.getOrDefault("planning", Map.of()));
        content.put("chapters", normalizedChapters);
        Outline outline = new Outline();
        outline.setStory(story);
        outline.setTitle(requiredText(selected, "title", 255));
        outline.setWorldId(run.getWorld() == null ? null : run.getWorld().getId().toString());
        outline.setContentJson(codec.write(content, "{\"planning\":{},\"chapters\":[]}"));
        run.setOutline(outlineRepository.save(outline));
        return run.getOutline().getId();
    }

    private Story requireStory(CreationWorkflowRun run) {
        if (run.getStory() == null) throw new BusinessException("请先确认故事方向");
        return run.getStory();
    }

    private void advance(CreationWorkflowRun run, boolean automatic) {
        GuidedCreationStep next = run.getCurrentStep().next();
        run.setCurrentStep(next);
        run.setActiveJobId(null);
        run.setErrorMessage(null);
        if (next == GuidedCreationStep.COMPLETED) {
            run.setStatus(CreationWorkflowStatus.COMPLETED);
            run.setCompletedAt(Instant.now());
        } else {
            run.setStatus(automatic ? CreationWorkflowStatus.AUTO_RUNNING : CreationWorkflowStatus.WAITING_USER);
        }
    }

    private String requiredText(Map<String, Object> map, String key, int maxLength) {
        String value = text(map, key, maxLength);
        if (value.isBlank()) throw new BusinessException("缺少必填字段：" + key);
        return value;
    }

    private String text(Map<String, Object> map, String key, int maxLength) {
        Object raw = map.get(key);
        String value = raw == null ? "" : String.valueOf(raw).trim();
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
