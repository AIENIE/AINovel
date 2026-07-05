package com.ainovel.app.v2;

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
import java.util.*;

@Tag(name = "V2", description = "AINovel v2 and quality APIs")
@RestController
@RequestMapping("/v2")
public class V2ContextController {
    private final ResourceAccessGuard accessGuard;
    private final V2ContextPersistenceService persistenceService;

    @Autowired
    public V2ContextController(ResourceAccessGuard accessGuard, V2ContextPersistenceService persistenceService) {
        this.accessGuard = accessGuard;
        this.persistenceService = persistenceService;
    }

    @Operation(summary = "v2 API endpoint")

    @GetMapping("/stories/{storyId}/lorebook")
    public List<Map<String, Object>> listLorebook(@AuthenticationPrincipal UserDetails principal,
                                                  @PathVariable UUID storyId) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);
        return listLorebookInternal(storyId);
    }

    @Operation(summary = "v2 API endpoint")

    @PostMapping("/stories/{storyId}/lorebook")
    public Map<String, Object> createLorebook(@AuthenticationPrincipal UserDetails principal,
                                              @PathVariable UUID storyId,
                                              @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        Story story = accessGuard.requireOwnedStory(storyId, user);
        return persistenceService.createLorebook(user, story, payload);
    }

    @Operation(summary = "v2 API endpoint")

    @PutMapping("/stories/{storyId}/lorebook/{entryId}")
    public Map<String, Object> updateLorebook(@AuthenticationPrincipal UserDetails principal,
                                              @PathVariable UUID storyId,
                                              @PathVariable UUID entryId,
                                              @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);
        return persistenceService.updateLorebook(storyId, entryId, payload);
    }

    @Operation(summary = "v2 API endpoint")

    @DeleteMapping("/stories/{storyId}/lorebook/{entryId}")
    public ResponseEntity<Void> deleteLorebook(@AuthenticationPrincipal UserDetails principal,
                                               @PathVariable UUID storyId,
                                               @PathVariable UUID entryId) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);
        persistenceService.deleteLorebook(storyId, entryId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "v2 API endpoint")

    @PostMapping("/stories/{storyId}/lorebook/import")
    public Map<String, Object> importLorebook(@AuthenticationPrincipal UserDetails principal,
                                              @PathVariable UUID storyId,
                                              @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        Story story = accessGuard.requireOwnedStory(storyId, user);
        List<Object> entries = list(payload.get("entries"));
        int imported = 0;
        for (Object item : entries) {
            if (!(item instanceof Map<?, ?> itemMap)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> source = new HashMap<>((Map<String, Object>) itemMap);
            persistenceService.createLorebook(user, story, source);
            imported++;
        }
        return Map.of(
                "storyId", storyId,
                "imported", imported,
                "total", entries.size()
        );
    }

    @Operation(summary = "v2 API endpoint")

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
        Set<UUID> enabledNodeIds = new HashSet<>();
        for (Map<String, Object> node : nodes) {
            UUID id = uuidVal(node.get("id"), null);
            if (id != null) {
                enabledNodeIds.add(id);
            }
        }
        List<Map<String, Object>> edges = new ArrayList<>();
        for (Map<String, Object> relation : listRelationshipsInternal(storyId)) {
            UUID source = uuidVal(relation.get("source"), null);
            UUID target = uuidVal(relation.get("target"), null);
            if (source == null || target == null) {
                continue;
            }
            if (!enabledNodeIds.contains(source) || !enabledNodeIds.contains(target)) {
                continue;
            }
            edges.add(relation);
        }
        return Map.of(
                "storyId", storyId,
                "nodes", nodes,
                "edges", edges,
                "generatedAt", Instant.now()
        );
    }

    @Operation(summary = "v2 API endpoint")

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
        Set<UUID> nodeIds = new HashSet<>();
        for (Map<String, Object> node : nodes) {
            UUID id = uuidVal(node.get("id"), null);
            if (id != null) {
                nodeIds.add(id);
            }
        }
        List<Map<String, Object>> edges = new ArrayList<>();
        for (Map<String, Object> relation : listRelationshipsInternal(storyId)) {
            UUID source = uuidVal(relation.get("source"), null);
            UUID target = uuidVal(relation.get("target"), null);
            if (source == null || target == null) {
                continue;
            }
            if (nodeIds.contains(source) || nodeIds.contains(target)) {
                edges.add(relation);
            }
        }
        return Map.of(
                "storyId", storyId,
                "keyword", term,
                "nodes", nodes,
                "edges", edges
        );
    }

    @Operation(summary = "v2 API endpoint")

    @PostMapping("/stories/{storyId}/graph/relationships")
    public Map<String, Object> createRelationship(@AuthenticationPrincipal UserDetails principal,
                                                  @PathVariable UUID storyId,
                                                  @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        Story story = accessGuard.requireOwnedStory(storyId, user);

        UUID sourceId = uuidVal(payload.get("source"), null);
        UUID targetId = uuidVal(payload.get("target"), null);
        if (sourceId == null || targetId == null) {
            throw new RuntimeException("source/target 不能为空");
        }
        if (sourceId.equals(targetId)) {
            throw new RuntimeException("source/target 不能相同");
        }
        return persistenceService.createRelationship(story, sourceId, targetId, str(payload.get("relationType"), "related_to"));
    }

    @Operation(summary = "v2 API endpoint")

    @DeleteMapping("/stories/{storyId}/graph/relationships/{relationshipId}")
    public ResponseEntity<Void> deleteRelationship(@AuthenticationPrincipal UserDetails principal,
                                                   @PathVariable UUID storyId,
                                                   @PathVariable UUID relationshipId) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);
        persistenceService.deleteRelationship(storyId, relationshipId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "v2 API endpoint")

    @PostMapping("/stories/{storyId}/graph/sync")
    public Map<String, Object> syncGraph(@AuthenticationPrincipal UserDetails principal,
                                         @PathVariable UUID storyId) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);
        return Map.of(
                "storyId", storyId,
                "status", "completed",
                "syncedAt", Instant.now(),
                "nodes", listLorebookInternal(storyId).size(),
                "edges", listRelationshipsInternal(storyId).size()
        );
    }

    @Operation(summary = "v2 API endpoint")

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
        return persistenceService.createExtraction(story, payload);
    }

    @Operation(summary = "v2 API endpoint")

    @GetMapping("/stories/{storyId}/extractions")
    public List<Map<String, Object>> listExtractions(@AuthenticationPrincipal UserDetails principal,
                                                     @PathVariable UUID storyId) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);
        return persistenceService.listExtractions(storyId);
    }

    @Operation(summary = "v2 API endpoint")

    @PutMapping("/stories/{storyId}/extractions/{id}/review")
    public Map<String, Object> reviewExtraction(@AuthenticationPrincipal UserDetails principal,
                                                @PathVariable UUID storyId,
                                                @PathVariable UUID id,
                                                @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);
        return persistenceService.reviewExtraction(storyId, id, payload);
    }

    @Operation(summary = "v2 API endpoint")

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
        List<Map<String, Object>> extractions = persistenceService.listExtractions(storyId);
        for (Map<String, Object> extraction : extractions) {
            if (!boolVal(extraction.get("reviewed"), false)) {
                pendingExtractions.add(extraction);
            }
        }

        List<Map<String, Object>> systemPromptEntries = new ArrayList<>();
        List<Map<String, Object>> beforeSceneEntries = new ArrayList<>();
        List<Map<String, Object>> afterSceneEntries = new ArrayList<>();
        for (Map<String, Object> entry : selected) {
            String insertionPosition = str(entry.get("insertionPosition"), "before_scene");
            if ("system_prompt".equals(insertionPosition)) {
                systemPromptEntries.add(entry);
            } else if ("after_scene".equals(insertionPosition)) {
                afterSceneEntries.add(entry);
            } else {
                beforeSceneEntries.add(entry);
            }
        }

        Set<UUID> selectedIds = new HashSet<>();
        Map<UUID, String> nameById = new HashMap<>();
        for (Map<String, Object> entry : selected) {
            UUID id = uuidVal(entry.get("id"), null);
            if (id != null) {
                selectedIds.add(id);
                nameById.put(id, str(entry.get("displayName"), id.toString().substring(0, 8)));
            }
        }
        List<String> graphRelations = new ArrayList<>();
        for (Map<String, Object> relation : listRelationshipsInternal(storyId)) {
            UUID source = uuidVal(relation.get("source"), null);
            UUID target = uuidVal(relation.get("target"), null);
            if (source == null || target == null) {
                continue;
            }
            if (!selectedIds.contains(source) || !selectedIds.contains(target)) {
                continue;
            }
            String relationType = str(relation.get("relationType"), "related_to");
            graphRelations.add(nameById.getOrDefault(source, source.toString().substring(0, 8)) +
                    " --" + relationType + "--> " +
                    nameById.getOrDefault(target, target.toString().substring(0, 8)));
        }

        List<String> activeCharacters = new ArrayList<>();
        for (Map<String, Object> entry : selected) {
            if (!"character".equalsIgnoreCase(str(entry.get("category"), "custom"))) {
                continue;
            }
            activeCharacters.add(str(entry.get("displayName"), "角色"));
            if (activeCharacters.size() >= 5) {
                break;
            }
        }
        String recentSummary = chapterIndex <= 1
                ? "当前为开篇场景，暂无前情摘要。"
                : "第" + (chapterIndex - 1) + "章回顾：关键人物关系已更新，当前场景建议重点承接上一章冲突。";

        Map<String, Object> response = new HashMap<>();
        response.put("storyId", storyId);
        response.put("chapterIndex", chapterIndex);
        response.put("sceneIndex", sceneIndex);
        response.put("tokenBudget", tokenBudget);
        response.put("tokenUsed", usedBudget);
        response.put("lorebookEntries", selected);
        response.put("systemPromptEntries", systemPromptEntries);
        response.put("beforeSceneEntries", beforeSceneEntries);
        response.put("afterSceneEntries", afterSceneEntries);
        response.put("graphRelations", graphRelations);
        response.put("recentSummary", recentSummary);
        response.put("activeCharacters", activeCharacters);
        response.put("pendingExtractions", pendingExtractions);
        response.put("generatedAt", Instant.now());
        return response;
    }

    private List<Map<String, Object>> listLorebookInternal(UUID storyId) {
        List<Map<String, Object>> entries = new ArrayList<>(persistenceService.listLorebook(storyId));
        entries.sort(Comparator.comparingInt((Map<String, Object> entry) -> intVal(entry.get("priority"), 0)).reversed());
        return entries;
    }

    private List<Map<String, Object>> listRelationshipsInternal(UUID storyId) {
        return new ArrayList<>(persistenceService.listRelationships(storyId));
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

    private UUID uuidVal(Object value, UUID fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof UUID id) {
            return id;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException ex) {
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
