package com.ainovel.app.v2;

import com.ainovel.app.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@RestController
@RequestMapping("/v2")
public class V2WorkspaceController {
    private final V2AccessGuard accessGuard;

    private final ConcurrentMap<UUID, ConcurrentMap<UUID, Map<String, Object>>> layoutsByUser = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ConcurrentMap<UUID, Map<String, Object>>> sessionsByUser = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ConcurrentMap<UUID, Map<String, Object>>> goalsByUser = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ConcurrentMap<String, Map<String, Object>>> shortcutsByUser = new ConcurrentHashMap<>();

    public V2WorkspaceController(V2AccessGuard accessGuard) {
        this.accessGuard = accessGuard;
    }

    @GetMapping("/users/me/workspace-layouts")
    public List<Map<String, Object>> listLayouts(@AuthenticationPrincipal UserDetails principal) {
        User user = accessGuard.currentUser(principal);
        return new ArrayList<>(layoutsByUser.computeIfAbsent(user.getId(), uid -> new ConcurrentHashMap<>()).values());
    }

    @PostMapping("/users/me/workspace-layouts")
    public Map<String, Object> createLayout(@AuthenticationPrincipal UserDetails principal,
                                            @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        UUID id = UUID.randomUUID();
        Map<String, Object> layout = new HashMap<>();
        layout.put("id", id);
        layout.put("userId", user.getId());
        layout.put("name", str(payload.get("name"), "我的布局"));
        layout.put("layout", payload.getOrDefault("layout", Map.of("preset", "writing")));
        layout.put("isActive", boolVal(payload.get("isActive"), false));
        layout.put("createdAt", Instant.now());
        layout.put("updatedAt", Instant.now());

        ConcurrentMap<UUID, Map<String, Object>> userLayouts = layoutsByUser.computeIfAbsent(user.getId(), uid -> new ConcurrentHashMap<>());
        if (Boolean.TRUE.equals(layout.get("isActive"))) {
            for (Map<String, Object> existing : userLayouts.values()) {
                existing.put("isActive", false);
                existing.put("updatedAt", Instant.now());
            }
        }
        userLayouts.put(id, layout);
        return layout;
    }

    @PutMapping("/users/me/workspace-layouts/{id}")
    public Map<String, Object> updateLayout(@AuthenticationPrincipal UserDetails principal,
                                            @PathVariable UUID id,
                                            @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        Map<String, Object> layout = requireLayout(user.getId(), id);

        mergeIfPresent(payload, layout, "name");
        if (payload.containsKey("layout")) {
            layout.put("layout", payload.get("layout"));
        }
        if (payload.containsKey("isActive") && boolVal(payload.get("isActive"), false)) {
            for (Map<String, Object> existing : layoutsByUser.computeIfAbsent(user.getId(), uid -> new ConcurrentHashMap<>()).values()) {
                existing.put("isActive", false);
            }
            layout.put("isActive", true);
        }
        layout.put("updatedAt", Instant.now());
        return layout;
    }

