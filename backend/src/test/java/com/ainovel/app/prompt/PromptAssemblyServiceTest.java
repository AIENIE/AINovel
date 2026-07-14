package com.ainovel.app.prompt;

import com.ainovel.app.ai.dto.AiChatRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptAssemblyServiceTest {

    @Test
    void shouldKeepStableRulesBeforeDynamicSceneData() {
        PromptAssemblyService service = new PromptAssemblyService();
        SceneGenerationPromptInput input = new SceneGenerationPromptInput(
                "雨城疑案",
                "悬疑",
                "冷峻克制",
                "旧城连续失踪案",
                "第一章",
                "主角第一次接近码头线索",
                1,
                "雨夜门外",
                "林烬在铁门前发现铜扣",
                2,
                "林烬：谨慎、重证据\n陆遥：失踪者家属",
                "前文片段：林烬确认失踪者都在雨夜去过码头。",
                List.of(new PromptReference(
                        "material",
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        "旧报纸摘录",
                        "旧报纸提到陆家码头在十年前停用，但雨夜仍有货船靠岸。",
                        0.91d,
                        0
                )),
                List.of("嘴角微微上扬", "空气仿佛凝固"),
                2800,
                3200,
                null,
                128000
        );

        AssembledPrompt prompt = service.assembleSceneDraft(input);

        assertEquals(2, prompt.messages().size());
        AiChatRequest.Message system = prompt.messages().get(0);
        AiChatRequest.Message user = prompt.messages().get(1);
        assertEquals("system", system.role());
        assertEquals("user", user.role());
        assertTrue(system.content().contains("AINOVEL_SCENE_DRAFT_RULES_V1"));
        assertTrue(system.content().contains("只输出小说正文"));
        assertFalse(system.content().contains("雨夜门外"));
        assertTrue(user.content().contains("雨夜门外"));
        assertTrue(user.content().contains("旧报纸提到陆家码头"));
        assertTrue(user.content().contains("嘴角微微上扬"));
    }

    @Test
    void shouldAddExactCraftedConstraintsWithoutChangingDynamicInput() {
        PromptAssemblyService service = new PromptAssemblyService();
        SceneGenerationPromptInput input = new SceneGenerationPromptInput(
                "雨城疑案", "悬疑", "冷峻克制", "旧城连续失踪案", "第一章", "主角第一次接近码头线索", 1,
                "雨夜门外", "林烬在铁门前发现铜扣", 1, "林烬：谨慎、重证据", "前文片段", List.of(),
                List.of(), 2800, 3200, "", 128000);

        AssembledPrompt prompt = service.assembleWithCreativeConstraints(input, List.of("套路 A", "套路 B"), 1);
        AiChatRequest.Message system = prompt.messages().get(0);
        AiChatRequest.Message user = prompt.messages().get(1);

        assertTrue(system.content().contains("【精雕模式写作约束】"));
        assertTrue(system.content().contains("套路 A"));
        assertTrue(system.content().contains("套路 B"));
        assertTrue(system.content().contains("代价可见"));
        assertTrue(system.content().contains("呼吸感"));
        assertFalse(system.content().contains("具体经验质感"));
        assertTrue(user.content().contains("雨夜门外"));
    }
}
