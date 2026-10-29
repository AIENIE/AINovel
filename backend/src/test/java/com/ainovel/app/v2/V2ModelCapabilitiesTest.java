package com.ainovel.app.v2;

import com.ainovel.app.common.ApiStatusException;
import com.ainovel.app.modelconfig.AiModelConfigurationService;
import com.ainovel.app.story.repo.StoryRepository;
import com.ainovel.app.v2.model.V2ModelRegistry;
import com.ainovel.app.v2.repo.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class V2ModelCapabilitiesTest {
    private final ObjectMapper json = new ObjectMapper();
    private final V2Json projection = new V2Json(json);
    private final V2ModelRegistryRepository models = mock(V2ModelRegistryRepository.class);

    private V2ModelPersistenceService service(V2ModelRegistry model) {
        var service = new V2ModelPersistenceService(models, mock(V2TaskModelRoutingRepository.class),
                mock(V2UserModelPreferenceRepository.class), mock(V2ModelUsageLogRepository.class),
                mock(StoryRepository.class), projection);
        ReflectionTestUtils.setField(service, "modelConfiguration", mock(AiModelConfigurationService.class));
        when(models.findByOrderByPriorityDesc()).thenReturn(List.of(model));
        when(models.findByModelKey(model.getModelKey())).thenReturn(Optional.of(model));
        return service;
    }

    private V2ModelRegistry model(String capabilities) {
        var model = new V2ModelRegistry();
        ReflectionTestUtils.setField(model, "id", UUID.randomUUID());
        model.setModelKey("catalog-model");
        model.setCapabilitiesJson(capabilities);
        return model;
    }

    @Test
    void legacyArrayAndSynchronizedCatalogKeepTheSameLegacyProjection() throws Exception {
        var original = List.of("chat", "draft_generation", "自定义能力");
        String legacyJson = json.writeValueAsString(original);
        var model = model(legacyJson);
        var service = service(model);
        assertEquals(original, service.listModels().getFirst().get("capabilities"));

        String catalogJson = json.writeValueAsString(Map.of(
                "legacyCapabilitiesJson", legacyJson,
                "gatewayChat", Map.of("budgetToolsDeclared", true, "budgetToolsAvailable", true),
                "otherMetadata", Map.of("retained", true)));
        model.setCapabilitiesJson(catalogJson);

        var listed = service.listModels().getFirst();
        assertEquals(original, listed.get("capabilities"));
        assertEquals(original, service.findModel(model.getModelKey()).get("capabilities"));
        assertFalse(listed.containsKey("gatewayChat"));
        assertFalse(listed.containsKey("legacyCapabilitiesJson"));
        assertFalse(listed.containsKey("otherMetadata"));
        assertEquals(catalogJson, model.getCapabilitiesJson());
        verify(models, never()).save(any());
    }

    @Test
    void catalogWithoutLegacyCapabilitiesExposesAnEmptyListAndRetainsItsMetadata() {
        String catalogJson = "{\"gatewayChat\":{\"budgetToolsAvailable\":true},\"otherMetadata\":42}";
        var model = model(catalogJson);
        assertEquals(List.of(), service(model).listModels().getFirst().get("capabilities"));
        assertEquals(catalogJson, model.getCapabilitiesJson());
    }

    @Test
    void catalogProjectionDoesNotRelaxOtherStoredListValidation() {
        String catalogJson = "{\"gatewayChat\":{},\"legacyCapabilitiesJson\":\"[\\\"chat\\\"]\"}";
        assertEquals(List.of("chat"), projection.modelCapabilities(catalogJson));
        assertEquals("STORED_DATA_INVALID", assertThrows(ApiStatusException.class, () -> projection.list(catalogJson)).getMessage());
        assertEquals("STORED_DATA_INVALID", assertThrows(ApiStatusException.class,
                () -> projection.modelCapabilities("{\"gatewayChat\":{},\"legacyCapabilitiesJson\":{}}")).getMessage());
    }
}
