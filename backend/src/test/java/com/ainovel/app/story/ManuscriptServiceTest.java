package com.ainovel.app.story;

import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.ai.dto.AiChatResponse;
import com.ainovel.app.common.JsonColumnCodec;
import com.ainovel.app.manuscript.ManuscriptService;
import com.ainovel.app.manuscript.dto.ManuscriptDto;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.material.MaterialRetrievalService;
import com.ainovel.app.prompt.AssembledPrompt;
import com.ainovel.app.prompt.PromptAssemblyService;
import com.ainovel.app.quality.PlotQualityRequest;
import com.ainovel.app.quality.PlotQualityService;
import com.ainovel.app.quality.SlopQualityGate;
import com.ainovel.app.quality.SlopQualityRequest;
import com.ainovel.app.quality.SlopQualityResult;
import com.ainovel.app.quality.SlopQualityStatus;
import com.ainovel.app.quality.SlopSeverity;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.story.dto.OutlineSaveRequest;
import com.ainovel.app.story.model.CharacterCard;
import com.ainovel.app.story.model.Outline;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.story.repo.CharacterCardRepository;
import com.ainovel.app.story.repo.OutlineRepository;
import com.ainovel.app.style.StyleContextProvider;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManuscriptServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonColumnCodec jsonColumnCodec = new JsonColumnCodec(objectMapper);

    @Test
    void generateForSceneShouldPersistAcceptedGateResult() {
        ManuscriptRepository manuscriptRepository = mock(ManuscriptRepository.class);
        CharacterCardRepository characterCardRepository = mock(CharacterCardRepository.class);
        AiService aiService = mock(AiService.class);
        ResourceAccessGuard accessGuard = mock(ResourceAccessGuard.class);
        SlopQualityGate slopQualityGate = mock(SlopQualityGate.class);
        PlotQualityService plotQualityService = mock(PlotQualityService.class);
        PromptAssemblyService promptAssemblyService = mock(PromptAssemblyService.class);
        MaterialRetrievalService materialRetrievalService = mock(MaterialRetrievalService.class);
        StyleContextProvider styleContextProvider = mock(StyleContextProvider.class);
        ManuscriptService service = service(
                manuscriptRepository,
                mock(OutlineRepository.class),
                characterCardRepository,
                aiService,
                accessGuard,
                slopQualityGate,
                plotQualityService,
                promptAssemblyService,
                materialRetrievalService,
                styleContextProvider
        );

        User owner = user("manuscript_author");
        UUID previousSceneId = UUID.randomUUID();
        UUID targetSceneId = UUID.randomUUID();
        Story story = story(owner);
        Outline outline = outline(story, List.of(
                new OutlineSaveRequest.ChapterPayload(
                        UUID.randomUUID(),
                        "第一章",
                        "旧案重启",
                        1,
                        Map.of("purpose", "setup"),
                        List.of(
                                new OutlineSaveRequest.ScenePayload(previousSceneId, "线索浮现", "发现铜扣", null, 1, Map.of()),
                                new OutlineSaveRequest.ScenePayload(targetSceneId, "追入雨巷", "主角追查误导线索", null, 2, Map.of())
                        )
                )
        ));
        Manuscript manuscript = manuscript(UUID.randomUUID(), outline, Map.of(previousSceneId.toString(), "<p>上一场景留下铜扣线索。</p>"));
        CharacterCard character = character(story, "林烬", "谨慎的调查员", "总先排除伪证", "与周燃互相制衡");
        String acceptedText = "稿".repeat(2900);

        when(manuscriptRepository.findWithStoryById(manuscript.getId())).thenReturn(Optional.of(manuscript));
        when(manuscriptRepository.save(any(Manuscript.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(characterCardRepository.findByStory(story)).thenReturn(List.of(character));
        when(promptAssemblyService.assembleSceneDraft(any())).thenReturn(new AssembledPrompt(
                List.of(new AiChatRequest.Message("user", "生成本场景")),
                128000
        ));
        when(materialRetrievalService.search(eq(owner), any())).thenReturn(List.of());
        when(styleContextProvider.buildSlopContext(story)).thenReturn("冷峻、压迫感强");
        when(aiService.chat(eq(owner), any())).thenReturn(new AiChatResponse("assistant", "文".repeat(2900), null, 0));
        when(slopQualityGate.evaluateAndRepair(eq(owner), any())).thenReturn(new SlopQualityResult(
                UUID.randomUUID(),
                acceptedText,
                18,
                SlopSeverity.LOW,
                false,
                0,
                SlopQualityStatus.ACCEPTED,
                List.of()
        ));

        ManuscriptDto dto = service.generateForScene(manuscript.getId(), targetSceneId);

        assertEquals(manuscript.getId(), dto.id());
        assertTrue(dto.sections().get(targetSceneId.toString()).startsWith("<p>稿稿稿"));
        assertEquals("<p>上一场景留下铜扣线索。</p>", dto.sections().get(previousSceneId.toString()));
        verify(accessGuard).assertOwner(owner);
        verify(manuscriptRepository).save(manuscript);

        ArgumentCaptor<SlopQualityRequest> gateRequestCaptor = ArgumentCaptor.forClass(SlopQualityRequest.class);
        verify(slopQualityGate).evaluateAndRepair(eq(owner), gateRequestCaptor.capture());
        SlopQualityRequest gateRequest = gateRequestCaptor.getValue();
        assertSame(manuscript.getId(), gateRequest.manuscriptId());
        assertEquals(targetSceneId, gateRequest.sceneId());
        assertTrue(gateRequest.previousContext().contains("铜扣线索"));
        assertEquals("冷峻、压迫感强", gateRequest.styleContext());

        ArgumentCaptor<PlotQualityRequest> plotRequestCaptor = ArgumentCaptor.forClass(PlotQualityRequest.class);
        verify(plotQualityService).analyze(eq(owner), plotRequestCaptor.capture());
        assertEquals(targetSceneId, plotRequestCaptor.getValue().sceneId());
        assertEquals(acceptedText, plotRequestCaptor.getValue().sceneText());
    }

    @Test
    void generateForSceneShouldStopAfterThreeOutOfRangeAttempts() {
        ManuscriptRepository manuscriptRepository = mock(ManuscriptRepository.class);
        CharacterCardRepository characterCardRepository = mock(CharacterCardRepository.class);
        AiService aiService = mock(AiService.class);
        ResourceAccessGuard accessGuard = mock(ResourceAccessGuard.class);
        PromptAssemblyService promptAssemblyService = mock(PromptAssemblyService.class);
        MaterialRetrievalService materialRetrievalService = mock(MaterialRetrievalService.class);
        StyleContextProvider styleContextProvider = mock(StyleContextProvider.class);
        ManuscriptService service = service(
                manuscriptRepository,
                mock(OutlineRepository.class),
                characterCardRepository,
                aiService,
                accessGuard,
                mock(SlopQualityGate.class),
                mock(PlotQualityService.class),
                promptAssemblyService,
                materialRetrievalService,
                styleContextProvider
        );

        User owner = user("retry_author");
        UUID targetSceneId = UUID.randomUUID();
        Story story = story(owner);
        Outline outline = outline(story, List.of(
                new OutlineSaveRequest.ChapterPayload(
                        UUID.randomUUID(),
                        "第一章",
                        "章摘要",
                        1,
                        Map.of(),
                        List.of(new OutlineSaveRequest.ScenePayload(targetSceneId, "目标场景", "场景摘要", null, 1, Map.of()))
                )
        ));
        Manuscript manuscript = manuscript(UUID.randomUUID(), outline, Map.of());

        when(manuscriptRepository.findWithStoryById(manuscript.getId())).thenReturn(Optional.of(manuscript));
        when(characterCardRepository.findByStory(story)).thenReturn(List.of());
        when(promptAssemblyService.assembleSceneDraft(any())).thenReturn(new AssembledPrompt(
                List.of(new AiChatRequest.Message("user", "生成本场景")),
                128000
        ));
        when(materialRetrievalService.search(eq(owner), any())).thenReturn(List.of());
        when(styleContextProvider.buildSlopContext(story)).thenReturn("暂无风格画像");
        when(aiService.chat(eq(owner), any())).thenReturn(new AiChatResponse("assistant", "短".repeat(2000), null, 0));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.generateForScene(manuscript.getId(), targetSceneId));

        assertTrue(ex.getMessage().contains("重试 3 次"));
        verify(aiService, times(3)).chat(eq(owner), any());
        verify(promptAssemblyService, times(3)).assembleSceneDraft(any());
        verify(manuscriptRepository, never()).save(any(Manuscript.class));
    }

    private ManuscriptService service(
            ManuscriptRepository manuscriptRepository,
            OutlineRepository outlineRepository,
            CharacterCardRepository characterCardRepository,
            AiService aiService,
            ResourceAccessGuard accessGuard,
            SlopQualityGate slopQualityGate,
            PlotQualityService plotQualityService,
            PromptAssemblyService promptAssemblyService,
            MaterialRetrievalService materialRetrievalService,
            StyleContextProvider styleContextProvider
    ) {
        ManuscriptService service = new ManuscriptService();
        ReflectionTestUtils.setField(service, "manuscriptRepository", manuscriptRepository);
        ReflectionTestUtils.setField(service, "outlineRepository", outlineRepository);
        ReflectionTestUtils.setField(service, "characterCardRepository", characterCardRepository);
        ReflectionTestUtils.setField(service, "aiService", aiService);
        ReflectionTestUtils.setField(service, "accessGuard", accessGuard);
        ReflectionTestUtils.setField(service, "slopQualityGate", slopQualityGate);
        ReflectionTestUtils.setField(service, "plotQualityService", plotQualityService);
        ReflectionTestUtils.setField(service, "promptAssemblyService", promptAssemblyService);
        ReflectionTestUtils.setField(service, "materialRetrievalService", materialRetrievalService);
        ReflectionTestUtils.setField(service, "styleContextProvider", styleContextProvider);
        ReflectionTestUtils.setField(service, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(service, "jsonColumnCodec", jsonColumnCodec);
        return service;
    }

    private Manuscript manuscript(UUID manuscriptId, Outline outline, Map<String, String> sections) {
        Manuscript manuscript = new Manuscript();
        manuscript.setId(manuscriptId);
        manuscript.setOutline(outline);
        manuscript.setTitle("第一稿");
        manuscript.setWorldId("world-1");
        manuscript.setSectionsJson(jsonColumnCodec.write(sections, "{}"));
        manuscript.setCharacterLogsJson("[]");
        return manuscript;
    }

    private Outline outline(Story story, List<OutlineSaveRequest.ChapterPayload> chapters) {
        Outline outline = new Outline();
        outline.setId(UUID.randomUUID());
        outline.setStory(story);
        outline.setTitle("案件大纲");
        outline.setWorldId("world-1");
        outline.setContentJson(jsonColumnCodec.write(Map.of(
                "planning", Map.of("mystery", "副将复活"),
                "chapters", chapters
        ), "{}"));
        return outline;
    }

    private Story story(User owner) {
        Story story = new Story();
        story.setId(UUID.randomUUID());
        story.setUser(owner);
        story.setTitle("雨城疑案");
        story.setSynopsis("主角在雨城重查副将复活真相。");
        story.setGenre("悬疑");
        story.setTone("冷峻");
        return story;
    }

    private CharacterCard character(Story story, String name, String synopsis, String details, String relationships) {
        CharacterCard card = new CharacterCard();
        card.setId(UUID.randomUUID());
        card.setStory(story);
        card.setName(name);
        card.setSynopsis(synopsis);
        card.setDetails(details);
        card.setRelationships(relationships);
        return card;
    }

    private User user(String username) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("hash");
        user.setRemoteUid(10003L);
        return user;
    }
}
