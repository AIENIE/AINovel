package com.ainovel.app.world;

import com.ainovel.app.user.User;
import com.ainovel.app.user.UserRepository;
import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.dto.AiRefineResponse;
import com.ainovel.app.ai.dto.AiUsageDto;
import com.ainovel.app.world.dto.WorldDetailDto;
import com.ainovel.app.world.dto.WorldGenerationStatus;
import com.ainovel.app.world.model.World;
import com.ainovel.app.world.repo.WorldRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class WorldPublishFlowTests {
    @Autowired
    private WorldService worldService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private WorldRepository worldRepository;
    @MockBean
    private AiService aiService;

    @Test
    @WithMockUser(username = "world_test_user", roles = {"USER"})
    void publishKeepsCompletedModulesAndActivationAfterGeneration() {
        User user = new User();
        user.setUsername("world_test_user");
        user.setEmail("world_test_user@example.com");
        user.setPasswordHash("x");
        user.setRoles(Set.of("ROLE_USER"));
        userRepository.save(user);

        World world = new World();
        world.setUser(user);
        world.setName("Test World");
        world.setStatus("draft");
        world.setVersion("0.1.0");
        world.setModulesJson("{\"geography\":{\"terrain\":\"mountains\"}}");
        world.setModuleProgressJson("{}");
        worldRepository.save(world);

        WorldDetailDto afterPublish = worldService.publish(world.getId());
        assertEquals("generating", afterPublish.status());
        assertEquals("COMPLETED", afterPublish.moduleProgress().get("geography"));
        assertEquals("AWAITING_GENERATION", afterPublish.moduleProgress().get("society"));
        assertEquals("AWAITING_GENERATION", afterPublish.moduleProgress().get("magic_tech"));

        when(aiService.refine(any(), any()))
                .thenReturn(new AiRefineResponse("雨都议会按港口税权划分派系，商会控制粮运，平民节庆围绕潮汐钟展开。",
                        new AiUsageDto(1, 1, 0, 0, 0),
                        100))
                .thenReturn(new AiRefineResponse("商会以粮运和海盐为核心产业，税契由港口议会逐季审定。",
                        new AiUsageDto(1, 1, 0, 0, 0),
                        100))
                .thenReturn(new AiRefineResponse("市民节庆围绕潮汐钟和雾灯巡游展开，外来者需先登记船印。",
                        new AiUsageDto(1, 1, 0, 0, 0),
                        100))
                .thenReturn(new AiRefineResponse("灵潮科技",
                        new AiUsageDto(1, 1, 0, 0, 0),
                        100))
                .thenReturn(new AiRefineResponse("灵潮科技以潮汐晶体供能，越界使用会造成记忆回潮和城市停摆。",
                        new AiUsageDto(1, 1, 0, 0, 0),
                        100))
                .thenReturn(new AiRefineResponse("核心限制是晶体过载会反噬使用者记忆，并让附近设备短暂停摆。",
                        new AiUsageDto(1, 1, 0, 0, 0),
                        100));

        WorldDetailDto afterSociety = worldService.generateModule(world.getId(), "society");
        assertFalse(afterSociety.modules().get("society").get("politics").contains("占位"));
        assertTrue(afterSociety.modules().get("society").get("politics").contains("雨都议会"));
        assertFalse(afterSociety.modules().get("society").get("economy").contains("占位"));
        assertFalse(afterSociety.modules().get("society").get("culture").contains("占位"));
        assertEquals("generating", afterSociety.status());

        WorldDetailDto afterMagic = worldService.generateModule(world.getId(), "magic_tech");
        assertEquals("active", afterMagic.status());
        assertEquals("0.1.1", afterMagic.version());
        assertTrue(afterMagic.modules().get("magic_tech").get("rules").contains("灵潮科技"));
        assertFalse(afterMagic.modules().get("magic_tech").get("limitations").contains("占位"));
    }

    @Test
    @WithMockUser(username = "world_failed_user", roles = {"USER"})
    void generateModuleShouldRecordFailureInsteadOfWritingPlaceholderContent() {
        User user = new User();
        user.setUsername("world_failed_user");
        user.setEmail("world_failed_user@example.com");
        user.setPasswordHash("x");
        user.setRemoteUid(70001L);
        user.setRoles(Set.of("ROLE_USER"));
        userRepository.save(user);

        World world = new World();
        world.setUser(user);
        world.setName("Failed World");
        world.setStatus("generating");
        world.setVersion("0.1.0");
        world.setModulesJson("{}");
        world.setModuleProgressJson("{\"geography\":\"AWAITING_GENERATION\"}");
        worldRepository.save(world);
        when(aiService.refine(any(), any())).thenThrow(new RuntimeException("ai-service unavailable"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> worldService.generateModule(world.getId(), "geography"));

        assertTrue(ex.getMessage().contains("ai-service unavailable"));
        WorldGenerationStatus status = worldService.generationStatus(world.getId());
        assertEquals("FAILED", status.queue().getFirst().status());
        assertTrue(status.queue().getFirst().error().contains("ai-service unavailable"));
        WorldDetailDto detail = worldService.get(world.getId());
        assertFalse(detail.modules().containsKey("geography"));
    }
}
