package com.ainovel.app.world;

import com.ainovel.app.user.User;
import com.ainovel.app.user.UserRepository;
import com.ainovel.app.world.dto.WorldDetailDto;
import com.ainovel.app.world.model.World;
import com.ainovel.app.world.repo.WorldRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class WorldPublishFlowTests {
    @Autowired
    private WorldService worldService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private WorldRepository worldRepository;

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

        WorldDetailDto afterSociety = worldService.generateModule(world.getId(), "society");
        assertEquals("AI 生成的占位内容：政治体制", afterSociety.modules().get("society").get("politics"));
        assertEquals("AI 生成的占位内容：经济结构", afterSociety.modules().get("society").get("economy"));
        assertEquals("AI 生成的占位内容：文化", afterSociety.modules().get("society").get("culture"));
        assertEquals("generating", afterSociety.status());

        WorldDetailDto afterMagic = worldService.generateModule(world.getId(), "magic_tech");
        assertEquals("active", afterMagic.status());
        assertEquals("0.1.1", afterMagic.version());
        assertEquals("AI 生成的占位内容：体系名称", afterMagic.modules().get("magic_tech").get("system_name"));
        assertEquals("AI 生成的占位内容：规则", afterMagic.modules().get("magic_tech").get("rules"));
        assertEquals("AI 生成的占位内容：限制", afterMagic.modules().get("magic_tech").get("limitations"));
    }
}
