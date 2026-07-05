package com.ainovel.app.v2;

import com.ainovel.app.common.BusinessException;
import com.ainovel.app.config.AppTimeProvider;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Tag(name = "V2", description = "AINovel v2 and quality APIs")
@RestController
@RequestMapping("/v2")
public class V2WorkspaceController {
    private final ResourceAccessGuard accessGuard;
    private final AppTimeProvider timeProvider;
    private final V2WorkspacePersistenceService persistenceService;

    @Autowired
    public V2WorkspaceController(ResourceAccessGuard accessGuard,
                                 AppTimeProvider timeProvider,
                                 V2WorkspacePersistenceService persistenceService) {
        this.accessGuard = accessGuard;
        this.timeProvider = timeProvider;
        this.persistenceService = persistenceService;
    }

    @Operation(summary = "v2 API endpoint")

    @GetMapping("/users/me/workspace-layouts")
    public List<Map<String, Object>> listLayouts(@AuthenticationPrincipal UserDetails principal) {
        User user = accessGuard.currentUser(principal);
        return persistenceService.listLayouts(user);
    }

    @Operation(summary = "v2 API endpoint")

    @PostMapping("/users/me/workspace-layouts")
    public Map<String, Object> createLayout(@AuthenticationPrincipal UserDetails principal,
                                            @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        return persistenceService.createLayout(user, payload);
    }

    @Operation(summary = "v2 API endpoint")

    @PutMapping("/users/me/workspace-layouts/{id}")
    public Map<String, Object> updateLayout(@AuthenticationPrincipal UserDetails principal,
                                            @PathVariable UUID id,
                                            @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        return persistenceService.updateLayout(user, id, payload);
    }

    @Operation(summary = "v2 API endpoint")

    @DeleteMapping("/users/me/workspace-layouts/{id}")
    public ResponseEntity<Void> deleteLayout(@AuthenticationPrincipal UserDetails principal,
                                             @PathVariable UUID id) {
        User user = accessGuard.currentUser(principal);
        persistenceService.deleteLayout(user, id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "v2 API endpoint")

    @PostMapping("/users/me/workspace-layouts/{id}/activate")
    public Map<String, Object> activateLayout(@AuthenticationPrincipal UserDetails principal,
                                              @PathVariable UUID id) {
        User user = accessGuard.currentUser(principal);
        return persistenceService.activateLayout(user, id);
    }

    @Operation(summary = "v2 API endpoint")

    @PostMapping("/writing-sessions/start")
    public Map<String, Object> startSession(@AuthenticationPrincipal UserDetails principal,
                                            @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        UUID storyId = uuid(payload.get("storyId"));
        if (storyId == null) {
            throw new BusinessException("storyId 不能为空");
        }
        Story story = accessGuard.requireOwnedStory(storyId, user);
        return persistenceService.startSession(user, story, payload);
    }

    @Operation(summary = "v2 API endpoint")

    @PutMapping("/writing-sessions/{id}/heartbeat")
    public Map<String, Object> heartbeat(@AuthenticationPrincipal UserDetails principal,
                                         @PathVariable UUID id,
                                         @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        return persistenceService.updateSession(user, id, payload, false);
    }

    @Operation(summary = "v2 API endpoint")

    @PostMapping("/writing-sessions/{id}/end")
    public Map<String, Object> endSession(@AuthenticationPrincipal UserDetails principal,
                                          @PathVariable UUID id,
                                          @RequestBody(required = false) Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        return persistenceService.updateSession(user, id, payload == null ? Map.of() : payload, true);
    }

    @Operation(summary = "v2 API endpoint")

    @GetMapping("/writing-sessions/stats")
    public Map<String, Object> sessionStats(@AuthenticationPrincipal UserDetails principal) {
        User user = accessGuard.currentUser(principal);
        Collection<Map<String, Object>> sessions = persistenceService.listSessions(user);

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

        LocalDate today = timeProvider.today();
        var zoneId = timeProvider.zoneId();
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

    @Operation(summary = "v2 API endpoint")

    @GetMapping("/users/me/writing-goals")
    public List<Map<String, Object>> listGoals(@AuthenticationPrincipal UserDetails principal) {
        User user = accessGuard.currentUser(principal);
        return persistenceService.listGoals(user);
    }

    @Operation(summary = "v2 API endpoint")

    @PostMapping("/users/me/writing-goals")
    public Map<String, Object> createGoal(@AuthenticationPrincipal UserDetails principal,
                                          @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        UUID storyId = uuid(payload.get("storyId"));
        if (storyId != null) {
            Story story = accessGuard.requireOwnedStory(storyId, user);
            return persistenceService.createGoal(user, story, payload);
        }
        return persistenceService.createGoal(user, null, payload);
    }

    @Operation(summary = "v2 API endpoint")

    @PutMapping("/users/me/writing-goals/{id}")
    public Map<String, Object> updateGoal(@AuthenticationPrincipal UserDetails principal,
                                          @PathVariable UUID id,
                                          @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        return persistenceService.updateGoal(user, id, payload);
    }

    @Operation(summary = "v2 API endpoint")

    @DeleteMapping("/users/me/writing-goals/{id}")
    public ResponseEntity<Void> deleteGoal(@AuthenticationPrincipal UserDetails principal,
                                           @PathVariable UUID id) {
        User user = accessGuard.currentUser(principal);
        persistenceService.deleteGoal(user, id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "v2 API endpoint")

    @GetMapping("/users/me/shortcuts")
    public List<Map<String, Object>> listShortcuts(@AuthenticationPrincipal UserDetails principal) {
        User user = accessGuard.currentUser(principal);
        return persistenceService.listShortcuts(user);
    }

    @Operation(summary = "v2 API endpoint")

    @PutMapping("/users/me/shortcuts")
    public List<Map<String, Object>> updateShortcuts(@AuthenticationPrincipal UserDetails principal,
                                                     @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        Object shortcutsObj = payload.get("shortcuts");
        if (!(shortcutsObj instanceof List<?> list)) {
            throw new BusinessException("shortcuts 必须为数组");
        }
        return persistenceService.updateShortcuts(user, list);
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
}
