package com.ainovel.app.v2;

import com.ainovel.app.story.model.Story;
import com.ainovel.app.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@RestController
@RequestMapping("/v2")
public class V2ContextController {
    private final V2AccessGuard accessGuard;

    private final ConcurrentMap<UUID, ConcurrentMap<UUID, Map<String, Object>>> lorebookByStory = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ConcurrentMap<UUID, Map<String, Object>>> extractionByStory = new ConcurrentHashMap<>();

    public V2ContextController(V2AccessGuard accessGuard) {
        this.accessGuard = accessGuard;
    }

    @GetMapping("/stories/{storyId}/lorebook")
    public List<Map<String, Object>> listLorebook(@AuthenticationPrincipal UserDetails principal,
                                                  @PathVariable UUID storyId) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);
        return listLorebookInternal(storyId);
    }

    @PostMapping("/stories/{storyId}/lorebook")
    public Map<String, Object> createLorebook(@AuthenticationPrincipal UserDetails principal,
                                              @PathVariable UUID storyId,
                                              @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);

        Map<String, Object> entry = new HashMap<>();
        UUID id = UUID.randomUUID();
        entry.put("id", id);
        entry.put("storyId", storyId);
        entry.put("userId", user.getId());
        entry.put("entryKey", str(payload.get("entryKey"), "entry-" + id.toString().substring(0, 8)));
        entry.put("displayName", str(payload.get("displayName"), "未命名条目"));
        entry.put("category", str(payload.get("category"), "custom"));
        entry.put("content", str(payload.get("content"), ""));
        entry.put("keywords", list(payload.get("keywords")));
        entry.put("priority", intVal(payload.get("priority"), 0));
        entry.put("enabled", boolVal(payload.get("enabled"), true));
        entry.put("insertionPosition", str(payload.get("insertionPosition"), "before_scene"));
        entry.put("tokenBudget", intVal(payload.get("tokenBudget"), 500));
        entry.put("createdAt", Instant.now());
        entry.put("updatedAt", Instant.now());

        lorebookByStory.computeIfAbsent(storyId, key -> new ConcurrentHashMap<>()).put(id, entry);
        return entry;
    }

    @PutMapping("/stories/{storyId}/lorebook/{entryId}")
    public Map<String, Object> updateLorebook(@AuthenticationPrincipal UserDetails principal,
                                              @PathVariable UUID storyId,
                                              @PathVariable UUID entryId,
                                              @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);

        Map<String, Object> entry = requireLorebook(storyId, entryId);
        mergeIfPresent(payload, entry, "entryKey", "displayName", "category", "content", "insertionPosition");
        if (payload.containsKey("keywords")) {
            entry.put("keywords", list(payload.get("keywords")));
        }
        if (payload.containsKey("priority")) {
            entry.put("priority", intVal(payload.get("priority"), 0));
        }
        if (payload.containsKey("enabled")) {
            entry.put("enabled", boolVal(payload.get("enabled"), true));
        }
        if (payload.containsKey("tokenBudget")) {
            entry.put("tokenBudget", intVal(payload.get("tokenBudget"), 500));
        }
        entry.put("updatedAt", Instant.now());
        return entry;
    }

    @DeleteMapping("/stories/{storyId}/lorebook/{entryId}")
    public ResponseEntity<Void> deleteLorebook(@AuthenticationPrincipal UserDetails principal,
                                               @PathVariable UUID storyId,
                                               @PathVariable UUID entryId) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);
        lorebookByStory.computeIfAbsent(storyId, key -> new ConcurrentHashMap<>()).remove(entryId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/stories/{storyId}/lorebook/import")
    public Map<String, Object> importLorebook(@AuthenticationPrincipal UserDetails principal,
                                              @PathVariable UUID storyId,
                                              @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);
        List<Object> entries = list(payload.get("entries"));
        int imported = 0;
        for (Object item : entries) {
            if (!(item instanceof Map<?, ?> itemMap)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> source = new HashMap<>((Map<String, Object>) itemMap);
            createLorebook(principal, storyId, source);
            imported++;
        }
        return Map.of(
                "storyId", storyId,
                "imported", imported,
                "total", entries.size()
        );
    }

    @GetMapping("/stories/{storyId}/graph")
    public Map<String, Object> graph(@AuthenticationPrincipal UserDetails principal,
                                     @PathVariable UUID storyId) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);
        List<Map<String, Object>> entries = listLorebookInternal(storyId);
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Map<String, Object> entry : entries) {
            if (!boolVal(entry.get("enabled"), true)) {
                continue;
            }
            nodes.add(Map.of(
                    "id", entry.get("id"),
                    "label", str(entry.get("displayName"), ""),
                    "type", str(entry.get("category"), "custom")
            ));
        }
        return Map.of(
                "storyId", storyId,
                "nodes", nodes,
                "edges", List.of(),
                "generatedAt", Instant.now()
        );
    }

    @GetMapping("/stories/{storyId}/graph/query")
    public Map<String, Object> queryGraph(@AuthenticationPrincipal UserDetails principal,
                                          @PathVariable UUID storyId,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false, defaultValue = "20") int limit) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);
        String term = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Map<String, Object> entry : listLorebookInternal(storyId)) {
            String displayName = str(entry.get("displayName"), "").toLowerCase(Locale.ROOT);
            String content = str(entry.get("content"), "").toLowerCase(Locale.ROOT);
            if (term.isEmpty() || displayName.contains(term) || content.contains(term)) {
                nodes.add(Map.of(
                        "id", entry.get("id"),
                        "label", entry.get("displayName"),
                        "type", entry.get("category")
                ));
            }
            if (nodes.size() >= Math.max(limit, 1)) {
                break;
            }
        }
        return Map.of(
                "storyId", storyId,
                "keyword", term,
                "nodes", nodes,
                "edges", List.of()
        );
    }

    @PostMapping("/stories/{storyId}/graph/sync")
    public Map<String, Object> syncGraph(@AuthenticationPrincipal UserDetails principal,
                                         @PathVariable UUID storyId) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);
        return Map.of(
                "storyId", storyId,
                "status", "completed",
                "syncedAt", Instant.now(),
                "nodes", listLorebookInternal(storyId).size()
        );
    }

    @PostMapping("/stories/{storyId}/extract-entities")
    public Map<String, Object> extractEntities(@AuthenticationPrincipal UserDetails principal,
                                               @PathVariable UUID storyId,
                                               @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        Story story = accessGuard.requireOwnedStory(storyId, user);
        String text = str(payload.get("text"), "").trim();
        if (text.isEmpty()) {
            throw new RuntimeException("text 不能为空");
        }

        UUID id = UUID.randomUUID();
        Map<String, Object> extraction = new HashMap<>();
        extraction.put("id", id);
        extraction.put("storyId", storyId);
        extraction.put("manuscriptId", payload.get("manuscriptId"));
        extraction.put("entityName", str(payload.get("entityName"), text.substring(0, Math.min(12, text.length()))));
        extraction.put("entityType", str(payload.get("entityType"), "concept"));
        extraction.put("attributes", payload.getOrDefault("attributes", Map.of("source", "ai")));
        extraction.put("sourceText", text.substring(0, Math.min(text.length(), 200)));
        extraction.put("confidence", doubleVal(payload.get("confidence"), 0.82d));
        extraction.put("reviewed", false);
        extraction.put("reviewAction", "pending");
        extraction.put("linkedLorebookId", null);
        extraction.put("createdAt", Instant.now());

        extractionByStory.computeIfAbsent(story.getId(), key -> new ConcurrentHashMap<>()).put(id, extraction);
        return extraction;
    }

    @GetMapping("/stories/{storyId}/extractions")
    public List<Map<String, Object>> listExtractions(@AuthenticationPrincipal UserDetails principal,
                                                     @PathVariable UUID storyId) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);
        return new ArrayList<>(extractionByStory.computeIfAbsent(storyId, key -> new ConcurrentHashMap<>()).values());
    }

    @PutMapping("/stories/{storyId}/extractions/{id}/review")
    public Map<String, Object> reviewExtraction(@AuthenticationPrincipal UserDetails principal,
                                                @PathVariable UUID storyId,
                                                @PathVariable UUID id,
                                                @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);

        Map<String, Object> extraction = extractionByStory
                .computeIfAbsent(storyId, key -> new ConcurrentHashMap<>())
                .get(id);
        if (extraction == null) {
            throw new RuntimeException("提取记录不存在");
        }

        String action = str(payload.get("reviewAction"), "approved");
        extraction.put("reviewed", true);
        extraction.put("reviewAction", action);
        if (payload.containsKey("linkedLorebookId")) {
            extraction.put("linkedLorebookId", payload.get("linkedLorebookId"));
        }
        extraction.put("reviewedAt", Instant.now());
        return extraction;
    }

    @GetMapping("/stories/{storyId}/context/preview")
    public Map<String, Object> previewContext(@AuthenticationPrincipal UserDetails principal,
                                              @PathVariable UUID storyId,
                                              @RequestParam(required = false, defaultValue = "1") int chapterIndex,
                                              @RequestParam(required = false, defaultValue = "1") int sceneIndex,
                                              @RequestParam(required = false, defaultValue = "3500") int tokenBudget) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);

        List<Map<String, Object>> enabled = listLorebookInternal(storyId).stream()
                .filter(entry -> boolVal(entry.get("enabled"), true))
                .toList();

        int usedBudget = 0;
        List<Map<String, Object>> selected = new ArrayList<>();
        for (Map<String, Object> entry : enabled) {
            int entryBudget = intVal(entry.get("tokenBudget"), 500);
            if (usedBudget + entryBudget > tokenBudget) {
                continue;
            }
            usedBudget += entryBudget;
            selected.add(entry);
        }

        List<Map<String, Object>> pendingExtractions = new ArrayList<>();
        for (Map<String, Object> extraction : extractionByStory.computeIfAbsent(storyId, key -> new ConcurrentHashMap<>()).values()) {
            if (!boolVal(extraction.get("reviewed"), false)) {
                pendingExtractions.add(extraction);
            }
        }

        return Map.of(
                "storyId", storyId,
                "chapterIndex", chapterIndex,
                "sceneIndex", sceneIndex,
                "tokenBudget", tokenBudget,
                "tokenUsed", usedBudget,
                "lorebookEntries", selected,
                "pendingExtractions", pendingExtractions,
                "generatedAt", Instant.now()
        );
    }

    private Map<String, Object> requireLorebook(UUID storyId, UUID entryId) {
        Map<String, Object> entry = lorebookByStory.computeIfAbsent(storyId, key -> new ConcurrentHashMap<>()).get(entryId);
        if (entry == null) {
            throw new RuntimeException("Lorebook 条目不存在");
        }
        return entry;
    }

    private List<Map<String, Object>> listLorebookInternal(UUID storyId) {
        List<Map<String, Object>> entries = new ArrayList<>(lorebookByStory.computeIfAbsent(storyId, key -> new ConcurrentHashMap<>()).values());
        entries.sort(Comparator.comparingInt((Map<String, Object> entry) -> intVal(entry.get("priority"), 0)).reversed());
        return entries;
    }

    private void mergeIfPresent(Map<String, Object> source, Map<String, Object> target, String... keys) {
        for (String key : keys) {
            if (source.containsKey(key)) {
                target.put(key, source.get(key));
            }
        }
    }

    private String str(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? fallback : text;
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

    private double doubleVal(Object value, double fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException ex) {
            return fallback;
        }
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

    private List<Object> list(Object value) {
        if (value instanceof List<?> raw) {
            return new ArrayList<>(raw);
        }
        return new ArrayList<>();
    }
}
