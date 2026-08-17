package com.ainovel.app.manuscript;

import com.ainovel.app.ai.AiModelPolicy;
import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.ai.dto.AiChatResponse;
import com.ainovel.app.manuscript.context.CompiledSceneDraftContext;
import com.ainovel.app.manuscript.context.SceneDraftContextCompiler;
import com.ainovel.app.manuscript.context.SceneDraftContextManifest;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.prompt.AssembledPrompt;
import com.ainovel.app.quality.SlopQualityGate;
import com.ainovel.app.quality.SlopQualityRequest;
import com.ainovel.app.quality.SlopQualityResult;
import com.ainovel.app.quality.SlopQualityStatus;
import com.ainovel.app.quality.SlopSeverity;
import com.ainovel.app.story.model.Outline;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.story.repo.CharacterCardRepository;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SceneGenerationServiceMetadataTest {

    @Test
    void shouldReturnSuccessfulAttemptPromptHashAndExactCompiledManifest() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        CharacterCardRepository characterCardRepository = mock(CharacterCardRepository.class);
        AiService aiService = mock(AiService.class);
        SlopQualityGate qualityGate = mock(SlopQualityGate.class);
        ScenePlotQualitySupport plotQualitySupport = mock(ScenePlotQualitySupport.class);
        SceneGenerationPromptBuilder promptBuilder = mock(SceneGenerationPromptBuilder.class);
        SceneDraftContextCompiler contextCompiler = mock(SceneDraftContextCompiler.class);

        SceneGenerationService service = new SceneGenerationService();
        ReflectionTestUtils.setField(service, "characterCardRepository", characterCardRepository);
        ReflectionTestUtils.setField(service, "aiService", aiService);
        ReflectionTestUtils.setField(service, "slopQualityGate", qualityGate);
        ReflectionTestUtils.setField(service, "scenePlotQualitySupport", plotQualitySupport);
        ReflectionTestUtils.setField(service, "sceneGenerationPromptBuilder", promptBuilder);
        ReflectionTestUtils.setField(service, "sceneDraftContextCompiler", contextCompiler);
        ReflectionTestUtils.setField(service, "objectMapper", objectMapper);

        User owner = new User();
        owner.setId(UUID.randomUUID());
        Story story = new Story();
        story.setId(UUID.randomUUID());
        story.setUser(owner);
        story.setTitle("雨城疑案");
        story.setGenre("悬疑");
        story.setTone("冷峻");
        UUID sceneId = UUID.randomUUID();
        Outline outline = new Outline();
        outline.setId(UUID.randomUUID());
        outline.setStory(story);
        outline.setContentJson(objectMapper.writeValueAsString(Map.of(
                "planning", Map.of("centralQuestion", "谁留下铜扣"),
                "chapters", List.of(Map.of(
                        "id", UUID.randomUUID(),
                        "title", "第一章",
                        "summary", "旧案重启",
                        "order", 1,
                        "planning", Map.of(),
                        "scenes", List.of(Map.of(
                                "id", sceneId,
                                "title", "雨夜门外",
                                "summary", "发现铜扣",
                                "order", 1,
                                "planning", Map.of("sceneType", "action")
                        ))
                ))
        )));
        Manuscript manuscript = new Manuscript();
        manuscript.setId(UUID.randomUUID());
        manuscript.setOutline(outline);
        manuscript.setSectionsJson("{}");

        SceneDraftContextManifest contextManifest = new SceneDraftContextManifest(
                SceneDraftContextCompiler.COMPILER_VERSION,
                "c".repeat(64),
                "SHA-256",
                3500,
                240,
                story.getId(),
                outline.getId(),
                manuscript.getId(),
                sceneId,
                null,
                1,
                1,
                "action",
                List.of(),
                List.of()
        );
        CompiledSceneDraftContext compiled = new CompiledSceneDraftContext(
                "SCENE_DRAFT_CONTEXT scene-draft-v2",
                contextManifest,
                "暂无可用前文。",
                List.of(),
                List.of(),
                List.of()
        );
        when(contextCompiler.compile(eq(manuscript), eq(sceneId), any(Map.class))).thenReturn(compiled);
        when(characterCardRepository.findByStory(story)).thenReturn(List.of());

        List<AiChatRequest.Message> failedMessages = List.of(
                new AiChatRequest.Message("system", "first-system"),
                new AiChatRequest.Message("user", "first-user")
        );
        List<AiChatRequest.Message> successfulMessages = List.of(
                new AiChatRequest.Message("system", "second-system"),
                new AiChatRequest.Message("user", "second-user")
        );
        when(promptBuilder.build(
                eq(owner), eq(story), any(SceneGenerationContext.class), any(), any(), any(),
                anyInt(), anyInt(), anyInt(), anyInt(),
                eq(GenerationMode.FAST), eq(compiled)
        )).thenReturn(
                new AssembledPrompt(failedMessages, 128000),
                new AssembledPrompt(successfulMessages, 128000)
        );

        String acceptedText = "汉".repeat(2900);
        when(aiService.chat(eq(owner), any(AiChatRequest.class))).thenReturn(
                new AiChatResponse("assistant", "太短", null, 0),
                new AiChatResponse("assistant", acceptedText, null, 0)
        );
        SlopQualityRequest qualityRequest = new SlopQualityRequest(
                story.getId(), manuscript.getId(), sceneId, story.getTitle(), story.getGenre(), story.getTone(),
                "第一章", "雨夜门外", "发现铜扣", "暂无可用前文。", "暂无角色卡。", "", acceptedText
        );
        when(plotQualitySupport.buildQualityRequest(
                eq(manuscript), eq(story), any(SceneGenerationContext.class), any(), any(), eq(acceptedText)
        )).thenReturn(qualityRequest);
        when(qualityGate.evaluateAndRepair(owner, qualityRequest)).thenReturn(new SlopQualityResult(
                UUID.randomUUID(), acceptedText, 0, SlopSeverity.LOW, false, 0,
                SlopQualityStatus.ACCEPTED, List.of()
        ));

        SceneGenerationService.GenerationResult result = service.generateSceneSection(
                manuscript, sceneId, Map.of(), GenerationMode.FAST
        );

        assertEquals(2, result.metadata().attemptCount());
        assertEquals(AiModelPolicy.REQUIRED_TEXT_MODEL_KEY, result.metadata().modelKey());
        assertEquals(SceneDraftContextCompiler.COMPILER_VERSION, result.metadata().promptVersion());
        assertEquals(expectedPromptHash(successfulMessages), result.metadata().promptHash());
        assertSame(contextManifest, result.metadata().contextManifest());
    }

    private String expectedPromptHash(List<AiChatRequest.Message> messages) throws Exception {
        StringBuilder canonical = new StringBuilder(SceneGenerationService.SCENE_DRAFT_SYSTEM_RULES_VERSION);
        for (AiChatRequest.Message message : messages) {
            canonical.append('\n').append(message.role().trim()).append('\n').append(message.content());
        }
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
    }
}