    @DeleteMapping("/users/me/workspace-layouts/{id}")
    public ResponseEntity<Void> deleteLayout(@AuthenticationPrincipal UserDetails principal,
                                             @PathVariable UUID id) {
        User user = accessGuard.currentUser(principal);
        layoutsByUser.computeIfAbsent(user.getId(), uid -> new ConcurrentHashMap<>()).remove(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/me/workspace-layouts/{id}/activate")
    public Map<String, Object> activateLayout(@AuthenticationPrincipal UserDetails principal,
                                              @PathVariable UUID id) {
        User user = accessGuard.currentUser(principal);
        ConcurrentMap<UUID, Map<String, Object>> userLayouts = layoutsByUser.computeIfAbsent(user.getId(), uid -> new ConcurrentHashMap<>());
        Map<String, Object> layout = userLayouts.get(id);
        if (layout == null) {
            throw new RuntimeException("布局不存在");
        }
        for (Map<String, Object> existing : userLayouts.values()) {
            existing.put("isActive", false);
            existing.put("updatedAt", Instant.now());
        }
        layout.put("isActive", true);
        layout.put("updatedAt", Instant.now());
        return layout;
    }

    @PostMapping("/writing-sessions/start")
    public Map<String, Object> startSession(@AuthenticationPrincipal UserDetails principal,
                                            @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        UUID storyId = uuid(payload.get("storyId"));
        if (storyId == null) {
            throw new RuntimeException("storyId 不能为空");
        }
        accessGuard.requireOwnedStory(storyId, user);

        UUID id = UUID.randomUUID();
        Map<String, Object> session = new HashMap<>();
        session.put("id", id);
        session.put("userId", user.getId());
        session.put("storyId", storyId);
        session.put("startedAt", Instant.now());
        session.put("endedAt", null);
        session.put("wordsWritten", intVal(payload.get("wordsWritten"), 0));
        session.put("wordsDeleted", intVal(payload.get("wordsDeleted"), 0));
        session.put("netWords", intVal(payload.get("netWords"), 0));
        session.put("durationSeconds", 0);
        session.put("chaptersEdited", payload.getOrDefault("chaptersEdited", List.of()));

        sessionsByUser.computeIfAbsent(user.getId(), uid -> new ConcurrentHashMap<>()).put(id, session);
        return session;
    }

    @PutMapping("/writing-sessions/{id}/heartbeat")
    public Map<String, Object> heartbeat(@AuthenticationPrincipal UserDetails principal,
                                         @PathVariable UUID id,
                                         @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        Map<String, Object> session = requireSession(user.getId(), id);
        if (session.get("endedAt") != null) {
            throw new RuntimeException("会话已结束，无法继续心跳更新");
        }

        int previousNet = intVal(session.get("netWords"), 0);
        int wordsWritten = intVal(payload.get("wordsWritten"), intVal(session.get("wordsWritten"), 0));
        int wordsDeleted = intVal(payload.get("wordsDeleted"), intVal(session.get("wordsDeleted"), 0));
        session.put("wordsWritten", wordsWritten);
        session.put("wordsDeleted", wordsDeleted);
        session.put("netWords", wordsWritten - wordsDeleted);
        session.put("chaptersEdited", payload.getOrDefault("chaptersEdited", session.get("chaptersEdited")));
        session.put("durationSeconds", currentDurationSeconds(session));
        session.put("updatedAt", Instant.now());
        syncGoalProgress(user.getId(), session, wordsWritten - wordsDeleted - previousNet);
        return session;
    }

    @PostMapping("/writing-sessions/{id}/end")
    public Map<String, Object> endSession(@AuthenticationPrincipal UserDetails principal,
                                          @PathVariable UUID id,
                                          @RequestBody(required = false) Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        Map<String, Object> session = requireSession(user.getId(), id);

        if (session.get("endedAt") == null) {
            int previousNet = intVal(session.get("netWords"), 0);
            if (payload != null) {
                int wordsWritten = intVal(payload.get("wordsWritten"), intVal(session.get("wordsWritten"), 0));
                int wordsDeleted = intVal(payload.get("wordsDeleted"), intVal(session.get("wordsDeleted"), 0));
                session.put("wordsWritten", wordsWritten);
                session.put("wordsDeleted", wordsDeleted);
                session.put("netWords", wordsWritten - wordsDeleted);
                if (payload.containsKey("chaptersEdited")) {
                    session.put("chaptersEdited", payload.get("chaptersEdited"));
                }
            }
            session.put("endedAt", Instant.now());
            session.put("durationSeconds", currentDurationSeconds(session));
            syncGoalProgress(user.getId(), session, intVal(session.get("netWords"), 0) - previousNet);
        }
        return session;
    }

    @GetMapping("/writing-sessions/stats")
    public Map<String, Object> sessionStats(@AuthenticationPrincipal UserDetails principal) {
        User user = accessGuard.currentUser(principal);
        Collection<Map<String, Object>> sessions = sessionsByUser.computeIfAbsent(user.getId(), uid -> new ConcurrentHashMap<>()).values();

        int totalSessions = sessions.size();
        int totalWritten = 0;
        int totalDeleted = 0;
        int totalNet = 0;
        long totalDuration = 0;
        for (Map<String, Object> session : sessions) {
            totalWritten += intVal(session.get("wordsWritten"), 0);
            totalDeleted += intVal(session.get("wordsDeleted"), 0);
            totalNet += intVal(session.get("netWords"), 0);
            totalDuration += intVal(session.get("durationSeconds"), 0);
        }

        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zoneId);
        Map<LocalDate, int[]> daily = new HashMap<>();
        Map<LocalDate, int[]> weekly = new HashMap<>();
        Map<YearMonth, int[]> monthly = new HashMap<>();
        for (Map<String, Object> session : sessions) {
            Object startedObj = session.get("startedAt");
            if (!(startedObj instanceof Instant startedAt)) {
                continue;
            }
            LocalDate day = startedAt.atZone(zoneId).toLocalDate();
            int net = intVal(session.get("netWords"), 0);
            int duration = intVal(session.get("durationSeconds"), 0);
            daily.computeIfAbsent(day, key -> new int[] {0, 0, 0});
            daily.get(day)[0] += net;
            daily.get(day)[1] += duration;
            daily.get(day)[2] += 1;

            LocalDate weekStart = day.minusDays(day.getDayOfWeek().getValue() - 1L);
            weekly.computeIfAbsent(weekStart, key -> new int[] {0, 0});
            weekly.get(weekStart)[0] += net;
            weekly.get(weekStart)[1] += 1;

            YearMonth ym = YearMonth.from(day);
            monthly.computeIfAbsent(ym, key -> new int[] {0, 0});
            monthly.get(ym)[0] += net;
            monthly.get(ym)[1] += 1;
        }

        List<Map<String, Object>> dailySeries = new ArrayList<>();
        for (int i = 29; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            int[] values = daily.getOrDefault(day, new int[] {0, 0, 0});
            dailySeries.add(Map.of(
                    "date", day.toString(),
                    "netWords", values[0],
                    "durationSeconds", values[1],
                    "sessions", values[2]
            ));
        }

        List<Map<String, Object>> weeklySeries = new ArrayList<>();
        LocalDate thisWeekStart = today.minusDays(today.getDayOfWeek().getValue() - 1L);
        for (int i = 11; i >= 0; i--) {
            LocalDate weekStart = thisWeekStart.minusWeeks(i);
            int[] values = weekly.getOrDefault(weekStart, new int[] {0, 0});
            weeklySeries.add(Map.of(
                    "weekStart", weekStart.toString(),
                    "netWords", values[0],
                    "sessions", values[1]
            ));
        }

        List<Map<String, Object>> monthSeries = new ArrayList<>();
        YearMonth currentMonth = YearMonth.now(zoneId);
        for (int i = 11; i >= 0; i--) {
            YearMonth ym = currentMonth.minusMonths(i);
            int[] values = monthly.getOrDefault(ym, new int[] {0, 0});
            monthSeries.add(Map.of(
                    "month", ym.toString(),
                    "netWords", values[0],
                    "sessions", values[1]
            ));
        }

        return Map.of(
                "totalSessions", totalSessions,
                "totalWordsWritten", totalWritten,
                "totalWordsDeleted", totalDeleted,
                "totalNetWords", totalNet,
                "totalDurationSeconds", totalDuration,
                "averageWordsPerSession", totalSessions == 0 ? 0 : totalNet / totalSessions,
                "dailySeries", dailySeries,
                "weeklySeries", weeklySeries,
                "monthlySeries", monthSeries
        );
    }

    @GetMapping("/users/me/writing-goals")
    public List<Map<String, Object>> listGoals(@AuthenticationPrincipal UserDetails principal) {
        User user = accessGuard.currentUser(principal);
        ConcurrentMap<UUID, Map<String, Object>> goals = goalsByUser.computeIfAbsent(user.getId(), uid -> new ConcurrentHashMap<>());
        goals.values().forEach(this::applyDailyResetIfNeeded);
        return new ArrayList<>(goals.values());
    }

    @PostMapping("/users/me/writing-goals")
    public Map<String, Object> createGoal(@AuthenticationPrincipal UserDetails principal,
                                          @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        UUID storyId = uuid(payload.get("storyId"));
        if (storyId != null) {
            accessGuard.requireOwnedStory(storyId, user);
        }

        UUID id = UUID.randomUUID();
        Map<String, Object> goal = new HashMap<>();
        goal.put("id", id);
        goal.put("userId", user.getId());
        goal.put("storyId", storyId);
        goal.put("goalType", str(payload.get("goalType"), "daily_words"));
        goal.put("targetValue", intVal(payload.get("targetValue"), 1000));
        goal.put("currentValue", intVal(payload.get("currentValue"), 0));
        goal.put("deadline", payload.get("deadline"));
        goal.put("status", str(payload.get("status"), "active"));
        goal.put("createdAt", Instant.now());
        goal.put("updatedAt", Instant.now());

        goalsByUser.computeIfAbsent(user.getId(), uid -> new ConcurrentHashMap<>()).put(id, goal);
        return goal;
    }

    @PutMapping("/users/me/writing-goals/{id}")
    public Map<String, Object> updateGoal(@AuthenticationPrincipal UserDetails principal,
                                          @PathVariable UUID id,
                                          @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        Map<String, Object> goal = goalsByUser.computeIfAbsent(user.getId(), uid -> new ConcurrentHashMap<>()).get(id);
        if (goal == null) {
            throw new RuntimeException("写作目标不存在");
        }

        mergeIfPresent(payload, goal, "goalType", "deadline", "status");
        if (payload.containsKey("targetValue")) {
            goal.put("targetValue", intVal(payload.get("targetValue"), intVal(goal.get("targetValue"), 1000)));
        }
        if (payload.containsKey("currentValue")) {
            goal.put("currentValue", intVal(payload.get("currentValue"), intVal(goal.get("currentValue"), 0)));
        }
        goal.put("updatedAt", Instant.now());
        return goal;
    }

    @DeleteMapping("/users/me/writing-goals/{id}")
    public ResponseEntity<Void> deleteGoal(@AuthenticationPrincipal UserDetails principal,
                                           @PathVariable UUID id) {
        User user = accessGuard.currentUser(principal);
        goalsByUser.computeIfAbsent(user.getId(), uid -> new ConcurrentHashMap<>()).remove(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/me/shortcuts")
    public List<Map<String, Object>> listShortcuts(@AuthenticationPrincipal UserDetails principal) {
        User user = accessGuard.currentUser(principal);
        return new ArrayList<>(shortcutsByUser.computeIfAbsent(user.getId(), uid -> defaultShortcuts(user.getId())).values());
    }

    @PutMapping("/users/me/shortcuts")
    public List<Map<String, Object>> updateShortcuts(@AuthenticationPrincipal UserDetails principal,
                                                     @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        ConcurrentMap<String, Map<String, Object>> userShortcuts = shortcutsByUser.computeIfAbsent(user.getId(), uid -> defaultShortcuts(user.getId()));

        Object shortcutsObj = payload.get("shortcuts");
        if (!(shortcutsObj instanceof List<?> list)) {
            throw new RuntimeException("shortcuts 必须为数组");
        }

        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> shortcut = (Map<String, Object>) raw;
            String action = str(shortcut.get("action"), "");
            String key = str(shortcut.get("shortcut"), "");
            if (action.isEmpty() || key.isEmpty()) {
                continue;
            }
            Map<String, Object> value = new HashMap<>();
            value.put("id", UUID.randomUUID());
            value.put("userId", user.getId());
            value.put("action", action);
            value.put("shortcut", key);
            value.put("isCustom", true);
            value.put("createdAt", Instant.now());
            userShortcuts.put(action, value);
        }

        return new ArrayList<>(userShortcuts.values());
    }

    private Map<String, Object> requireLayout(UUID userId, UUID layoutId) {
        Map<String, Object> layout = layoutsByUser.computeIfAbsent(userId, uid -> new ConcurrentHashMap<>()).get(layoutId);
        if (layout == null) {
            throw new RuntimeException("布局不存在");
        }
        return layout;
    }

    private Map<String, Object> requireSession(UUID userId, UUID sessionId) {
        Map<String, Object> session = sessionsByUser.computeIfAbsent(userId, uid -> new ConcurrentHashMap<>()).get(sessionId);
        if (session == null) {
            throw new RuntimeException("写作会话不存在");
        }
        return session;
    }

    private int currentDurationSeconds(Map<String, Object> session) {
        Instant startedAt = (Instant) session.get("startedAt");
        Instant endedAt = session.get("endedAt") instanceof Instant ended ? ended : Instant.now();
        return (int) Duration.between(startedAt, endedAt).toSeconds();
    }

    private ConcurrentMap<String, Map<String, Object>> defaultShortcuts(UUID userId) {
        ConcurrentMap<String, Map<String, Object>> defaults = new ConcurrentHashMap<>();
        defaults.put("save", shortcut(userId, "save", "Ctrl+S", false));
        defaults.put("focus_mode", shortcut(userId, "focus_mode", "Ctrl+Shift+F", false));
        defaults.put("command_palette", shortcut(userId, "command_palette", "Ctrl+K", false));
        defaults.put("toggle_left_panel", shortcut(userId, "toggle_left_panel", "Ctrl+B", false));
        defaults.put("toggle_right_panel", shortcut(userId, "toggle_right_panel", "Ctrl+Shift+B", false));
        defaults.put("next_chapter", shortcut(userId, "next_chapter", "Ctrl+]", false));
        defaults.put("prev_chapter", shortcut(userId, "prev_chapter", "Ctrl+[", false));
        defaults.put("ai_refine", shortcut(userId, "ai_refine", "Ctrl+Shift+R", false));
        defaults.put("new_scene", shortcut(userId, "new_scene", "Ctrl+Shift+N", false));
        defaults.put("search_manuscript", shortcut(userId, "search_manuscript", "Ctrl+F", false));
        defaults.put("search_replace", shortcut(userId, "search_replace", "Ctrl+H", false));
        defaults.put("export", shortcut(userId, "export", "Ctrl+Shift+E", false));
        defaults.put("close_tab", shortcut(userId, "close_tab", "Ctrl+W", false));
        defaults.put("next_tab", shortcut(userId, "next_tab", "Ctrl+Tab", false));
        defaults.put("undo", shortcut(userId, "undo", "Ctrl+Z", false));
        defaults.put("redo", shortcut(userId, "redo", "Ctrl+Shift+Z", false));
        return defaults;
    }

    private Map<String, Object> shortcut(UUID userId, String action, String key, boolean custom) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", UUID.randomUUID());
        data.put("userId", userId);
        data.put("action", action);
        data.put("shortcut", key);
        data.put("isCustom", custom);
        data.put("createdAt", Instant.now());
        return data;
    }

    private UUID uuid(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID id) {
            return id;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private int intVal(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String str(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? fallback : text;
    }

    private boolean boolVal(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private void mergeIfPresent(Map<String, Object> source, Map<String, Object> target, String... keys) {
        for (String key : keys) {
            if (source.containsKey(key)) {
                target.put(key, source.get(key));
            }
        }
    }

    private void syncGoalProgress(UUID userId, Map<String, Object> session, int deltaNetWords) {
        if (deltaNetWords == 0) {
            return;
        }
        ConcurrentMap<UUID, Map<String, Object>> goals = goalsByUser.computeIfAbsent(userId, uid -> new ConcurrentHashMap<>());
        UUID storyId = uuid(session.get("storyId"));
        for (Map<String, Object> goal : goals.values()) {
            applyDailyResetIfNeeded(goal);
            String status = str(goal.get("status"), "active");
            if (!"active".equalsIgnoreCase(status)) {
                continue;
            }
            UUID goalStoryId = uuid(goal.get("storyId"));
            if (goalStoryId != null && storyId != null && !Objects.equals(goalStoryId, storyId)) {
                continue;
            }
            String goalType = str(goal.get("goalType"), "daily_words");
            if (!goalType.contains("words")) {
                continue;
            }
            int current = intVal(goal.get("currentValue"), 0);
            int target = Math.max(1, intVal(goal.get("targetValue"), 1));
            int nextValue = Math.max(0, current + deltaNetWords);
            goal.put("currentValue", nextValue);
            if (nextValue >= target) {
                goal.put("status", "completed");
            }
            goal.put("updatedAt", Instant.now());
        }
    }

    private void applyDailyResetIfNeeded(Map<String, Object> goal) {
        String goalType = str(goal.get("goalType"), "");
        if (!goalType.startsWith("daily_")) {
            return;
        }
        Object updatedObj = goal.get("updatedAt");
        if (!(updatedObj instanceof Instant updatedAt)) {
            return;
        }
        LocalDate updatedDay = updatedAt.atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        if (!updatedDay.isEqual(today)) {
            goal.put("currentValue", 0);
            if (!"archived".equalsIgnoreCase(str(goal.get("status"), ""))) {
                goal.put("status", "active");
            }
            goal.put("updatedAt", Instant.now());
        }
    }
}
