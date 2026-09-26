package com.ainovel.app.manuscript;

import com.ainovel.app.material.MaterialRetrievalService;
import com.ainovel.app.manuscript.context.CompiledSceneDraftContext;
import com.ainovel.app.manuscript.context.SceneDraftContextManifest;
import com.ainovel.app.prompt.AssembledPrompt;
import com.ainovel.app.prompt.PromptAssemblyService;
import com.ainovel.app.prompt.SceneGenerationPromptInput;
import com.ainovel.app.quality.SlopPatternSamplingService;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.user.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SceneGenerationPromptBuilderTest {
    @Test void isolatedFastCraftedAndLengthRetryNeverReadLegacyPromptSources() {
        var builder=new SceneGenerationPromptBuilder();
        var materials=mock(MaterialRetrievalService.class);
        var sampling=mock(SlopPatternSamplingService.class);
        ReflectionTestUtils.setField(builder,"promptAssemblyService",new PromptAssemblyService());
        ReflectionTestUtils.setField(builder,"materialRetrievalService",materials);
        ReflectionTestUtils.setField(builder,"slopPatternSamplingService",sampling);
        when(sampling.sample(any())).thenReturn(List.of());
        var story=new Story();story.setTitle("HIDDEN_TITLE");story.setSynopsis("HIDDEN_SYNOPSIS");story.setGenre("HIDDEN_GENRE");story.setTone("HIDDEN_TONE");
        UUID mid=UUID.randomUUID(),bid=UUID.randomUUID(),sid=UUID.randomUUID();
        var stamp=new com.ainovel.app.narrative.NarrativeContextDtos.Stamp(mid,bid,1,2,3,4,"order");
        var manifest=new SceneDraftContextManifest("scene-isolation-h2-v1","hash","SHA-256",3500,50,UUID.randomUUID(),UUID.randomUUID(),mid,sid,null,1,1,"",List.of(),List.of(),stamp);
        var compiled=new CompiledSceneDraftContext("[PLAN] 作者批准本场等待来信，尚未发生",manifest,"safe",List.of(),List.of(),List.of(),"safe");
        var scene=new SceneGenerationContext(sid,"HIDDEN_CHAPTER","HIDDEN_SUMMARY",1,"HIDDEN_SCENE","HIDDEN_SCENE_SUMMARY",1,List.of(),List.of());
        for(var mode:GenerationMode.values()) for(int attempt=1;attempt<=2;attempt++) {
            String draft="完整初稿"+"中段".repeat(850)+"最后交还铜钥匙，门始终未开。";
            var prompt=builder.build(new User(),story,scene,"HIDDEN_CHARACTER","HIDDEN_PREVIOUS",draft,10,attempt,600,900,mode,compiled);
            String sent=prompt.messages().toString();
            assertTrue(sent.contains("作者批准本场等待来信"));
            assertFalse(sent.contains("HIDDEN_"));
            if(attempt==2)assertTrue(sent.contains(draft),"篇幅修正必须携带完整上一稿及结尾");
        }
        verifyNoInteractions(materials);
    }

    @Test
    void craftedModeUsesSampledConstraintsInsteadOfFastAssembly() {
        PromptAssemblyService promptAssemblyService = mock(PromptAssemblyService.class);
        MaterialRetrievalService materialRetrievalService = mock(MaterialRetrievalService.class);
        SlopPatternSamplingService samplingService = mock(SlopPatternSamplingService.class);
        SceneGenerationPromptBuilder builder = new SceneGenerationPromptBuilder();
        ReflectionTestUtils.setField(builder, "promptAssemblyService", promptAssemblyService);
        ReflectionTestUtils.setField(builder, "materialRetrievalService", materialRetrievalService);
        ReflectionTestUtils.setField(builder, "slopPatternSamplingService", samplingService);

        User owner = new User();
        Story story = new Story();
        story.setTitle("雨城疑案");
        story.setGenre("悬疑");
        story.setTone("冷峻");
        story.setSynopsis("旧城连续失踪案");
        UUID sceneId = UUID.randomUUID();
        SceneGenerationContext scene = new SceneGenerationContext(
                sceneId, "第一章", "旧案重启", 1, "雨夜门外", "发现铜扣", 2, List.of(), List.of());
        List<String> patterns = List.of("套路 A", "套路 B");
        AssembledPrompt expected = new AssembledPrompt(List.of(), 128000);
        when(materialRetrievalService.search(eq(owner), any())).thenReturn(List.of());
        when(samplingService.sample(sceneId)).thenReturn(patterns);
        when(promptAssemblyService.assembleWithCreativeConstraints(any(), eq(patterns), eq(2))).thenReturn(expected);

        AssembledPrompt actual = builder.build(
                owner, story, scene, "角色设定", "前文", "", 0, 1, 2800, 3200, GenerationMode.CRAFTED);

        assertSame(expected, actual);
        verify(samplingService).sample(sceneId);
        verify(promptAssemblyService).assembleWithCreativeConstraints(any(), eq(patterns), eq(2));
        verify(promptAssemblyService, never()).assembleSceneDraft(any());
    }

    @Test
    void shouldForwardCompiledContextManifestIdentityToPromptAssembly() {
        PromptAssemblyService promptAssemblyService = mock(PromptAssemblyService.class);
        MaterialRetrievalService materialRetrievalService = mock(MaterialRetrievalService.class);
        SlopPatternSamplingService samplingService = mock(SlopPatternSamplingService.class);
        SceneGenerationPromptBuilder builder = new SceneGenerationPromptBuilder();
        ReflectionTestUtils.setField(builder, "promptAssemblyService", promptAssemblyService);
        ReflectionTestUtils.setField(builder, "materialRetrievalService", materialRetrievalService);
        ReflectionTestUtils.setField(builder, "slopPatternSamplingService", samplingService);

        User owner = new User();
        Story story = new Story();
        story.setTitle("雨城疑案");
        UUID sceneId = UUID.randomUUID();
        SceneGenerationContext scene = new SceneGenerationContext(
                sceneId, "第二章", "重返码头", 2, "逼问", "确认铜扣主人", 2, List.of(), List.of());
        SceneDraftContextManifest manifest = new SceneDraftContextManifest(
                "scene-draft-v2", "c".repeat(64), "SHA-256", 3500, 300,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), sceneId,
                null, 2, 2, "dialogue", List.of(), List.of()
        );
        CompiledSceneDraftContext compiled = new CompiledSceneDraftContext(
                "SCENE_DRAFT_CONTEXT scene-draft-v2\n[当前场景规划]\n目标=确认铜扣主人",
                manifest,
                "最近正文",
                List.of(),
                List.of(),
                List.of()
        );
        AssembledPrompt expected = new AssembledPrompt(List.of(), 128000);
        when(materialRetrievalService.search(eq(owner), any())).thenReturn(List.of());
        when(promptAssemblyService.assembleSceneDraft(any())).thenReturn(expected);

        AssembledPrompt actual = builder.build(
                owner, story, scene, "角色设定", "最近正文", "", 0, 1,
                2800, 3200, GenerationMode.FAST, compiled
        );

        assertSame(expected, actual);
        ArgumentCaptor<SceneGenerationPromptInput> inputCaptor = ArgumentCaptor.forClass(SceneGenerationPromptInput.class);
        verify(promptAssemblyService).assembleSceneDraft(inputCaptor.capture());
        assertEquals("使用下方 SCENE_DRAFT_CONTEXT 中已采用的人物资料。", inputCaptor.getValue().characterContext());
        assertEquals(compiled.content(), inputCaptor.getValue().compiledContext());
        assertEquals(compiled.compilerVersion(), inputCaptor.getValue().contextCompilerVersion());
        assertEquals(compiled.contextHash(), inputCaptor.getValue().contextHash());
    }
}
