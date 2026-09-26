package com.ainovel.app.material;

import com.ainovel.app.material.model.*;
import com.ainovel.app.material.repo.*;
import com.ainovel.app.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MaterialIndexBudgetTest {
    final MaterialRepository materials = mock(MaterialRepository.class);
    final MaterialChunkProjectionRepository chunks = mock(MaterialChunkProjectionRepository.class);
    final MaterialIndexJobRepository jobs = mock(MaterialIndexJobRepository.class);
    final TextEmbeddingClient embeddings = mock(TextEmbeddingClient.class);
    final MaterialVectorIndex vectors = mock(MaterialVectorIndex.class);
    final TransactionTemplate tx = mock(TransactionTemplate.class);
    final Material material = new Material(); final MaterialIndexJob job = new MaterialIndexJob();
    final MaterialRetrievalService service;
    MaterialIndexBudgetTest() {
        material.setId(UUID.randomUUID()); material.setContentVersion(1); material.setContent("测试素材"); material.setStatus("approved");
        User user = new User(); user.setId(UUID.randomUUID()); user.setUsername("author"); material.setUser(user);
        ReflectionTestUtils.setField(job,"id",UUID.randomUUID()); job.setMaterial(material); job.setStatus("queued");
        when(jobs.findByIdForUpdate(job.getId())).thenReturn(Optional.of(job));
        when(materials.findByIdForUpdate(material.getId())).thenReturn(Optional.of(material));
        when(tx.execute(any())).thenAnswer(i -> ((TransactionCallback<?>) i.getArgument(0)).doInTransaction(mock(TransactionStatus.class)));
        doAnswer(i -> { ((Consumer<TransactionStatus>) i.getArgument(0)).accept(mock(TransactionStatus.class)); return null; }).when(tx).executeWithoutResult(any());
        service = new MaterialRetrievalService(materials,new MaterialChunker(),embeddings,vectors,chunks,jobs,tx);
    }
    @Test void oversizedMaterialFailsBeforeRemoteCallOrProjectionDeletion() {
        material.setContent("中".repeat(512*780+121));
        ReflectionTestUtils.invokeMethod(service,"process",job.getId());
        assertEquals("failed",job.getStatus()); assertEquals("INDEX_TOO_LARGE",job.getErrorCode());
        verifyNoInteractions(embeddings); verify(chunks,never()).deleteByMaterialId(any());
    }
    @Test void fifthFailedAttemptIsTerminal() {
        job.setAttemptCount(4); when(embeddings.embed(any(),anyString())).thenThrow(new IllegalStateException("offline failure"));
        ReflectionTestUtils.invokeMethod(service,"process",job.getId());
        assertEquals(5,job.getAttemptCount()); assertEquals("failed",job.getStatus()); assertNull(job.getLeaseOwner());
    }
    @Test void editedSourceCannotPublishStaleVectorsOrReplaceProjection() {
        when(embeddings.embed(any(),anyString())).thenAnswer(i -> { material.setContentVersion(2); return new float[]{1}; });
        ReflectionTestUtils.invokeMethod(service,"process",job.getId());
        verify(vectors,never()).upsert(any(),any()); verify(chunks,never()).deleteByMaterialId(any());
        assertNotEquals("completed",job.getStatus());
    }
    @Test void schedulerOnlyDispatchesAndDoesNotEmbedInline() {
        List<Runnable> queued = new ArrayList<>();
        ReflectionTestUtils.setField(service,"executor",(java.util.concurrent.Executor) queued::add);
        when(jobs.findByStatus("processing")).thenReturn(List.of());
        when(jobs.findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(eq("queued"),any(),any())).thenReturn(List.of(job));
        service.dispatchIndexJobs(); service.dispatchIndexJobs();
        assertEquals(1,queued.size()); verifyNoInteractions(embeddings);
    }
}
