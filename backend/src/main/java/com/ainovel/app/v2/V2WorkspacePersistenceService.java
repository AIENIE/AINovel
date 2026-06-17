package com.ainovel.app.v2;

import com.ainovel.app.config.AppTimeProvider;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.user.User;
import com.ainovel.app.v2.model.V2KeyboardShortcut;
import com.ainovel.app.v2.model.V2WorkspaceLayout;
import com.ainovel.app.v2.model.V2WritingGoal;
import com.ainovel.app.v2.model.V2WritingSession;
import com.ainovel.app.v2.repo.V2KeyboardShortcutRepository;
import com.ainovel.app.v2.repo.V2WorkspaceLayoutRepository;
import com.ainovel.app.v2.repo.V2WritingGoalRepository;
import com.ainovel.app.v2.repo.V2WritingSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
public class V2WorkspacePersistenceService {
    private final V2WorkspaceLayoutRepository layoutRepository;
    private final V2WritingSessionRepository sessionRepository;
    private final V2WritingGoalRepository goalRepository;
    private final V2KeyboardShortcutRepository shortcutRepository;
    private final AppTimeProvider timeProvider;

    public V2WorkspacePersistenceService(V2WorkspaceLayoutRepository layoutRepository,
                                         V2WritingSessionRepository sessionRepository,
                                         V2WritingGoalRepository goalRepository,
                                         V2KeyboardShortcutRepository shortcutRepository,
                                         AppTimeProvider timeProvider) {
        this.layoutRepository = layoutRepository;
        this.sessionRepository = sessionRepository;
        this.goalRepository = goalRepository;
        this.shortcutRepository = shortcutRepository;
        this.timeProvider = timeProvider;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listLayouts(User user) {
        return layoutRepository.findByUserId(user.getId()).stream().map(this::layoutMap).toList();
    }

    @Transactional
    public Map<String, Object> createLayout(User user, Map<String, Object> payload) {
        if (boolVal(payload.get("isActive"), false)) {
            for (V2WorkspaceLayout existing : layoutRepository.findByUserId(user.getId())) {
                existing.setActive(false);
            }
        }
        V2WorkspaceLayout layout = new V2WorkspaceLayout();
        layout.setUser(user);
        layout.setName(str(payload.get("name"), "我的布局"));
        layout.setLayoutJson(V2Json.write(payload.getOrDefault("layout", Map.of("preset", "writing"))));
        layout.setActive(boolVal(payload.get("isActive"), false));
        return layoutMap(layoutRepository.save(layout));
    }

    @Transactional
    public Map<String, Object> updateLayout(User user, UUID id, Map<String, Object> payload) {
        V2WorkspaceLayout layout = layoutRepository.findByUserIdAndId(user.getId(), id).orElseThrow(() -> new RuntimeException("布局不存在"));
        if (payload.containsKey("name")) layout.setName(str(payload.get("name"), layout.getName()));
        if (payload.containsKey("layout")) layout.setLayoutJson(V2Json.write(payload.get("layout")));
        if (payload.containsKey("isActive") && boolVal(payload.get("isActive"), false)) {
            for (V2WorkspaceLayout existing : layoutRepository.findByUserId(user.getId())) {
                existing.setActive(false);
            }
            layout.setActive(true);
        }
        return layoutMap(layoutRepository.save(layout));
    }

    @Transactional
    public void deleteLayout(User user, UUID id) {
        layoutRepository.delete(layoutRepository.findByUserIdAndId(user.getId(), id).orElseThrow(() -> new RuntimeException("布局不存在")));
    }

    @Transactional
    public Map<String, Object> activateLayout(User user, UUID id) {
        for (V2WorkspaceLayout existing : layoutRepository.findByUserId(user.getId())) {
            existing.setActive(false);
        }
        V2WorkspaceLayout layout = layoutRepository.findByUserIdAndId(user.getId(), id).orElseThrow(() -> new RuntimeException("布局不存在"));
        layout.setActive(true);
        return layoutMap(layoutRepository.save(layout));
    }

    @Transactional
    public Map<String, Object> startSession(User user, Story story, Map<String, Object> payload) {
        V2WritingSession session = new V2WritingSession();
        session.setUser(user);
        session.setStory(story);
        session.setStartedAt(now());
        session.setWordsWritten(intVal(payload.get("wordsWritten"), 0));
        session.setWordsDeleted(intVal(payload.get("wordsDeleted"), 0));
        session.setNetWords(intVal(payload.get("netWords"), 0));
        session.setDurationSeconds(0);
        session.setChaptersEditedJson(V2Json.write(payload.getOrDefault("chaptersEdited", List.of())));
        return sessionMap(sessionRepository.save(session));
    }

    @Transactional
    public Map<String, Object> updateSession(User user, UUID id, Map<String, Object> payload, boolean end) {
        V2WritingSession session = sessionRepository.findByUserIdAndId(user.getId(), id).orElseThrow(() -> new RuntimeException("写作会话不存在"));
        if (!end && session.getEndedAt() != null) {
            throw new RuntimeException("会话已结束，无法继续心跳更新");
        }
        int previousNet = session.getNetWords();
        int written = intVal(payload.get("wordsWritten"), session.getWordsWritten());
        int deleted = intVal(payload.get("wordsDeleted"), session.getWordsDeleted());
        session.setWordsWritten(written);
        session.setWordsDeleted(deleted);
        session.setNetWords(written - deleted);
        session.setDurationSeconds(currentDurationSeconds(session));
        if (payload.containsKey("chaptersEdited")) {
            session.setChaptersEditedJson(V2Json.write(payload.get("chaptersEdited")));
        }
        if (end && session.getEndedAt() == null) {
            session.setEndedAt(now());
        }
        syncGoalProgress(user, session.getStory().getId(), session.getNetWords() - previousNet);
        return sessionMap(sessionRepository.save(session));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listSessions(User user) {
        return sessionRepository.findByUserId(user.getId()).stream().map(this::sessionMap).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listGoals(User user) {
        return goalRepository.findByUserId(user.getId()).stream().map(this::goalMap).toList();
    }

    @Transactional
    public Map<String, Object> createGoal(User user, Story story, Map<String, Object> payload) {
        V2WritingGoal goal = new V2WritingGoal();
        goal.setUser(user);
        goal.setStory(story);
        goal.setGoalType(str(payload.get("goalType"), "daily_words"));
        goal.setTargetValue(intVal(payload.get("targetValue"), 1000));
        goal.setCurrentValue(intVal(payload.get("currentValue"), 0));
        goal.setDeadline(date(payload.get("deadline")));
        goal.setStatus(str(payload.get("status"), "active"));
        return goalMap(goalRepository.save(goal));
    }

    @Transactional
    public Map<String, Object> updateGoal(User user, UUID id, Map<String, Object> payload) {
        V2WritingGoal goal = goalRepository.findByUserIdAndId(user.getId(), id).orElseThrow(() -> new RuntimeException("写作目标不存在"));
        if (payload.containsKey("goalType")) goal.setGoalType(str(payload.get("goalType"), goal.getGoalType()));
        if (payload.containsKey("targetValue")) goal.setTargetValue(intVal(payload.get("targetValue"), goal.getTargetValue()));
        if (payload.containsKey("currentValue")) goal.setCurrentValue(intVal(payload.get("currentValue"), goal.getCurrentValue()));
        if (payload.containsKey("deadline")) goal.setDeadline(date(payload.get("deadline")));
        if (payload.containsKey("status")) goal.setStatus(str(payload.get("status"), goal.getStatus()));
        return goalMap(goalRepository.save(goal));
    }

    @Transactional
    public void deleteGoal(User user, UUID id) {
        goalRepository.delete(goalRepository.findByUserIdAndId(user.getId(), id).orElseThrow(() -> new RuntimeException("写作目标不存在")));
    }

    @Transactional
    public List<Map<String, Object>> listShortcuts(User user) {
        ensureDefaultShortcuts(user);
        return shortcutRepository.findByUserId(user.getId()).stream().map(this::shortcutMap).toList();
    }

    @Transactional
    public List<Map<String, Object>> updateShortcuts(User user, List<?> shortcuts) {
        ensureDefaultShortcuts(user);
        for (Object item : shortcuts) {
            if (!(item instanceof Map<?, ?> raw)) continue;
            String action = str(raw.get("action"), "");
            String key = str(raw.get("shortcut"), "");
            if (action.isBlank() || key.isBlank()) continue;
            V2KeyboardShortcut shortcut = shortcutRepository.findByUserIdAndAction(user.getId(), action).orElseGet(V2KeyboardShortcut::new);
            shortcut.setUser(user);
            shortcut.setAction(action);
            shortcut.setShortcut(key);
            shortcut.setCustom(true);
            shortcutRepository.save(shortcut);
        }
        return listShortcuts(user);
    }

    private void ensureDefaultShortcuts(User user) {
        if (!shortcutRepository.findByUserId(user.getId()).isEmpty()) return;
        for (Map.Entry<String, String> item : defaultShortcuts().entrySet()) {
            V2KeyboardShortcut shortcut = new V2KeyboardShortcut();
            shortcut.setUser(user);
            shortcut.setAction(item.getKey());
            shortcut.setShortcut(item.getValue());
            shortcut.setCustom(false);
            shortcutRepository.save(shortcut);
        }
    }

    private Map<String, Object> layoutMap(V2WorkspaceLayout layout) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", layout.getId());
        out.put("userId", layout.getUser().getId());
        out.put("name", layout.getName());
        out.put("layout", V2Json.map(layout.getLayoutJson()));
        out.put("isActive", layout.isActive());
        out.put("createdAt", layout.getCreatedAt());
        out.put("updatedAt", layout.getUpdatedAt());
        return out;
    }

    private Map<String, Object> sessionMap(V2WritingSession session) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", session.getId());
        out.put("userId", session.getUser().getId());
        out.put("storyId", session.getStory().getId());
        out.put("startedAt", session.getStartedAt());
        out.put("endedAt", session.getEndedAt());
        out.put("wordsWritten", session.getWordsWritten());
        out.put("wordsDeleted", session.getWordsDeleted());
        out.put("netWords", session.getNetWords());
        out.put("durationSeconds", session.getDurationSeconds());
        out.put("chaptersEdited", V2Json.list(session.getChaptersEditedJson()));
        return out;
    }

    private Map<String, Object> goalMap(V2WritingGoal goal) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", goal.getId());
        out.put("userId", goal.getUser().getId());
        out.put("storyId", goal.getStory() == null ? null : goal.getStory().getId());
        out.put("goalType", goal.getGoalType());
        out.put("targetValue", goal.getTargetValue());
        out.put("currentValue", goal.getCurrentValue());
        out.put("deadline", goal.getDeadline());
        out.put("status", goal.getStatus());
        out.put("createdAt", goal.getCreatedAt());
        out.put("updatedAt", goal.getUpdatedAt());
        return out;
    }

