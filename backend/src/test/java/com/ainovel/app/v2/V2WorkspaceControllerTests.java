package com.ainovel.app.v2;

import com.ainovel.app.config.AppTimeProvider;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class V2WorkspaceControllerTests {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");

    private ResourceAccessGuard accessGuard;
    private V2WorkspacePersistenceService persistenceService;
    private V2WorkspaceController controller;
    private UserDetails principal;
    private User user;
    private Story story;
    private UUID storyId;

    @BeforeEach
    void setUp() {
        accessGuard = mock(ResourceAccessGuard.class);
        persistenceService = mock(V2WorkspacePersistenceService.class);

        AppTimeProvider timeProvider = mock(AppTimeProvider.class);
        LocalDate today = LocalDate.of(2026, 7, 8);
        Instant now = instant(today.atTime(12, 0));
        when(timeProvider.nowInstant()).thenReturn(now);
        when(timeProvider.today()).thenReturn(today);
        when(timeProvider.zoneId()).thenReturn(ZONE_ID);
        when(timeProvider.toLocalDate(any())).thenAnswer(invocation ->
                ((Instant) invocation.getArgument(0)).atZone(ZONE_ID).toLocalDate()
        );
        controller = new V2WorkspaceController(accessGuard, timeProvider, persistenceService);

        principal = mock(UserDetails.class);
        user = new User();
        user.setId(UUID.randomUUID());

        story = new Story();
        storyId = UUID.randomUUID();
        story.setId(storyId);

        when(accessGuard.currentUser(any())).thenReturn(user);
        when(accessGuard.requireOwnedStory(storyId, user)).thenReturn(story);
    }

    @Test
    void heartbeatShouldDelegateToPersistenceServiceWithHeartbeatFlag() {
        UUID sessionId = UUID.randomUUID();
        Map<String, Object> payload = Map.of("wordsWritten", 120, "wordsDeleted", 5);
        Map<String, Object> session = Map.of("id", sessionId, "status", "active");
        when(persistenceService.updateSession(user, sessionId, payload, false)).thenReturn(session);

        Map<String, Object> result = controller.heartbeat(principal, sessionId, payload);

        assertEquals(sessionId, result.get("id"));
        verify(persistenceService).updateSession(user, sessionId, payload, false);
    }

    @Test
    void createGoalShouldDelegateWithResolvedStory() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("storyId", storyId.toString());
        payload.put("goalType", "daily_words");
        payload.put("targetValue", 1500);

        Map<String, Object> goal = Map.of("id", UUID.randomUUID(), "storyId", storyId);
        when(persistenceService.createGoal(user, story, payload)).thenReturn(goal);

        Map<String, Object> result = controller.createGoal(principal, payload);

        assertEquals(storyId, result.get("storyId"));
        verify(accessGuard).requireOwnedStory(storyId, user);
        verify(persistenceService).createGoal(user, story, payload);
    }

    @Test
    void sessionStatsShouldAggregatePersistedSessionsByTimeWindow() {
        when(persistenceService.listSessions(user)).thenReturn(List.of(
                session(120, 20, 300, instant(LocalDateTime.of(2026, 7, 8, 10, 0))),
                session(60, 10, 120, instant(LocalDateTime.of(2026, 7, 6, 9, 0))),
                session(50, 10, 90, instant(LocalDateTime.of(2026, 6, 15, 8, 0)))
        ));

        Map<String, Object> stats = controller.sessionStats(principal);

        assertEquals(3, stats.get("totalSessions"));
        assertEquals(230, stats.get("totalWordsWritten"));
        assertEquals(40, stats.get("totalWordsDeleted"));
        assertEquals(190, stats.get("totalNetWords"));
        assertEquals(510L, stats.get("totalDurationSeconds"));
        assertEquals(63, stats.get("averageWordsPerSession"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dailySeries = (List<Map<String, Object>>) stats.get("dailySeries");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> weeklySeries = (List<Map<String, Object>>) stats.get("weeklySeries");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> monthlySeries = (List<Map<String, Object>>) stats.get("monthlySeries");

        Map<String, Object> todayEntry = findByField(dailySeries, "date", "2026-07-08");
        assertEquals(100, todayEntry.get("netWords"));
        assertEquals(1, todayEntry.get("sessions"));

        Map<String, Object> currentWeek = findByField(weeklySeries, "weekStart", "2026-07-06");
        assertEquals(150, currentWeek.get("netWords"));
        assertEquals(2, currentWeek.get("sessions"));

        Map<String, Object> july = findByField(monthlySeries, "month", "2026-07");
        assertEquals(150, july.get("netWords"));
        assertEquals(2, july.get("sessions"));

        Map<String, Object> june = findByField(monthlySeries, "month", "2026-06");
        assertEquals(40, june.get("netWords"));
        assertEquals(1, june.get("sessions"));
        verify(persistenceService).listSessions(user);
    }

    @Test
    void updateShortcutsShouldRejectNonArrayPayload() {
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                controller.updateShortcuts(principal, Map.of("shortcuts", Map.of("action", "save")))
        );
        assertTrue(ex.getMessage().contains("数组"));
    }

    @Test
    void updateShortcutsShouldDelegateShortcutListToPersistenceService() {
        List<Map<String, Object>> shortcuts = List.of(
                Map.of("action", "save", "shortcut", "Ctrl+Shift+S")
        );
        when(persistenceService.updateShortcuts(user, shortcuts)).thenReturn(shortcuts);

        List<Map<String, Object>> result = controller.updateShortcuts(principal, Map.of("shortcuts", shortcuts));

        assertEquals("Ctrl+Shift+S", result.get(0).get("shortcut"));
        verify(persistenceService).updateShortcuts(user, shortcuts);
    }

    private Map<String, Object> session(int wordsWritten, int wordsDeleted, int durationSeconds, Instant startedAt) {
        Map<String, Object> session = new HashMap<>();
        session.put("id", UUID.randomUUID());
        session.put("wordsWritten", wordsWritten);
        session.put("wordsDeleted", wordsDeleted);
        session.put("netWords", wordsWritten - wordsDeleted);
        session.put("durationSeconds", durationSeconds);
        session.put("startedAt", startedAt);
        session.put("endedAt", startedAt.plusSeconds(durationSeconds));
        return session;
    }

    private Instant instant(LocalDateTime dateTime) {
        return dateTime.atZone(ZONE_ID).toInstant();
    }

    private Map<String, Object> findByField(List<Map<String, Object>> items, String field, String value) {
        return items.stream()
                .filter(item -> value.equals(item.get(field)))
                .findFirst()
                .orElseThrow();
    }
}
