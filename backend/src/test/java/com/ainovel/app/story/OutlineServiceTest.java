package com.ainovel.app.story;

import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.dto.AiChatResponse;
import com.ainovel.app.common.JsonColumnCodec;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.story.dto.ChapterUpdateRequest;
import com.ainovel.app.story.dto.OutlineChapterGenerateRequest;
import com.ainovel.app.story.dto.OutlineDto;
import com.ainovel.app.story.dto.OutlineSaveRequest;
import com.ainovel.app.story.dto.SceneUpdateRequest;
import com.ainovel.app.story.model.Outline;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.story.repo.OutlineRepository;
import com.ainovel.app.story.repo.StoryRepository;
import com.ainovel.app.user.User;
import com.ainovel.app.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutlineServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonColumnCodec jsonColumnCodec = new JsonColumnCodec(objectMapper);

    @Test
    void saveOutlineShouldNormalizeNestedChapterTree() {
        OutlineRepository outlineRepository = mock(OutlineRepository.class);
        ResourceAccessGuard accessGuard = mock(ResourceAccessGuard.class);
        OutlineService service = service(outlineRepository, mock(StoryRepository.class), accessGuard, mock(AiService.class), mock(UserRepository.class));
        User owner = user("outline_author");
        Outline outline = outline(outlineId(), story(owner), Map.of("planning", Map.of(), "chapters", List.of()));

        when(outlineRepository.findByIdWithStoryUser(outline.getId())).thenReturn(Optional.of(outline));
        when(outlineRepository.save(any(Outline.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OutlineDto dto = service.saveOutline(outline.getId(), new OutlineSaveRequest(
                "第一卷大纲",
                "world-2",
                Map.of("goal", "升级悬念"),
                List.of(new OutlineSaveRequest.ChapterPayload(
                        null,
                        "第一章",
                        "暴雨夜的尸体",
                        null,
                        Map.of("beat", "setup"),
                        List.of(new OutlineSaveRequest.ScenePayload(
                                null,
                                "雨巷追迹",
                                "主角发现第一条误导线索",
                                null,
                                null,
                                Map.of("focus", "线索")
                        ))
                ))
        ));

        assertEquals("第一卷大纲", dto.title());
        assertEquals("world-2", dto.worldId());
        assertEquals("升级悬念", dto.planning().get("goal"));
        assertEquals(1, dto.chapters().size());
        OutlineDto.ChapterDto chapter = dto.chapters().get(0);
        assertNotNull(chapter.id());
        assertEquals(1, chapter.order());
        assertEquals("setup", chapter.planning().get("beat"));
        assertEquals(1, chapter.scenes().size());
        OutlineDto.SceneDto scene = chapter.scenes().get(0);
        assertNotNull(scene.id());
        assertEquals(1, scene.order());
        assertEquals("线索", scene.planning().get("focus"));
        assertTrue(outline.getContentJson().contains("第一章"));
        verify(accessGuard).assertOwner(owner);
        verify(outlineRepository).save(outline);
    }

    @Test
    void updateChapterShouldKeepUntouchedFieldsAndRefreshRequestedOnes() {
        OutlineRepository outlineRepository = mock(OutlineRepository.class);
        ResourceAccessGuard accessGuard = mock(ResourceAccessGuard.class);
        OutlineService service = service(outlineRepository, mock(StoryRepository.class), accessGuard, mock(AiService.class), mock(UserRepository.class));
        User owner = user("chapter_editor");
        UUID chapterId = UUID.randomUUID();
        UUID sceneId = UUID.randomUUID();
        Outline outline = outline(outlineId(), story(owner), Map.of(
                "planning", Map.of("arc", "phase-1"),
                "chapters", List.of(new OutlineSaveRequest.ChapterPayload(
                        chapterId,
                        "旧章名",
                        "旧摘要",
                        2,
                        Map.of("purpose", "铺垫"),
                        List.of(new OutlineSaveRequest.ScenePayload(sceneId, "场景一", "摘要一", null, 1, Map.of("tone", "cold")))
                ))
        ));

        when(outlineRepository.findAllWithStoryUser()).thenReturn(List.of(outline));
        when(outlineRepository.findByIdWithStoryUser(outline.getId())).thenReturn(Optional.of(outline));
        when(outlineRepository.save(any(Outline.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OutlineDto dto = service.updateChapter(chapterId, new ChapterUpdateRequest("新章名", null, 7));

        OutlineDto.ChapterDto updated = dto.chapters().get(0);
        assertEquals(chapterId, updated.id());
        assertEquals("新章名", updated.title());
        assertEquals("旧摘要", updated.summary());
        assertEquals(7, updated.order());
        assertEquals("铺垫", updated.planning().get("purpose"));
        assertEquals(sceneId, updated.scenes().get(0).id());
        verify(accessGuard, times(2)).assertOwner(owner);
    }

    @Test
    void updateSceneShouldRewriteOnlyTargetScene() {
        OutlineRepository outlineRepository = mock(OutlineRepository.class);
        ResourceAccessGuard accessGuard = mock(ResourceAccessGuard.class);
        OutlineService service = service(outlineRepository, mock(StoryRepository.class), accessGuard, mock(AiService.class), mock(UserRepository.class));
        User owner = user("scene_editor");
        UUID targetSceneId = UUID.randomUUID();
        UUID siblingSceneId = UUID.randomUUID();
        Outline outline = outline(outlineId(), story(owner), Map.of(
                "planning", Map.of("arc", "phase-2"),
                "chapters", List.of(new OutlineSaveRequest.ChapterPayload(
                        UUID.randomUUID(),
                        "第二章",
                        "章摘要",
                        2,
                        Map.of("purpose", "推进"),
                        List.of(
                                new OutlineSaveRequest.ScenePayload(siblingSceneId, "旧前置场景", "前置摘要", null, 1, Map.of("beat", "prep")),
                                new OutlineSaveRequest.ScenePayload(targetSceneId, "旧目标场景", "旧目标摘要", "<p>旧内容</p>", 2, Map.of("beat", "hit"))
                        )
                ))
        ));

        when(outlineRepository.findAllWithStoryUser()).thenReturn(List.of(outline));
        when(outlineRepository.findByIdWithStoryUser(outline.getId())).thenReturn(Optional.of(outline));
        when(outlineRepository.save(any(Outline.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OutlineDto dto = service.updateScene(targetSceneId, new SceneUpdateRequest(null, "新场景摘要", "<p>新内容</p>", 5));

        OutlineDto.SceneDto sibling = dto.chapters().get(0).scenes().get(0);
        OutlineDto.SceneDto target = dto.chapters().get(0).scenes().get(1);
        assertEquals("旧前置场景", sibling.title());
        assertEquals("旧目标场景", target.title());
        assertEquals("新场景摘要", target.summary());
        assertEquals("<p>新内容</p>", target.content());
        assertEquals(5, target.order());
        verify(accessGuard, times(2)).assertOwner(owner);
    }

    @Test
    void addGeneratedChapterShouldAppendAiDraftAndFillMissingScenes() {
        OutlineRepository outlineRepository = mock(OutlineRepository.class);
        ResourceAccessGuard accessGuard = mock(ResourceAccessGuard.class);
        AiService aiService = mock(AiService.class);
        UserRepository userRepository = mock(UserRepository.class);
        OutlineService service = service(outlineRepository, mock(StoryRepository.class), accessGuard, aiService, userRepository);
        User owner = user("planner");
        Story story = story(owner);
        Outline outline = outline(outlineId(), story, Map.of(
                "planning", Map.of("arc", "overall"),
                "chapters", List.of(new OutlineSaveRequest.ChapterPayload(
                        UUID.randomUUID(),
                        "第一章",
                        "旧章摘要",
                        1,
                        Map.of("purpose", "setup"),
                        List.of()
                ))
        ));

        when(outlineRepository.findByIdWithStoryUser(outline.getId())).thenReturn(Optional.of(outline));
        when(outlineRepository.save(any(Outline.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accessGuard.currentUsername()).thenReturn(owner.getUsername());
        when(userRepository.findByUsername(owner.getUsername())).thenReturn(Optional.of(owner));
        when(aiService.chat(eq(owner), any())).thenReturn(new AiChatResponse(
                "assistant",
                """
                {
                  "title":"第三章 破局前夜",
                  "summary":"众人准备收网，但核心误导开始反噬主角判断。",
                  "scenes":[
                    {"title":"风暴前的安静","summary":"主角用错误线索说服同伴提前行动。"}
                  ]
                }
                """,
                null,
                0
        ));

        OutlineDto dto = service.addGeneratedChapter(outline.getId(), new OutlineChapterGenerateRequest(3, 3, null, null));

        assertEquals(2, dto.chapters().size());
        OutlineDto.ChapterDto appended = dto.chapters().get(1);
        assertEquals("第三章 破局前夜", appended.title());
        assertEquals(2, appended.order());
        assertEquals(5, appended.scenes().size());
        assertEquals("风暴前的安静", appended.scenes().get(0).title());
        assertEquals("第3章 第5节", appended.scenes().get(4).title());
        assertEquals("progression", appended.planning().get("twistRole"));
        verify(accessGuard, times(2)).assertOwner(owner);
        verify(aiService).chat(eq(owner), any());
    }

    private OutlineService service(
            OutlineRepository outlineRepository,
            StoryRepository storyRepository,
            ResourceAccessGuard accessGuard,
            AiService aiService,
            UserRepository userRepository
    ) {
        OutlineService service = new OutlineService();
        ReflectionTestUtils.setField(service, "outlineRepository", outlineRepository);
        ReflectionTestUtils.setField(service, "storyRepository", storyRepository);
        ReflectionTestUtils.setField(service, "accessGuard", accessGuard);
        ReflectionTestUtils.setField(service, "aiService", aiService);
        ReflectionTestUtils.setField(service, "userRepository", userRepository);
        ReflectionTestUtils.setField(service, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(service, "jsonColumnCodec", jsonColumnCodec);
        return service;
    }

    private Outline outline(UUID outlineId, Story story, Map<String, Object> content) {
        Outline outline = new Outline();
        outline.setId(outlineId);
        outline.setStory(story);
        outline.setTitle("旧大纲");
        outline.setWorldId("world-1");
        outline.setContentJson(jsonColumnCodec.write(content, "{}"));
        return outline;
    }

    private Story story(User owner) {
        Story story = new Story();
        story.setId(UUID.randomUUID());
        story.setUser(owner);
        story.setTitle("雨城疑案");
        story.setSynopsis("副将死而复生，主角被迫重查旧案。");
        story.setGenre("悬疑");
        story.setTone("冷峻");
        return story;
    }

    private User user(String username) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("hash");
        user.setRemoteUid(10001L);
        return user;
    }

    private UUID outlineId() {
        return UUID.randomUUID();
    }
}