    private Map<String, Object> shortcutMap(V2KeyboardShortcut shortcut) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", shortcut.getId());
        out.put("userId", shortcut.getUser().getId());
        out.put("action", shortcut.getAction());
        out.put("shortcut", shortcut.getShortcut());
        out.put("isCustom", shortcut.isCustom());
        out.put("createdAt", shortcut.getCreatedAt());
        return out;
    }

    private void syncGoalProgress(User user, UUID storyId, int deltaNetWords) {
        if (deltaNetWords == 0) return;
        for (V2WritingGoal goal : goalRepository.findByUserId(user.getId())) {
            if (!"active".equalsIgnoreCase(goal.getStatus())) continue;
            if (goal.getStory() != null && !Objects.equals(goal.getStory().getId(), storyId)) continue;
            if (!goal.getGoalType().contains("words")) continue;
            goal.setCurrentValue(Math.max(0, goal.getCurrentValue() + deltaNetWords));
            if (goal.getCurrentValue() >= Math.max(1, goal.getTargetValue())) {
                goal.setStatus("completed");
            }
            goalRepository.save(goal);
        }
    }

    private int currentDurationSeconds(V2WritingSession session) {
        Instant end = session.getEndedAt() == null ? now() : session.getEndedAt();
        return (int) Duration.between(session.getStartedAt(), end).toSeconds();
    }

    private Map<String, String> defaultShortcuts() {
        return Map.ofEntries(
                Map.entry("save", "Ctrl+S"),
                Map.entry("focus_mode", "Ctrl+Shift+F"),
                Map.entry("command_palette", "Ctrl+K"),
                Map.entry("toggle_left_panel", "Ctrl+B"),
                Map.entry("toggle_right_panel", "Ctrl+Shift+B"),
                Map.entry("next_chapter", "Ctrl+]"),
                Map.entry("prev_chapter", "Ctrl+["),
                Map.entry("ai_refine", "Ctrl+Shift+R"),
                Map.entry("new_scene", "Ctrl+Shift+N"),
                Map.entry("search_manuscript", "Ctrl+F"),
                Map.entry("search_replace", "Ctrl+H"),
                Map.entry("export", "Ctrl+Shift+E")
        );
    }

    private Instant now() {
        return timeProvider.nowInstant();
    }

    private static String str(Object value, String fallback) {
        if (value == null) return fallback;
        String text = value.toString().trim();
        return text.isBlank() ? fallback : text;
    }

    private static int intVal(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        try { return value == null ? fallback : Integer.parseInt(value.toString()); } catch (Exception ex) { return fallback; }
    }

    private static boolean boolVal(Object value, boolean fallback) {
        return value instanceof Boolean b ? b : value == null ? fallback : Boolean.parseBoolean(value.toString());
    }

    private static LocalDate date(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        return LocalDate.parse(value.toString());
    }
}
