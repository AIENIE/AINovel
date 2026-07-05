package com.ainovel.app.v2;

import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class V2ContextControllerTests {

    private ResourceAccessGuard accessGuard;
    private V2ContextPersistenceService persistenceService;
    private V2ContextController controller;
    private UserDetails principal;
    private User user;
    private UUID storyId;

    @BeforeEach
    void setUp() {
        accessGuard = mock(ResourceAccessGuard.class);
        persistenceService = mock(V2ContextPersistenceService.class);
        controller = new V2ContextController(accessGuard, persistenceService);

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
    void previewShouldRespectTokenBudget() {
        when(persistenceService.listLorebook(storyId)).thenReturn(List.of(
                lorebookEntry("角色设定", "character", 200, 10, "before_scene"),
                lorebookEntry("地点设定", "location", 180, 8, "before_scene")
        ));
        when(persistenceService.listExtractions(storyId)).thenReturn(List.of());
        when(persistenceService.listRelationships(storyId)).thenReturn(List.of());

        Map<String, Object> preview = controller.previewContext(principal, storyId, 1, 1, 250);
        int tokenUsed = ((Number) preview.get("tokenUsed")).intValue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> selected = (List<Map<String, Object>>) preview.get("lorebookEntries");

        assertTrue(tokenUsed <= 250);
        assertEquals(1, selected.size(), "预算不足时应仅注入一个条目");
        verify(persistenceService).listLorebook(storyId);
        verify(persistenceService).listExtractions(storyId);
        verify(persistenceService).listRelationships(storyId);
    }

    @Test
    void reviewExtractionShouldMarkReviewed() {
        UUID extractionId = UUID.randomUUID();
        Map<String, Object> reviewedPayload = new HashMap<>();
        reviewedPayload.put("id", extractionId);
        reviewedPayload.put("reviewed", true);
        reviewedPayload.put("reviewAction", "approved");
        when(persistenceService.reviewExtraction(storyId, extractionId, Map.of(
                "reviewAction", "approved"
        ))).thenReturn(reviewedPayload);

        Map<String, Object> reviewed = controller.reviewExtraction(principal, storyId, extractionId, Map.of(
                "reviewAction", "approved"
        ));

        assertEquals(true, reviewed.get("reviewed"));
        assertEquals("approved", reviewed.get("reviewAction"));
        verify(persistenceService).reviewExtraction(storyId, extractionId, Map.of("reviewAction", "approved"));
    }

    @Test
    void graphShouldContainPersistedRelationshipBetweenEnabledNodes() {
        UUID nodeAId = UUID.randomUUID();
        UUID nodeBId = UUID.randomUUID();
        UUID disabledNodeId = UUID.randomUUID();
        when(persistenceService.listLorebook(storyId)).thenReturn(List.of(
                lorebookEntry(nodeAId, "林烬", "character", 80, 10, "before_scene", true),
                lorebookEntry(nodeBId, "夜雨港", "location", 80, 8, "before_scene", true),
                lorebookEntry(disabledNodeId, "废弃设定", "concept", 40, 1, "before_scene", false)
        ));
        when(persistenceService.listRelationships(storyId)).thenReturn(List.of(
                relationship(nodeAId, nodeBId, "located_at"),
                relationship(nodeAId, disabledNodeId, "ignored")
        ));

        Map<String, Object> graph = controller.graph(principal, storyId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> edges = (List<Map<String, Object>>) graph.get("edges");
        assertEquals(1, edges.size());
        assertEquals("located_at", edges.get(0).get("relationType"));
        verify(persistenceService).listLorebook(storyId);
        verify(persistenceService).listRelationships(storyId);
    }

    @Test
    void previewShouldExposeSegmentedContextSections() {
        UUID characterId = UUID.randomUUID();
        UUID systemRuleId = UUID.randomUUID();
        UUID afterHintId = UUID.randomUUID();
        when(persistenceService.listLorebook(storyId)).thenReturn(List.of(
                lorebookEntry(characterId, "林烬", "character", 80, 10, "before_scene", true),
                lorebookEntry(systemRuleId, "世界规则", "concept", 60, 9, "system_prompt", true),
                lorebookEntry(afterHintId, "收束提示", "event", 50, 8, "after_scene", true)
        ));
        when(persistenceService.listRelationships(storyId)).thenReturn(List.of(
                relationship(characterId, systemRuleId, "knows")
        ));
        when(persistenceService.listExtractions(storyId)).thenReturn(List.of(
                extraction(false, "pending"),
                extraction(true, "approved")
        ));

        Map<String, Object> preview = controller.previewContext(principal, storyId, 3, 1, 300);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> systemPromptEntries = (List<Map<String, Object>>) preview.get("systemPromptEntries");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> beforeSceneEntries = (List<Map<String, Object>>) preview.get("beforeSceneEntries");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> afterSceneEntries = (List<Map<String, Object>>) preview.get("afterSceneEntries");
        @SuppressWarnings("unchecked")
        List<String> graphRelations = (List<String>) preview.get("graphRelations");
        @SuppressWarnings("unchecked")
        List<String> activeCharacters = (List<String>) preview.get("activeCharacters");

        assertEquals(1, systemPromptEntries.size());
        assertEquals(1, beforeSceneEntries.size());
        assertEquals(1, afterSceneEntries.size());
        assertFalse(graphRelations.isEmpty());
        assertTrue(activeCharacters.contains("林烬"));
        assertNotNull(preview.get("recentSummary"));
        assertEquals(1, ((List<?>) preview.get("pendingExtractions")).size());
        verify(persistenceService).listExtractions(storyId);
    }

    @Test
    void importLorebookShouldDelegateEachEntryToPersistenceService() {
        controller.importLorebook(principal, storyId, Map.of(
                "entries", List.of(
                        Map.of("displayName", "角色设定", "category", "character"),
                        Map.of("displayName", "地点设定", "category", "location"),
                        "ignored"
                )
        ));

        verify(persistenceService).createLorebook(eq(user), any(Story.class), eq(new HashMap<>(Map.of(
                "displayName", "角色设定",
                "category", "character"
        ))));
        verify(persistenceService).createLorebook(eq(user), any(Story.class), eq(new HashMap<>(Map.of(
                "displayName", "地点设定",
                "category", "location"
        ))));
    }

    private Map<String, Object> lorebookEntry(String displayName, String category, int tokenBudget, int priority, String insertionPosition) {
        return lorebookEntry(UUID.randomUUID(), displayName, category, tokenBudget, priority, insertionPosition, true);
    }

    private Map<String, Object> lorebookEntry(UUID id,
                                              String displayName,
                                              String category,
                                              int tokenBudget,
                                              int priority,
                                              String insertionPosition,
                                              boolean enabled) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("id", id);
        entry.put("displayName", displayName);
        entry.put("category", category);
        entry.put("tokenBudget", tokenBudget);
        entry.put("priority", priority);
        entry.put("insertionPosition", insertionPosition);
        entry.put("enabled", enabled);
        entry.put("content", displayName + " 内容");
        return entry;
    }

    private Map<String, Object> relationship(UUID source, UUID target, String relationType) {
        Map<String, Object> relation = new HashMap<>();
        relation.put("id", UUID.randomUUID());
        relation.put("source", source);
        relation.put("target", target);
        relation.put("relationType", relationType);
        return relation;
    }

    private Map<String, Object> extraction(boolean reviewed, String reviewAction) {
        Map<String, Object> extraction = new HashMap<>();
        extraction.put("id", UUID.randomUUID());
        extraction.put("reviewed", reviewed);
        extraction.put("reviewAction", reviewAction);
        extraction.put("createdAt", Instant.now());
        return extraction;
    }
}
