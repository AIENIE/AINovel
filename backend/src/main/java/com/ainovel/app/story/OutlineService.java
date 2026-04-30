package com.ainovel.app.story;

import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.story.dto.*;
import com.ainovel.app.story.model.Outline;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.story.repo.OutlineRepository;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.user.User;
import com.ainovel.app.user.UserRepository;
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
    @Autowired
    private AiService aiService;
    @Autowired
    private UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<OutlineDto> listByStory(Story story) {
        accessGuard.assertOwner(story.getUser());
        return outlineRepository.findByStoryWithStoryUser(story).stream().map(this::toDto).toList();
    }

    public OutlineDto get(UUID id) {
        Outline outline = outlineRepository.findByIdWithStoryUser(id).orElseThrow(() -> new RuntimeException("大纲不存在"));
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
        outline.setWorldId(request.worldId());
        outline.setContentJson(writeJson(content));
        outlineRepository.save(outline);
        return toDto(outline);
    }

    @Transactional
    public OutlineDto saveOutline(UUID outlineId, OutlineSaveRequest request) {
        Outline outline = outlineRepository.findByIdWithStoryUser(outlineId).orElseThrow(() -> new RuntimeException("大纲不存在"));
        accessGuard.assertOwner(outline.getStory().getUser());
        outline.setTitle(request.title() != null ? request.title() : outline.getTitle());
        outline.setWorldId(request.worldId());
        List<OutlineSaveRequest.ChapterPayload> normalized = normalizeChapters(request.chapters());
        Map<String, Object> content = new HashMap<>();
        content.put("planning", copyMap(request.planning()));
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
        Outline outline = outlineRepository.findByIdWithStoryUser(outlineId).orElseThrow(() -> new RuntimeException("大纲不存在"));
        accessGuard.assertOwner(outline.getStory().getUser());
        outlineRepository.delete(outline);
    }

    @Transactional
    public OutlineDto addGeneratedChapter(UUID outlineId, OutlineChapterGenerateRequest request) {
        Outline outline = outlineRepository.findByIdWithStoryUser(outlineId).orElseThrow(() -> new RuntimeException("大纲不存在"));
        accessGuard.assertOwner(outline.getStory().getUser());
        OutlineDto dto = toDto(outline);
        int order = dto.chapters() == null ? 1 : dto.chapters().size() + 1;
        UUID chapterId = UUID.randomUUID();
        int chapterNumber = request.chapterNumber() != null ? request.chapterNumber() : order;
        int scenesCount = normalizeSectionsPerChapter(request.sectionsPerChapter());
        GeneratedChapter generated = generateChapterByAi(outline.getStory(), chapterNumber, scenesCount, order);
        List<OutlineDto.SceneDto> scenes = new ArrayList<>();
        for (int i = 1; i <= scenesCount; i++) {
            GeneratedScene scene = i - 1 < generated.scenes().size() ? generated.scenes().get(i - 1) : null;
            String sceneTitle = scene == null ? "第" + chapterNumber + "章 第" + i + "节" : safe(scene.title(), "第" + chapterNumber + "章 第" + i + "节");
            String sceneSummary = scene == null ? "围绕章节主线推进关键冲突与人物关系。" : safe(scene.summary(), "围绕章节主线推进关键冲突与人物关系。");
            scenes.add(new OutlineDto.SceneDto(
                    UUID.randomUUID(),
                    sceneTitle,
                    sceneSummary,
                    null,
                    i,
                    Map.of(
                            "foreshadowHint", i == 1 ? "埋下一个不完整信息点" : "",
                            "misdirectionAction", i == scenesCount ? "收束前制造一次误判" : "",
                            "revealTrigger", i == scenesCount ? "在本节末尾释放新的真相碎片" : "",
                            "payoffPlan", i == scenesCount ? "为下一章的回收预留接口" : ""
                    )
            ));
        }
        OutlineDto.ChapterDto newChapter = new OutlineDto.ChapterDto(
                chapterId,
                safe(generated.title(), "第" + chapterNumber + "章"),
                safe(generated.summary(), "推进核心矛盾并强化人物成长线。"),
                order,
                Map.of(
                        "purpose", "推进核心矛盾",
                        "informationRelease", "引入新的线索或障碍",
                        "twistRole", order == 1 ? "setup" : "progression"
                ),
                scenes
        );
        List<OutlineDto.ChapterDto> updated = new ArrayList<>(dto.chapters() != null ? dto.chapters() : List.of());
        updated.add(newChapter);
        OutlineSaveRequest saveRequest = new OutlineSaveRequest(
                outline.getTitle(),
                outline.getWorldId(),
                copyMap(dto.planning()),
                updated.stream().map(c -> new OutlineSaveRequest.ChapterPayload(
                        c.id(),
                        c.title(),
                        c.summary(),
                        c.order(),
                        copyMap(c.planning()),
                        c.scenes().stream().map(s -> new OutlineSaveRequest.ScenePayload(
                                s.id(),
                                s.title(),
                                s.summary(),
                                s.content(),
                                s.order(),
                                copyMap(s.planning())
                        )).toList()
                )).toList()
        );
        return saveOutline(outlineId, saveRequest);
    }

    private int normalizeSectionsPerChapter(Integer input) {
        if (input == null) return 5;
        return Math.max(5, Math.min(7, input));
    }

    private GeneratedChapter generateChapterByAi(Story story, int chapterNumber, int scenesCount, int order) {
        try {
            User currentUser = userRepository.findByUsername(accessGuard.currentUsername()).orElse(story.getUser());
            String prompt = """
                    你是长篇小说策划编辑。请生成第 %d 章的大纲信息，必须返回 JSON，不要 markdown：
                    {
                      "title":"章节标题",
                      "summary":"章节摘要（80-140字）",
                      "scenes":[
                        {"title":"节标题","summary":"该节剧情摘要（60-120字）"}
                      ]
                    }
                    约束：
                    - scenes 数量必须为 %d
                    - 章节需与已有章节数量 %d 保持连续推进
                    - 题材：%s
                    - 故事标题：%s
                    - 故事梗概：%s
                    """.formatted(
                    chapterNumber,
                    scenesCount,
                    Math.max(order - 1, 0),
                    safe(story.getGenre(), "未指定"),
                    safe(story.getTitle(), "未命名故事"),
                    safe(story.getSynopsis(), "无")
            );
            String content = aiService.chat(currentUser, new AiChatRequest(
                    List.of(new AiChatRequest.Message("user", prompt)),
                    null,
                    null
            )).content();
            Map<String, Object> root = parseJson(content);
            if (root == null) {
                return new GeneratedChapter(null, null, List.of());
            }
            List<GeneratedScene> generatedScenes = new ArrayList<>();
            Object rawScenes = root.get("scenes");
            if (rawScenes instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        String title = safe(map.get("title") == null ? null : String.valueOf(map.get("title")), "");
                        String summary = safe(map.get("summary") == null ? null : String.valueOf(map.get("summary")), "");
                        if (!title.isBlank() || !summary.isBlank()) {
                            generatedScenes.add(new GeneratedScene(title, summary));
                        }
                    }
                }
            }
            return new GeneratedChapter(
                    safe(root.get("title") == null ? null : String.valueOf(root.get("title")), null),
                    safe(root.get("summary") == null ? null : String.valueOf(root.get("summary")), null),
                    generatedScenes
            );
        } catch (Exception ignored) {
            return new GeneratedChapter(null, null, List.of());
        }
    }

    private Map<String, Object> parseJson(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String text = raw.trim();
        if (text.startsWith("```")) {
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start >= 0 && end > start) {
                text = text.substring(start, end + 1);
            }
        }
        try {
            return objectMapper.readValue(text, new TypeReference<>() {});
        } catch (Exception ex) {
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start >= 0 && end > start) {
                try {
                    return objectMapper.readValue(text.substring(start, end + 1), new TypeReference<>() {});
                } catch (Exception ignored) {
                    return null;
                }
            }
            return null;
        }
    }

    private String safe(String value, String fallback) {
        if (value == null) return fallback;
        String text = value.trim();
        return text.isBlank() ? fallback : text;
    }

    private record GeneratedChapter(String title, String summary, List<GeneratedScene> scenes) {
    }

    private record GeneratedScene(String title, String summary) {
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
                outline.getUpdatedAt()
        );
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
                            copyMap(s.planning())
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

    private Outline findOutlineContainingChapter(UUID chapterId) {
        for (Outline outline : outlineRepository.findAllWithStoryUser()) {
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
