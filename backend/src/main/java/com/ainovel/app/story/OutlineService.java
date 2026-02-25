package com.ainovel.app.story;

import com.ainovel.app.story.dto.*;
import com.ainovel.app.story.model.Outline;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.story.repo.OutlineRepository;
import com.ainovel.app.security.ResourceAccessGuard;
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
    private ResourceAccessGuard accessGuard;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<OutlineDto> listByStory(Story story) {
        accessGuard.assertOwner(story.getUser());
        return outlineRepository.findByStory(story).stream().map(this::toDto).toList();
    }

    public OutlineDto get(UUID id) {
        Outline outline = outlineRepository.findById(id).orElseThrow(() -> new RuntimeException("大纲不存在"));
        accessGuard.assertOwner(outline.getStory().getUser());
        return toDto(outline);
    }

    @Transactional
    public OutlineDto createOutline(Story story, OutlineCreateRequest request) {
        accessGuard.assertOwner(story.getUser());
        Map<String, Object> content = new HashMap<>();
        content.put("chapters", new ArrayList<>());
        Outline outline = new Outline();
        outline.setStory(story);
        outline.setTitle(request.title() == null ? "新大纲" : request.title());
        outline.setWorldId(request.worldId());
        outline.setContentJson(writeJson(content));
        outlineRepository.save(outline);
        return toDto(outline);
    }

    @Transactional
    public OutlineDto saveOutline(UUID outlineId, OutlineSaveRequest request) {
        Outline outline = outlineRepository.findById(outlineId).orElseThrow(() -> new RuntimeException("大纲不存在"));
        accessGuard.assertOwner(outline.getStory().getUser());
        outline.setTitle(request.title() != null ? request.title() : outline.getTitle());
        outline.setWorldId(request.worldId());
        List<OutlineSaveRequest.ChapterPayload> normalized = normalizeChapters(request.chapters());
        Map<String, Object> content = new HashMap<>();
        content.put("chapters", normalized);
        outline.setContentJson(writeJson(content));
        outlineRepository.save(outline);
        return toDto(outline);
    }

    @Transactional
    public OutlineDto updateChapter(UUID chapterId, ChapterUpdateRequest request) {
        Outline outline = findOutlineContainingChapter(chapterId);
        accessGuard.assertOwner(outline.getStory().getUser());
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
                        c.scenes()
                ));
            } else {
                updated.add(c);
            }
        }
        OutlineSaveRequest saveRequest = new OutlineSaveRequest(outline.getTitle(), outline.getWorldId(), normalizeChapters(updated));
        return saveOutline(outline.getId(), saveRequest);
    }

    @Transactional
    public OutlineDto updateScene(UUID sceneId, SceneUpdateRequest request) {
        Outline outline = findOutlineContainingScene(sceneId);
        accessGuard.assertOwner(outline.getStory().getUser());
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
                            request.order() != null ? request.order() : s.order()
                    ));
                } else {
                    updatedScenes.add(s);
                }
            }
            updatedChapters.add(new OutlineSaveRequest.ChapterPayload(c.id(), c.title(), c.summary(), c.order(), updatedScenes));
        }
        OutlineSaveRequest saveRequest = new OutlineSaveRequest(outline.getTitle(), outline.getWorldId(), normalizeChapters(updatedChapters));
        return saveOutline(outline.getId(), saveRequest);
    }

    @Transactional
    public void deleteOutline(UUID outlineId) {
        Outline outline = outlineRepository.findById(outlineId).orElseThrow(() -> new RuntimeException("大纲不存在"));
        accessGuard.assertOwner(outline.getStory().getUser());
        outlineRepository.delete(outline);
    }

    @Transactional
    public OutlineDto addGeneratedChapter(UUID outlineId, OutlineChapterGenerateRequest request) {
        Outline outline = outlineRepository.findById(outlineId).orElseThrow(() -> new RuntimeException("大纲不存在"));
        accessGuard.assertOwner(outline.getStory().getUser());
        OutlineDto dto = toDto(outline);
        int order = dto.chapters() == null ? 1 : dto.chapters().size() + 1;
        UUID chapterId = UUID.randomUUID();
        List<OutlineDto.SceneDto> scenes = new ArrayList<>();
        int scenesCount = request.sectionsPerChapter() != null ? request.sectionsPerChapter() : 2;
        for (int i = 1; i <= scenesCount; i++) {
            scenes.add(new OutlineDto.SceneDto(UUID.randomUUID(), "场景 " + i, "根据第 " + i + " 段生成的摘要", null, i));
        }
        OutlineDto.ChapterDto newChapter = new OutlineDto.ChapterDto(
                chapterId,
                "第" + (request.chapterNumber() != null ? request.chapterNumber() : order) + "章",
                "AI 生成的章节摘要",
                order,
                scenes
        );
        List<OutlineDto.ChapterDto> updated = new ArrayList<>(dto.chapters() != null ? dto.chapters() : List.of());
        updated.add(newChapter);
        OutlineSaveRequest saveRequest = new OutlineSaveRequest(outline.getTitle(), outline.getWorldId(),
                updated.stream().map(c -> new OutlineSaveRequest.ChapterPayload(c.id(), c.title(), c.summary(), c.order(),
                        c.scenes().stream().map(s -> new OutlineSaveRequest.ScenePayload(s.id(), s.title(), s.summary(), s.content(), s.order())).toList())).toList());
        return saveOutline(outlineId, saveRequest);
    }

    private OutlineDto toDto(Outline outline) {
        Map<String, Object> content = readJson(outline.getContentJson());
        List<OutlineSaveRequest.ChapterPayload> chapters = objectMapper.convertValue(
                content.getOrDefault("chapters", new ArrayList<>()),
                new TypeReference<List<OutlineSaveRequest.ChapterPayload>>() {}
        );
        List<OutlineDto.ChapterDto> chapterDtos = chapters.stream().map(c -> new OutlineDto.ChapterDto(
                c.id() != null ? c.id() : UUID.randomUUID(),
                c.title(), c.summary(), c.order(),
                c.scenes() == null ? List.of() : c.scenes().stream().map(s -> new OutlineDto.SceneDto(
                        s.id() != null ? s.id() : UUID.randomUUID(),
                        s.title(), s.summary(), s.content(), s.order()
                )).toList()
        )).toList();
        return new OutlineDto(outline.getId(), outline.getStory().getId(), outline.getTitle(), outline.getWorldId(), chapterDtos, outline.getUpdatedAt());
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
                    scenes.add(new OutlineSaveRequest.ScenePayload(sceneId, s.title(), s.summary(), s.content(), so));
                }
            }
            normalized.add(new OutlineSaveRequest.ChapterPayload(chapterId, c.title(), c.summary(), order, scenes));
        }
        return normalized;
    }

    private Outline findOutlineContainingChapter(UUID chapterId) {
        for (Outline outline : outlineRepository.findAll()) {
            Map<String, Object> content = readJson(outline.getContentJson());
            List<Map<String, Object>> chapters = objectMapper.convertValue(content.getOrDefault("chapters", new ArrayList<>()), List.class);
            for (Map<String, Object> c : chapters) {
                Object id = c.get("id");
                if (id != null && chapterId.toString().equals(id.toString())) return outline;
            }
        }
        throw new RuntimeException("章节不存在");
    }

    private Outline findOutlineContainingScene(UUID sceneId) {
        for (Outline outline : outlineRepository.findAll()) {
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
        throw new RuntimeException("场景不存在");
    }

    private Map<String, Object> readJson(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
