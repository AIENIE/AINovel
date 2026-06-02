package com.ainovel.app.material;

import com.ainovel.app.material.dto.MaterialSearchRequest;
import com.ainovel.app.material.dto.MaterialSearchResultDto;
import com.ainovel.app.material.model.Material;
import com.ainovel.app.material.repo.MaterialRepository;
import com.ainovel.app.user.User;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MaterialRetrievalServiceTest {

    @Test
    void shouldFallbackToKeywordChunksWhenVectorSearchFails() {
        User user = user();
        Material material = new Material();
        material.setId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        material.setUser(user);
        material.setStatus("approved");
        material.setTitle("旧报纸摘录");
        material.setTagsJson("[\"码头\",\"陆家\"]");
        material.setSummary("关于陆家码头的旧资料");
        material.setContent("十年前，陆家码头在雨夜停用。后来仍有人看见货船靠岸，船灯在雾里闪了三次。");

        MaterialRepository repository = mock(MaterialRepository.class);
        when(repository.findAll()).thenReturn(List.of(material));

        MaterialRetrievalService service = new MaterialRetrievalService(
                repository,
                new MaterialChunker(),
                (embeddingUser, text) -> {
                    throw new RuntimeException("embedding unavailable");
                },
                new MaterialVectorIndex() {
                    @Override
                    public void upsert(MaterialChunk chunk, float[] vector) {
                    }

                    @Override
                    public List<VectorMatch> search(float[] vector, int limit) {
                        throw new RuntimeException("qdrant unavailable");
                    }
                }
        );

        List<MaterialSearchResultDto> results = service.search(user, new MaterialSearchRequest("陆家码头 雨夜", 5));

        assertFalse(results.isEmpty());
        MaterialSearchResultDto first = results.get(0);
        assertEquals(material.getId(), first.materialId());
        assertEquals(0, first.chunkSeq());
        assertEquals("keyword", first.source());
        assertTrue(first.snippet().contains("陆家码头"));
        assertTrue(first.matchReasons().contains("content"));
    }

    private User user() {
        User user = new User();
        user.setId(UUID.fromString("0f41d89f-e04f-47e2-aa87-c2bf9a29fd0f"));
        user.setUsername("material_user");
        user.setEmail("material_user@example.com");
        user.setPasswordHash("hashed");
        user.setRemoteUid(9000003L);
        return user;
    }
}
