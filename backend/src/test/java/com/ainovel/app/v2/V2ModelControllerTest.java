package com.ainovel.app.v2;

import com.ainovel.app.admin.ops.OpsRecordFileSink;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.user.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class V2ModelControllerTest {

    @Test
    void listModelsShouldDelegateToPersistenceService() {
        ResourceAccessGuard accessGuard = mock(ResourceAccessGuard.class);
        V2ModelPersistenceService persistenceService = mock(V2ModelPersistenceService.class);
        OpsRecordFileSink recordFileSink = mock(OpsRecordFileSink.class);
        UserDetails principal = mock(UserDetails.class);
        when(accessGuard.currentUser(principal)).thenReturn(user());
        when(persistenceService.listModels()).thenReturn(List.of(Map.of(
                "modelKey", "deepseek-v4-flash",
                "displayName", "DeepSeek V4 Flash"
        )));
        V2ModelController controller = new V2ModelController(accessGuard, persistenceService, recordFileSink);

        List<Map<String, Object>> models = controller.listModels(principal);

        assertEquals(1, models.size());
        assertEquals("deepseek-v4-flash", models.get(0).get("modelKey"));
        assertEquals("DeepSeek V4 Flash", models.get(0).get("displayName"));
        verify(persistenceService).listModels();
    }

    @Test
    void retiredComparisonDoesNotFabricateModelOutputsOrUsage() throws Exception {
        var controller = new V2ModelController(mock(ResourceAccessGuard.class),
                mock(V2ModelPersistenceService.class), mock(OpsRecordFileSink.class));
        org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build()
                .perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/v2/stories/"+UUID.randomUUID()+"/compare-models")
                        .contentType("application/json").content("{}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void localAdminRoutingUpdateUsesAuthenticationSubjectWithoutResolvingOrdinaryUser() {
        ResourceAccessGuard accessGuard = mock(ResourceAccessGuard.class);
        V2ModelPersistenceService persistenceService = mock(V2ModelPersistenceService.class);
        OpsRecordFileSink recordFileSink = mock(OpsRecordFileSink.class);
        V2ModelController controller = new V2ModelController(accessGuard, persistenceService, recordFileSink);
        Map<String, Object> updated = Map.of("taskType", "draft_generation");
        var configuration = mock(com.ainovel.app.modelconfig.AiModelConfigurationService.class);
        org.springframework.test.util.ReflectionTestUtils.setField(controller,"modelConfiguration",configuration);
        var feature = new com.ainovel.app.modelconfig.AiModelConfigurationService.FeatureView(null,0,false,null,null,false,List.of(),List.of());
        when(configuration.update(org.mockito.ArgumentMatchers.eq("draft_generation"), org.mockito.ArgumentMatchers.any())).thenReturn(feature);
        when(configuration.asMap(feature)).thenReturn(updated);

        Map<String, Object> result = controller.updateRouting(
                new UsernamePasswordAuthenticationToken("local-operator", "n/a"),
                "draft_generation",
                V2RequestPayload.of(Map.of("options", List.of()))
        );

        assertEquals(updated, result);
        verifyNoInteractions(accessGuard);
        ArgumentCaptor<Map<String, Object>> audit = ArgumentCaptor.forClass(Map.class);
        verify(recordFileSink).appendAudit(audit.capture());
        assertEquals("local-operator", audit.getValue().get("actor"));
        assertEquals("model-routing.update", audit.getValue().get("action"));
        assertEquals("draft_generation", audit.getValue().get("targetId"));
    }

    private User user() {
        User user = new User();
        user.setId(UUID.fromString("0f41d89f-e04f-47e2-aa87-c2bf9a29fd0f"));
        user.setUsername("flowuser");
        user.setEmail("flowuser@example.com");
        user.setPasswordHash("hashed");
        user.setRemoteUid(9000003L);
        return user;
    }
}
