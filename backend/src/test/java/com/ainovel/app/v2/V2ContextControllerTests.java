package com.ainovel.app.v2;

import com.ainovel.app.story.model.Story;
import com.ainovel.app.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class V2ContextControllerTests {

    private V2AccessGuard accessGuard;
    private V2ContextController controller;
    private UserDetails principal;
    private User user;
    private UUID storyId;

    @BeforeEach
    void setUp() {
        accessGuard = mock(V2AccessGuard.class);
        controller = new V2ContextController(accessGuard);

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
        controller.createLorebook(principal, storyId, Map.of(
                "displayName", "角色设定",
                "content", "内容A",
                "tokenBudget", 200,
                "priority", 10
        ));
        controller.createLorebook(principal, storyId, Map.of(
                "displayName", "地点设定",
                "content", "内容B",
                "tokenBudget", 180,
                "priority", 8
        ));

        Map<String, Object> preview = controller.previewContext(principal, storyId, 1, 1, 250);
        int tokenUsed = ((Number) preview.get("tokenUsed")).intValue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> selected = (List<Map<String, Object>>) preview.get("lorebookEntries");

        assertTrue(tokenUsed <= 250);
        assertEquals(1, selected.size(), "预算不足时应仅注入一个条目");
    }

    @Test
    void reviewExtractionShouldMarkReviewed() {
        Map<String, Object> extraction = controller.extractEntities(principal, storyId, Map.of(
                "text", "林逸在青云山发现了古老石碑"
        ));

        UUID extractionId = (UUID) extraction.get("id");
        Map<String, Object> reviewed = controller.reviewExtraction(principal, storyId, extractionId, Map.of(
                "reviewAction", "approved"
        ));

        assertEquals(true, reviewed.get("reviewed"));
        assertEquals("approved", reviewed.get("reviewAction"));
    }

    @Test
    void graphShouldContainRelationshipAndCascadeOnNodeDelete() {
        Map<String, Object> nodeA = controller.createLorebook(principal, storyId, Map.of(
                "displayName", "林烬",
                "category", "character",
                "tokenBudget", 80
        ));
        Map<String, Object> nodeB = controller.createLorebook(principal, storyId, Map.of(
                "displayName", "夜雨港",
                "category", "location",
                "tokenBudget", 80
        ));

        UUID nodeAId = (UUID) nodeA.get("id");
        UUID nodeBId = (UUID) nodeB.get("id");
        controller.createRelationship(principal, storyId, Map.of(
                "source", nodeAId.toString(),
                "target", nodeBId.toString(),
                "relationType", "located_at"
        ));

        Map<String, Object> graph = controller.graph(principal, storyId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> edges = (List<Map<String, Object>>) graph.get("edges");
        assertEquals(1, edges.size());

        controller.deleteLorebook(principal, storyId, nodeAId);
        Map<String, Object> graphAfterDelete = controller.graph(principal, storyId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> edgesAfterDelete = (List<Map<String, Object>>) graphAfterDelete.get("edges");
        assertEquals(0, edgesAfterDelete.size());
    }

    @Test
    void previewShouldExposeSegmentedContextSections() {
        Map<String, Object> character = controller.createLorebook(principal, storyId, Map.of(
                "displayName", "林烬",
                "category", "character",
                "tokenBudget", 80,
                "insertionPosition", "before_scene"
        ));
        Map<String, Object> systemRule = controller.createLorebook(principal, storyId, Map.of(
                "displayName", "世界规则",
                "category", "concept",
                "tokenBudget", 60,
                "insertionPosition", "system_prompt"
        ));
        Map<String, Object> afterHint = controller.createLorebook(principal, storyId, Map.of(
                "displayName", "收束提示",
                "category", "event",
                "tokenBudget", 50,
                "insertionPosition", "after_scene"
        ));

        controller.createRelationship(principal, storyId, Map.of(
                "source", character.get("id").toString(),
                "target", systemRule.get("id").toString(),
                "relationType", "knows"
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
    }
}
