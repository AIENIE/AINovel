package com.ainovel.app.material;

import com.ainovel.app.common.ApiStatusException;
import com.ainovel.app.material.evidence.MaterialEvidenceService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MaterialRetrievalCleanupTest {
    @Test void cleanupFailureAfterCommitIsReportedInsteadOfReturningSuccess() {
        var vectors = mock(MaterialVectorIndex.class);
        var service = new MaterialRetrievalService(mock(MaterialEvidenceService.class), vectors);
        UUID id = UUID.randomUUID();
        doThrow(new IllegalStateException("upstream unavailable")).when(vectors).deleteMaterial(id);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.deleteIndexAfterCommit(id);
            verifyNoInteractions(vectors);
            var callback = TransactionSynchronizationManager.getSynchronizations().getFirst();
            var error = assertThrows(ApiStatusException.class, callback::afterCommit);
            assertEquals("MATERIAL_VECTOR_CLEANUP_FAILED", error.getMessage());
            assertEquals(502, error.getStatus().value());
            verify(vectors).deleteMaterial(id);
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }
}
