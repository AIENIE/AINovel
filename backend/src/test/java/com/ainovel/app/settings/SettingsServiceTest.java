package com.ainovel.app.settings;

import com.ainovel.app.settings.dto.PromptTemplatesResponse;
import com.ainovel.app.settings.dto.WorldPromptTemplatesResponse;
import com.ainovel.app.settings.dto.WorldPromptTemplatesUpdateRequest;
import com.ainovel.app.settings.model.PromptTemplatesEntity;
import com.ainovel.app.settings.model.WorldPromptTemplatesEntity;
import com.ainovel.app.settings.repo.GlobalSettingsRepository;
import com.ainovel.app.settings.repo.PromptTemplatesRepository;
import com.ainovel.app.settings.repo.WorldPromptTemplatesRepository;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettingsServiceTest {

    @Test
    void getPromptTemplatesShouldCreateDefaultsWhenUserHasNoOverride() {
        PromptTemplatesRepository promptTemplatesRepository = mock(PromptTemplatesRepository.class);
        when(promptTemplatesRepository.findByUser(any(User.class))).thenReturn(Optional.empty());
        when(promptTemplatesRepository.save(any(PromptTemplatesEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SettingsService service = service(
                mock(GlobalSettingsRepository.class),
                promptTemplatesRepository,
                mock(WorldPromptTemplatesRepository.class)
        );

        PromptTemplatesResponse response = service.getPromptTemplates(user("writer"));

        assertTrue(response.storyCreation().contains("小说策划"));
        assertTrue(response.manuscriptSection().contains("保持语气"));
        verify(promptTemplatesRepository).save(any(PromptTemplatesEntity.class));
    }

    @Test
    void getWorldPromptTemplatesShouldDeserializeStoredJson() {
        WorldPromptTemplatesRepository worldPromptTemplatesRepository = mock(WorldPromptTemplatesRepository.class);
        WorldPromptTemplatesEntity entity = new WorldPromptTemplatesEntity();
        entity.setModulesJson("{\"geography\":\"描述地理\"}");
        entity.setFinalTemplatesJson("{\"final\":\"终稿模板\"}");
        entity.setFieldRefine("优化字段");
        when(worldPromptTemplatesRepository.findByUser(any(User.class))).thenReturn(Optional.of(entity));

        SettingsService service = service(
                mock(GlobalSettingsRepository.class),
                mock(PromptTemplatesRepository.class),
                worldPromptTemplatesRepository
        );

        WorldPromptTemplatesResponse response = service.getWorldPromptTemplates(user("writer"));

        assertEquals("描述地理", response.modules().get("geography"));
        assertEquals("终稿模板", response.finalTemplates().get("final"));
        assertEquals("优化字段", response.fieldRefine());
    }

    @Test
    void updateWorldPromptsShouldPersistSerializedMaps() {
        WorldPromptTemplatesRepository worldPromptTemplatesRepository = mock(WorldPromptTemplatesRepository.class);
        WorldPromptTemplatesEntity entity = new WorldPromptTemplatesEntity();
        when(worldPromptTemplatesRepository.findByUser(any(User.class))).thenReturn(Optional.of(entity));
        when(worldPromptTemplatesRepository.save(entity)).thenReturn(entity);

        SettingsService service = service(
                mock(GlobalSettingsRepository.class),
                mock(PromptTemplatesRepository.class),
                worldPromptTemplatesRepository
        );

        WorldPromptTemplatesResponse response = service.updateWorldPrompts(
                user("writer"),
                new WorldPromptTemplatesUpdateRequest(
                        Map.of("geography", "新的地理模块模板"),
                        Map.of("final", "新的终稿模板"),
                        "新的字段润色"
                )
        );

        assertTrue(entity.getModulesJson().contains("\"geography\":\"新的地理模块模板\""));
        assertTrue(entity.getFinalTemplatesJson().contains("\"final\":\"新的终稿模板\""));
        assertEquals("新的字段润色", entity.getFieldRefine());
        assertEquals("新的地理模块模板", response.modules().get("geography"));
        assertEquals("新的终稿模板", response.finalTemplates().get("final"));
    }

    private SettingsService service(
            GlobalSettingsRepository globalSettingsRepository,
            PromptTemplatesRepository promptTemplatesRepository,
            WorldPromptTemplatesRepository worldPromptTemplatesRepository
    ) {
        SettingsService service = new SettingsService();
        ReflectionTestUtils.setField(service, "globalSettingsRepository", globalSettingsRepository);
        ReflectionTestUtils.setField(service, "promptTemplatesRepository", promptTemplatesRepository);
        ReflectionTestUtils.setField(service, "worldPromptTemplatesRepository", worldPromptTemplatesRepository);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        return service;
    }

    private User user(String username) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("hash");
        user.setRoles(Set.of("ROLE_USER"));
        user.setCreatedAt(Instant.parse("2026-07-01T00:00:00Z"));
        user.setUpdatedAt(Instant.parse("2026-07-02T00:00:00Z"));
        return user;
    }
}
