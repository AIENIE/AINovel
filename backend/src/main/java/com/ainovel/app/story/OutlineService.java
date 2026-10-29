package com.ainovel.app.story;

import com.ainovel.app.common.BusinessException;
import com.ainovel.app.common.JsonColumnCodec;
import com.ainovel.app.story.dto.*;
import com.ainovel.app.story.model.Outline;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.story.repo.OutlineRepository;
import com.ainovel.app.story.repo.StoryRepository;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class OutlineService {
    @Autowired
    private OutlineRepository outlineRepository;
    @Autowired
    private StoryRepository storyRepository;
    @Autowired
    private ResourceAccessGuard accessGuard;
    @Autowired
    private OutlineAiService outlineAi;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JsonColumnCodec jsonColumnCodec;
    @Autowired
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    public List<OutlineDto> listByStory(Story story) {
        accessGuard.assertOwner(story.getUser());
        return outlineRepository.findByStoryWithStoryUser(story).stream().map(this::toDto).toList();
    }

    public List<OutlineDto> listByStoryId(UUID storyId) {
        Story story = storyRepository.findByIdWithUser(storyId).orElseThrow(() -> new BusinessException("故事不存在"));
        return listByStory(story);
    }

    public OutlineDto get(UUID id) {
        Outline outline = outlineRepository.findByIdWithStoryUser(id).orElseThrow(() -> new BusinessException("大纲不存在"));
        accessGuard.assertOwner(outline.getStory().getUser());
        return toDto(outline);
    }

    @Transactional
    public OutlineDto createOutline(Story story, OutlineCreateRequest request) {
        accessGuard.assertOwner(story.getUser());
        Map<String, Object> content = new HashMap<>();
        content.put("planning", copyMap(request.planning()));
        content.put("chapters", new ArrayList<>());
        Outline outline = new Outline();
        outline.setStory(story);
        outline.setTitle(request.title() == null ? "新大纲" : request.title());
        com.ainovel.app.world.WorldBindingService.preserveLegacy(null, request.worldId());
        outline.setContentJson(writeJson(content));
        outlineRepository.save(outline);
        return toDto(outline);
    }

    @Transactional
    public OutlineDto createOutline(UUID storyId, OutlineCreateRequest request) {
        Story story = storyRepository.findByIdWithUser(storyId).orElseThrow(() -> new BusinessException("故事不存在"));
        return createOutline(story, request);
    }

    @Transactional
    public OutlineDto saveOutline(UUID outlineId, OutlineSaveRequest request) {
        Outline outline = outlineRepository.findByIdWithStoryUser(outlineId).orElseThrow(() -> new BusinessException("大纲不存在"));
        accessGuard.assertOwner(outline.getStory().getUser());
        assertRevision(outline, request.expectedRevision());
        outline.setTitle(request.title() != null ? request.title() : outline.getTitle());
        com.ainovel.app.world.WorldBindingService.preserveLegacy(outline.getWorldId(), request.worldId());
        List<OutlineSaveRequest.ChapterPayload> normalized = normalizeChapters(request.chapters());
        Map<String, Object> content = new HashMap<>();
        content.put("planning", copyMap(request.planning()));
        content.put("chapters", normalized);
        outline.setContentJson(writeJson(content));
        outlineRepository.save(outline);
        outlineRepository.flush();
        if (eventPublisher != null) eventPublisher.publishEvent(new com.ainovel.app.narrative.NarrativeSourceChanged(null, outlineId));
        return toDto(outline);
    }

    @Transactional
    public OutlineDto updateChapter(UUID chapterId, ChapterUpdateRequest request) {
        Outline outline = findOutlineContainingChapter(chapterId);
        accessGuard.assertOwner(outline.getStory().getUser());
        assertRevision(outline, request.expectedRevision());
        Map<String, Object> content = readJson(outline.getContentJson());
        List<OutlineSaveRequest.ChapterPayload> chapters = objectMapper.convertValue(
                content.getOrDefault("chapters", new ArrayList<>()),
                new TypeReference<List<OutlineSaveRequest.ChapterPayload>>() {}
        );
        List<OutlineSaveRequest.ChapterPayload> updated = new ArrayList<>();
        for (OutlineSaveRequest.ChapterPayload c : chapters) {
            if (c.id() != null && c.id().equals(chapterId)) {
                updated.add(new OutlineSaveRequest.ChapterPayload(
                        c.id(),
                        request.title() != null ? request.title() : c.title(),
                        request.summary() != null ? request.summary() : c.summary(),
                        request.order() != null ? request.order() : c.order(),
                        copyMap(c.planning()),
                        c.scenes()
                ));
            } else {
                updated.add(c);
            }
        }
        OutlineSaveRequest saveRequest = new OutlineSaveRequest(
                outline.getTitle(),
                outline.getWorldId(),
                copyMap(objectMapper.convertValue(content.get("planning"), new TypeReference<>() {})),
                normalizeChapters(updated)
        );
        return saveOutline(outline.getId(), saveRequest);
    }

    @Transactional
    public OutlineDto updateScene(UUID sceneId, SceneUpdateRequest request) {
        Outline outline = findOutlineContainingScene(sceneId);
        accessGuard.assertOwner(outline.getStory().getUser());
        assertRevision(outline, request.expectedRevision());
        Map<String, Object> content = readJson(outline.getContentJson());
        List<OutlineSaveRequest.ChapterPayload> chapters = objectMapper.convertValue(
                content.getOrDefault("chapters", new ArrayList<>()),
                new TypeReference<List<OutlineSaveRequest.ChapterPayload>>() {}
        );
        List<OutlineSaveRequest.ChapterPayload> updatedChapters = new ArrayList<>();
        for (OutlineSaveRequest.ChapterPayload c : chapters) {
            List<OutlineSaveRequest.ScenePayload> scenes = c.scenes() == null ? new ArrayList<>() : new ArrayList<>(c.scenes());
            List<OutlineSaveRequest.ScenePayload> updatedScenes = new ArrayList<>();
            for (OutlineSaveRequest.ScenePayload s : scenes) {
                if (s.id() != null && s.id().equals(sceneId)) {
                    updatedScenes.add(new OutlineSaveRequest.ScenePayload(
                            s.id(),
                            request.title() != null ? request.title() : s.title(),
                            request.summary() != null ? request.summary() : s.summary(),
                            request.content() != null ? request.content() : s.content(),
                            request.order() != null ? request.order() : s.order(),
                            copyMap(s.planning())
                    ));
                } else {
                    updatedScenes.add(s);
                }
            }
            updatedChapters.add(new OutlineSaveRequest.ChapterPayload(
                    c.id(),
                    c.title(),
                    c.summary(),
                    c.order(),
                    copyMap(c.planning()),
                    updatedScenes
            ));
        }
        OutlineSaveRequest saveRequest = new OutlineSaveRequest(
                outline.getTitle(),
                outline.getWorldId(),
                copyMap(objectMapper.convertValue(content.get("planning"), new TypeReference<>() {})),
                normalizeChapters(updatedChapters)
        );
        return saveOutline(outline.getId(), saveRequest);
    }

    @Transactional
    public void deleteOutline(UUID outlineId) {
        Outline outline = outlineRepository.findByIdWithStoryUser(outlineId).orElseThrow(() -> new BusinessException("大纲不存在"));
        accessGuard.assertOwner(outline.getStory().getUser());
        outlineRepository.delete(outline);
    }

    public OutlineDto addGeneratedChapter(UUID outlineId, OutlineChapterGenerateRequest request) {
        Outline outline=outlineRepository.findByIdWithStoryUser(outlineId).orElseThrow(()->new BusinessException("大纲不存在"));
        accessGuard.assertOwner(outline.getStory().getUser());
        var result=outlineAi.compatible(outline.getStory().getUser(),outlineId,OutlineAiService.chapterRequest(request));
        if(!"APPLIED".equals(result.status()))throw com.ainovel.app.worldcontext.WorldContextPreferences.conflict("OUTLINE_AI_RESULT_NEEDS_REVIEW");
        return get(outlineId);
    }

    private OutlineDto toDto(Outline outline) {
        Map<String, Object> content = readJson(outline.getContentJson());
        List<OutlineSaveRequest.ChapterPayload> chapters = objectMapper.convertValue(
                content.getOrDefault("chapters", new ArrayList<>()),
                new TypeReference<List<OutlineSaveRequest.ChapterPayload>>() {}
        );
        List<OutlineDto.ChapterDto> chapterDtos = chapters.stream().map(c -> new OutlineDto.ChapterDto(
                c.id() != null ? c.id() : UUID.randomUUID(),
                c.title(), c.summary(), c.order(), copyMap(c.planning()),
                c.scenes() == null ? List.of() : c.scenes().stream().map(s -> new OutlineDto.SceneDto(
                        s.id() != null ? s.id() : UUID.randomUUID(),
                        s.title(), s.summary(), s.content(), s.order(), copyMap(s.planning())
                )).toList()
        )).toList();
        return new OutlineDto(
                outline.getId(),
                outline.getStory().getId(),
                outline.getTitle(),
                outline.getWorldId(),
                copyMap(objectMapper.convertValue(content.get("planning"), new TypeReference<>() {})),
                chapterDtos,
                outline.getUpdatedAt(),
                outline.getRevision()
        );
    }

    private void assertRevision(Outline outline, Long expectedRevision) {
        if (expectedRevision != null && expectedRevision != outline.getRevision()) {
            throw new com.ainovel.app.common.ApiStatusException(org.springframework.http.HttpStatus.CONFLICT, "STORY_RESOURCE_CONFLICT");
        }
    }

    private List<OutlineSaveRequest.ChapterPayload> normalizeChapters(List<OutlineSaveRequest.ChapterPayload> chapters) {
        if (chapters == null) return new ArrayList<>();
        List<OutlineSaveRequest.ChapterPayload> normalized = new ArrayList<>();
        int chapterOrder = 1;
        for (OutlineSaveRequest.ChapterPayload c : chapters) {
            UUID chapterId = c.id() != null ? c.id() : UUID.randomUUID();
            int order = c.order() != null ? c.order() : chapterOrder;
            chapterOrder = Math.max(chapterOrder, order + 1);
            List<OutlineSaveRequest.ScenePayload> scenes = new ArrayList<>();
            if (c.scenes() != null) {
                int sceneOrder = 1;
                for (OutlineSaveRequest.ScenePayload s : c.scenes()) {
                    UUID sceneId = s.id() != null ? s.id() : UUID.randomUUID();
                    int so = s.order() != null ? s.order() : sceneOrder;
                    sceneOrder = Math.max(sceneOrder, so + 1);
                    scenes.add(new OutlineSaveRequest.ScenePayload(
                            sceneId,
                            s.title(),
                            s.summary(),
                            s.content(),
                            so,
                            normalizeScenePlanning(s.planning())
                    ));
                }
            }
            normalized.add(new OutlineSaveRequest.ChapterPayload(
                    chapterId,
                    c.title(),
                    c.summary(),
                    order,
                    copyMap(c.planning()),
                    scenes
            ));
        }
        return normalized;
    }

    private Map<String, Object> copyMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return new HashMap<>();
        }
        return new HashMap<>(source);
    }

    private Map<String, Object> normalizeScenePlanning(Map<String, Object> source) {
        Map<String, Object> planning = copyMap(source);
        com.ainovel.app.story.model.SceneLengthRange.from(planning);
        if (!planning.containsKey("sceneType") || planning.get("sceneType") == null
                || planning.get("sceneType").toString().isBlank()) {
            planning.remove("sceneType");
            return planning;
        }
        String normalized = com.ainovel.app.story.model.SceneType.parse(planning.get("sceneType"))
                .map(com.ainovel.app.story.model.SceneType::value)
                .orElseThrow(() -> new BusinessException(
                        "sceneType 必须为 action/dialogue/introspection/description/flashback"
                ));
        planning.put("sceneType", normalized);
        return planning;
    }

    private Outline findOutlineContainingChapter(UUID chapterId) {
        for (Outline outline : outlineRepository.findAllWithStoryUser()) {
            Map<String, Object> content = readJson(outline.getContentJson());
            List<Map<String, Object>> chapters = objectMapper.convertValue(content.getOrDefault("chapters", new ArrayList<>()), List.class);
            for (Map<String, Object> c : chapters) {
                Object id = c.get("id");
                if (id != null && chapterId.toString().equals(id.toString())) return outline;
            }
        }
        throw new BusinessException("章节不存在");
    }

    private Outline findOutlineContainingScene(UUID sceneId) {
        for (Outline outline : outlineRepository.findAllWithStoryUser()) {
            Map<String, Object> content = readJson(outline.getContentJson());
            List<Map<String, Object>> chapters = objectMapper.convertValue(content.getOrDefault("chapters", new ArrayList<>()), List.class);
            for (Map<String, Object> c : chapters) {
                Object scenesObj = c.get("scenes");
                if (!(scenesObj instanceof List<?> scenes)) continue;
                for (Object sObj : scenes) {
                    if (!(sObj instanceof Map<?, ?> s)) continue;
                    Object id = s.get("id");
                    if (id != null && sceneId.toString().equals(id.toString())) return outline;
                }
            }
        }
        throw new BusinessException("场景不存在");
    }

    private Map<String, Object> readJson(String json) {
        return jsonColumnCodec.read(json, new TypeReference<>() {}, new HashMap<>());
    }

    private String writeJson(Object obj) {
        return jsonColumnCodec.writeRequired(obj);
    }
}
