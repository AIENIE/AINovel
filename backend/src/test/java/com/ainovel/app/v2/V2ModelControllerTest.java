package com.ainovel.app.v2;

import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class V2ModelControllerTest {

    @Test
    void shouldExposeOnlyDeepseekV4FlashModel() {
        ResourceAccessGuard accessGuard = mock(ResourceAccessGuard.class);
        UserDetails principal = mock(UserDetails.class);
        when(accessGuard.currentUser(principal)).thenReturn(user());
        V2ModelController controller = new V2ModelController(accessGuard);

        List<Map<String, Object>> models = controller.listModels(principal);

        assertEquals(1, models.size());
        assertEquals("deepseek-v4-flash", models.get(0).get("modelKey"));
        assertEquals("DeepSeek V4 Flash", models.get(0).get("displayName"));
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
