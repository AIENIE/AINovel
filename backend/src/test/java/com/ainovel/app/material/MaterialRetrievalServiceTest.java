package com.ainovel.app.material;

import com.ainovel.app.material.dto.MaterialSearchRequest;
import com.ainovel.app.material.dto.MaterialSearchResultDto;
import com.ainovel.app.material.model.Material;
import com.ainovel.app.material.model.MaterialChunkProjection;
import com.ainovel.app.material.repo.MaterialChunkProjectionRepository;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;

class MaterialRetrievalServiceTest {

    @Test
    void emptyVisibleCorpusDoesNotCallEmbeddingOrVectorSearch() {
        User user = user();
        MaterialRepository materials = mock(MaterialRepository.class);
        MaterialChunkProjectionRepository chunks = mock(MaterialChunkProjectionRepository.class);
        TextEmbeddingClient embeddings = mock(TextEmbeddingClient.class);
        MaterialVectorIndex vectors = mock(MaterialVectorIndex.class);
        when(chunks.searchVisible(user.getId(), "失物室", 10)).thenReturn(List.of());
        when(chunks.existsVisible(user.getId())).thenReturn(false);
        MaterialRetrievalService service = new MaterialRetrievalService(materials, new MaterialChunker(),
                embeddings, vectors, chunks, null, null);

        assertTrue(service.search(user, new MaterialSearchRequest("失物室", 5)).isEmpty());

        verify(chunks).existsVisible(user.getId());
        verifyNoInteractions(embeddings, vectors);
    }

    @Test
    void productionRetrievalKeepsKeywordResultsWhenUpstreamRejectsEmbedding() {
        User user = user();
        MaterialRepository materials = mock(MaterialRepository.class);
        MaterialChunkProjectionRepository chunks = mock(MaterialChunkProjectionRepository.class);
        TextEmbeddingClient embeddings = mock(TextEmbeddingClient.class);
        MaterialVectorIndex vectors = mock(MaterialVectorIndex.class);
        Material material = new Material();
        material.setId(UUID.randomUUID());
        MaterialChunkProjection chunk = new MaterialChunkProjection();
        chunk.setChunkId("chunk-1");
        chunk.setMaterial(material);
        chunk.setTitle("失物室登记制度");
        chunk.setText("失物室领取物品需核对登记。");
        when(chunks.searchVisible(user.getId(), "失物室", 10)).thenReturn(List.of(chunk));
        when(chunks.existsVisible(user.getId())).thenReturn(true);
        io.grpc.Metadata trailers = new io.grpc.Metadata();
        trailers.put(io.grpc.Metadata.Key.of("x-aienie-upstream-http-status",
                io.grpc.Metadata.ASCII_STRING_MARSHALLER), "401");
        when(embeddings.embed(user, "失物室")).thenThrow(io.grpc.Status.INVALID_ARGUMENT
                .withDescription("upstream rejected request").asRuntimeException(trailers));
        MaterialRetrievalService service = new MaterialRetrievalService(materials, new MaterialChunker(),
                embeddings, vectors, chunks, null, null);

        List<MaterialSearchResultDto> results = service.search(user, new MaterialSearchRequest("失物室", 5));

        assertEquals(1, results.size());
        assertEquals("keyword", results.getFirst().source());
        assertEquals(material.getId(), results.getFirst().materialId());
        verify(embeddings).embed(user, "失物室");
        verifyNoInteractions(vectors);
    }

    @Test
    void legacySearchReturnsKeywordChunksWithoutRemoteRetrieval() {
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

        TextEmbeddingClient embeddings = mock(TextEmbeddingClient.class);
        MaterialVectorIndex vectors = mock(MaterialVectorIndex.class);
        MaterialRetrievalService service = new MaterialRetrievalService(repository, new MaterialChunker(), embeddings, vectors);

        List<MaterialSearchResultDto> results = service.search(user, new MaterialSearchRequest("陆家码头 雨夜", 5));

        assertFalse(results.isEmpty());
        MaterialSearchResultDto first = results.get(0);
        assertEquals(material.getId(), first.materialId());
        assertEquals(0, first.chunkSeq());
        assertEquals("keyword", first.source());
        assertTrue(first.snippet().contains("陆家码头"));
        assertTrue(first.matchReasons().contains("content"));
        verifyNoInteractions(embeddings, vectors);
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
