package com.ainovel.app.material;

import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.material.model.Material;
import com.ainovel.app.material.repo.MaterialRepository;
import com.ainovel.app.material.repo.MaterialUploadJobRepository;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.story.model.Outline;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MaterialServiceClosureTest {
    private MaterialRepository materialRepository;
    private ManuscriptRepository manuscriptRepository;
    private ResourceAccessGuard accessGuard;
    private MaterialService service;
    private User user;

    @BeforeEach
    void setUp() {
        materialRepository = mock(MaterialRepository.class);
        manuscriptRepository = mock(ManuscriptRepository.class);
        accessGuard = mock(ResourceAccessGuard.class);
        service = new MaterialService(
                materialRepository,
                mock(MaterialUploadJobRepository.class),
                accessGuard,
                mock(MaterialRetrievalService.class),
                manuscriptRepository,
                new ObjectMapper()
        );

        user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("material_owner");
    }

    @Test
    void findDuplicatesShouldReturnScoredCandidatePairs() {
        when(accessGuard.isCurrentUserAdmin()).thenReturn(true);
        Material source = material("m1", "陆家码头旧报", "码头旧案 雨夜停船", "[\"码头\",\"雨夜\"]");
        Material target = material("m2", "陆家码头档案", "雨夜码头停船记录", "[\"码头\",\"档案\"]");
        Material unrelated = material("m3", "王都礼仪", "贵族宴会礼仪", "[\"礼仪\"]");
        when(materialRepository.findAll()).thenReturn(List.of(source, target, unrelated));

        List<Map<String, Object>> duplicates = service.findDuplicates();

        assertEquals(1, duplicates.size());
        Map<String, Object> duplicate = duplicates.getFirst();
        assertEquals(source.getId(), duplicate.get("sourceMaterialId"));
        assertEquals(target.getId(), duplicate.get("targetMaterialId"));
        assertTrue(((Number) duplicate.get("score")).doubleValue() > 0.5);
        assertFalse(((List<?>) duplicate.get("reasons")).isEmpty());
    }

    @Test
    void citationsShouldFindOwnedManuscriptSectionsContainingMaterialSignals() {
        Material material = material("m1", "陆家码头旧报", "陆家码头在雨夜停用", "[\"陆家码头\",\"雨夜\"]");
        when(materialRepository.findById(material.getId())).thenReturn(Optional.of(material));
        Story story = new Story();
        story.setId(UUID.randomUUID());
        story.setUser(user);
        story.setTitle("雨港迷案");
        Outline outline = new Outline();
        outline.setStory(story);
        UUID sceneId = UUID.randomUUID();
        outline.setContentJson("""
                {"chapters":[{"title":"第一章","scenes":[{"id":"%s","title":"旧码头","order":1}]}]}
                """.formatted(sceneId));
        Manuscript manuscript = new Manuscript();
        manuscript.setId(UUID.randomUUID());
        manuscript.setOutline(outline);
        manuscript.setSectionsJson("{\"" + sceneId + "\":\"<p>调查员在陆家码头的雨夜发现旧报。</p>\"}");
        when(manuscriptRepository.findByStoryUser(user)).thenReturn(List.of(manuscript));

        List<Map<String, Object>> citations = service.citations(material.getId());

        assertEquals(1, citations.size());
        Map<String, Object> citation = citations.getFirst();
        assertEquals(material.getId(), citation.get("materialId"));
        assertEquals(story.getId(), citation.get("storyId"));
        assertEquals(manuscript.getId(), citation.get("manuscriptId"));
        assertEquals(sceneId, citation.get("sceneId"));
        assertEquals("第一章", citation.get("chapterTitle"));
        assertEquals("旧码头", citation.get("sceneTitle"));
        assertTrue(String.valueOf(citation.get("snippet")).contains("陆家码头"));
    }

    private Material material(String suffix, String title, String content, String tagsJson) {
        Material material = new Material();
        material.setId(UUID.nameUUIDFromBytes(suffix.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        material.setUser(user);
        material.setTitle(title);
        material.setSummary(content);
        material.setContent(content);
        material.setTagsJson(tagsJson);
        material.setStatus("approved");
        return material;
    }
}
