package com.ainovel.app.world;

import com.ainovel.app.ai.AiService;
import com.ainovel.app.auth.SsoTokenExchangeService;
import com.ainovel.app.common.ApiStatusException;
import com.ainovel.app.user.User;
import com.ainovel.app.user.UserRepository;
import com.ainovel.app.world.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class WorldEditingTests {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.ainovel.app.security.remote.UserSessionValidator userSessionValidator;

    @MockBean private SsoTokenExchangeService ssoTokenExchangeService;
    @MockBean private AiService aiService;
    @Autowired private WorldService worlds;
    @Autowired private UserRepository users;
    @Autowired private ObjectMapper json;

    @Test
    @WithMockUser(username = "world_atomic_save")
    void savesMetadataAndModulesTogetherAndReturnsThePersistedRevision() {
        var created = create("world_atomic_save");
        var baseline = worlds.updateModules(created.id(), new WorldModulesUpdateRequest(Map.of(
                "geography", Map.of("terrain", "旧地形"),
                "legacy_extra", Map.of("custom", "保留的历史设定"))));

        var saved = worlds.update(created.id(), new WorldUpdateRequest(
                "潮汐城", "新简介", List.of("记忆"), "探索资源与代价", "作者备注",
                Map.of("geography", Map.of("terrain", "海岛", "climate", "海风"),
                        "society", Map.of("economy", "盐业")), baseline.revision()));

        assertEquals(baseline.revision() + 1, saved.revision());
        assertEquals(saved, worlds.get(created.id()));
        assertEquals("潮汐城", saved.name());
        assertEquals("新简介", saved.tagline());
        assertEquals(List.of("记忆"), saved.themes());
        assertEquals("探索资源与代价", saved.creativeIntent());
        assertEquals("作者备注", saved.notes());
        assertEquals(Map.of("terrain", "海岛", "climate", "海风"), saved.modules().get("geography"));
        assertEquals("盐业", saved.modules().get("society").get("economy"));
        assertEquals("保留的历史设定", saved.modules().get("legacy_extra").get("custom"));
        assertEquals(created.status(), saved.status());
        assertEquals(created.version(), saved.version());

        var next = worlds.update(created.id(), new WorldUpdateRequest(
                "潮汐城修订", null, null, null, null, null, saved.revision()));
        assertEquals(saved.revision() + 1, next.revision());
    }

    @Test
    @WithMockUser(username = "world_stale_save")
    void staleCombinedSaveLeavesBothMetadataAndModulesUntouched() {
        var firstTab = create("world_stale_save");
        var current = worlds.update(firstTab.id(), new WorldUpdateRequest(
                "已保存的新名称", null, null, null, null,
                Map.of("geography", Map.of("terrain", "已保存的新地形")), firstTab.revision()));

        var conflict = assertThrows(ApiStatusException.class, () -> worlds.update(firstTab.id(),
                new WorldUpdateRequest("旧标签页名称", "旧标签页简介", List.of("旧主题"), "旧意图", "旧备注",
                        Map.of("geography", Map.of("terrain", "旧标签页地形")), firstTab.revision())));

        assertConflict(conflict);
        assertEquals(current, worlds.get(firstTab.id()));
    }

    @Test
    @WithMockUser(username = "world_legacy_save")
    void legacyJsonBodiesRemainCompatibleAndPreserveOmittedData() throws Exception {
        var created = create("world_legacy_save");
        var module = worlds.updateModule(created.id(), "geography", json.readValue(
                "{\"fields\":{\"terrain\":\"群岛\",\"climate\":\"温带\"}}", WorldModuleUpdateRequest.class));
        var metadata = worlds.update(created.id(), json.readValue(
                "{\"name\":\"旧客户端改名\"}", WorldUpdateRequest.class));
        var batch = worlds.updateModules(created.id(), json.readValue(
                "{\"modules\":{\"society\":{\"politics\":\"议会\"}}}", WorldModulesUpdateRequest.class));

        assertEquals(module.modules(), metadata.modules());
        assertEquals("旧客户端改名", batch.name());
        assertEquals(created.tagline(), batch.tagline());
        assertEquals(created.themes(), batch.themes());
        assertEquals(created.creativeIntent(), batch.creativeIntent());
        assertEquals(created.notes(), batch.notes());
        assertEquals(module.modules().get("geography"), batch.modules().get("geography"));
        assertEquals("议会", batch.modules().get("society").get("politics"));
        assertEquals(batch, worlds.get(created.id()));
        assertEquals(batch.revision(), json.valueToTree(batch).path("revision").asLong());
    }

    @Test
    @WithMockUser(username = "world_module_conflict")
    void bothModuleEndpointsHonorTheOptionalExpectedRevision() {
        var created = create("world_module_conflict");
        var first = worlds.updateModule(created.id(), "geography",
                new WorldModuleUpdateRequest(Map.of("terrain", "海岛"), created.revision()));
        var current = worlds.updateModules(created.id(), new WorldModulesUpdateRequest(
                Map.of("society", Map.of("culture", "船歌")), first.revision()));

        assertConflict(assertThrows(ApiStatusException.class, () -> worlds.updateModule(created.id(), "geography",
                new WorldModuleUpdateRequest(Map.of("terrain", "过期地形"), first.revision()))));
        assertConflict(assertThrows(ApiStatusException.class, () -> worlds.updateModules(created.id(),
                new WorldModulesUpdateRequest(Map.of("society", Map.of("culture", "过期文化")), first.revision()))));
        assertEquals(current, worlds.get(created.id()));
    }

    private WorldDetailDto create(String username) {
        var user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.invalid");
        user.setPasswordHash("x");
        users.save(user);
        return worlds.create(user, new WorldCreateRequest("初始世界", "原简介", List.of("原主题"), "原意图", "原备注"));
    }

    private void assertConflict(ApiStatusException error) {
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals("WORLD_VERSION_CONFLICT", error.getMessage());
    }
}
