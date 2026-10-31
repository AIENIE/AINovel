package com.ainovel.app.material;

import com.ainovel.app.material.dto.MaterialSearchRequest;
import com.ainovel.app.material.dto.MaterialSearchResultDto;
import com.ainovel.app.material.model.Material;
import com.ainovel.app.material.evidence.MaterialEvidenceService;
import com.ainovel.app.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import java.util.UUID;

@Service
public class MaterialRetrievalService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MaterialRetrievalService.class);
    private final MaterialEvidenceService materialEvidence;
    private final MaterialVectorIndex vectors;

    public MaterialRetrievalService(MaterialEvidenceService materialEvidence, MaterialVectorIndex vectors) {
        this.materialEvidence = materialEvidence;
        this.vectors = vectors;
    }

    @Transactional
    public void indexMaterial(Material material) {
        materialEvidence.capture(material);
    }

    public void deleteIndexAfterCommit(UUID materialId) {
        Runnable cleanup = () -> {
            try { vectors.deleteMaterial(materialId); }
            catch (RuntimeException ex) {
                log.warn("event=material_vector_delete_failed materialId={} errorType={}", materialId, ex.getClass().getSimpleName());
                throw new com.ainovel.app.common.ApiStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "MATERIAL_VECTOR_CLEANUP_FAILED");
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cleanup.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { cleanup.run(); }
        });
    }

    public List<MaterialSearchResultDto> search(User user, MaterialSearchRequest request) {
        String query = request == null || request.query() == null ? "" : request.query().trim();
        int limit = Math.max(1, Math.min(request != null && request.limit() != null ? request.limit() : 10, 30));
        return materialEvidence.legacySearch(user, query, limit);
    }

}
