package com.ainovel.app.v2;

import com.ainovel.app.story.model.Story;
import com.ainovel.app.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class V2WorkspaceControllerTests {

    private V2AccessGuard accessGuard;
    private V2WorkspaceController controller;
    private UserDetails principal;
    private User user;
    private UUID storyId;

    @BeforeEach
    void setUp() {
        accessGuard = mock(V2AccessGuard.class);
        controller = new V2WorkspaceController(accessGuard);

        principal = mock(UserDetails.class);
        user = new User();
        user.setId(UUID.randomUUID());

        Story story = new Story();
        storyId = UUID.randomUUID();
        story.setId(storyId);

        when(accessGuard.currentUser(any())).thenReturn(user);
        when(accessGuard.requireOwnedStory(storyId, user)).thenReturn(story);
    }

    @Test
    void heartbeatShouldFailAfterSessionEnded() {
        Map<String, Object> session = controller.startSession(principal, Map.of("storyId", storyId));
        UUID sessionId = (UUID) session.get("id");

        controller.endSession(principal, sessionId, Map.of("wordsWritten", 100, "wordsDeleted", 5));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                controller.heartbeat(principal, sessionId, Map.of("wordsWritten", 120, "wordsDeleted", 5))
        );
        assertTrue(ex.getMessage().contains("会话已结束"));
    }

    @Test
    void updateShortcutsShouldRejectNonArrayPayload() {
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                controller.updateShortcuts(principal, Map.of("shortcuts", Map.of("action", "save")))
        );
        assertTrue(ex.getMessage().contains("数组"));
    }
}
