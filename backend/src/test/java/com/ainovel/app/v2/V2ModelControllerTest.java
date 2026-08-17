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
    void compareModelsShouldUsePersistedModelsAndRecordUsage() {
        ResourceAccessGuard accessGuard = mock(ResourceAccessGuard.class);
        V2ModelPersistenceService persistenceService = mock(V2ModelPersistenceService.class);
        OpsRecordFileSink recordFileSink = mock(OpsRecordFileSink.class);
        UserDetails principal = mock(UserDetails.class);
        User user = user();
        UUID storyId = UUID.randomUUID();
        UUID modelAId = UUID.randomUUID();
        UUID modelBId = UUID.randomUUID();
        Story story = new Story();
        story.setId(storyId);
        story.setTitle("雨城疑案");

        when(accessGuard.currentUser(principal)).thenReturn(user);
        when(accessGuard.requireOwnedStory(storyId, user)).thenReturn(story);
        when(persistenceService.listModels()).thenReturn(List.of(
                Map.of("id", modelAId, "modelKey", "deepseek-v4-flash", "displayName", "DeepSeek V4 Flash"),
                Map.of("id", modelBId, "modelKey", "deepseek-v4-pro", "displayName", "DeepSeek V4 Pro")
        ));
        V2ModelController controller = new V2ModelController(accessGuard, persistenceService, recordFileSink);

        Map<String, Object> result = controller.compareModels(principal, storyId, V2RequestPayload.of(Map.of(
                "modelAId", modelAId.toString(),
                "modelBId", modelBId.toString(),
                "taskType", "analysis",
                "prompt", "请分析这段剧情"
        )));

        assertEquals("analysis", result.get("taskType"));
        assertEquals("请分析这段剧情", result.get("prompt"));
        assertEquals(2, ((List<?>) result.get("candidates")).size());
        verify(persistenceService).listModels();
        verify(persistenceService).logUsage(user, storyId, modelAId, "analysis", 220, 340, 580, true, null);
        verify(persistenceService).logUsage(user, storyId, modelBId, "analysis", 210, 330, 560, true, null);
        verify(accessGuard).requireOwnedStory(storyId, user);
    }

    @Test
    void localAdminRoutingUpdateUsesAuthenticationSubjectWithoutResolvingOrdinaryUser() {
        ResourceAccessGuard accessGuard = mock(ResourceAccessGuard.class);
        V2ModelPersistenceService persistenceService = mock(V2ModelPersistenceService.class);
        OpsRecordFileSink recordFileSink = mock(OpsRecordFileSink.class);
        V2ModelController controller = new V2ModelController(accessGuard, persistenceService, recordFileSink);
        UUID recommended = UUID.randomUUID();
        Map<String, Object> updated = Map.of("taskType", "draft_generation");
        when(persistenceService.updateRouting(
                "draft_generation", recommended, null, "fixed", Map.of()
        )).thenReturn(updated);

        Map<String, Object> result = controller.updateRouting(
                new UsernamePasswordAuthenticationToken("local-operator", "n/a"),
                "draft_generation",
                V2RequestPayload.of(Map.of("recommendedModelId", recommended.toString()))
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
