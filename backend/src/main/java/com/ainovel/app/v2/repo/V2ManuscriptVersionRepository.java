package com.ainovel.app.v2.repo;

import com.ainovel.app.v2.model.V2ManuscriptVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface V2ManuscriptVersionRepository extends JpaRepository<V2ManuscriptVersion, UUID> {
    List<V2ManuscriptVersion> findByManuscriptIdOrderByCreatedAtDesc(UUID manuscriptId);
    List<V2ManuscriptVersion> findByManuscriptIdAndBranchId(UUID manuscriptId, UUID branchId);
    Optional<V2ManuscriptVersion> findByManuscriptIdAndId(UUID manuscriptId, UUID id);
    Optional<V2ManuscriptVersion> findTopByManuscriptIdOrderByCreatedAtDesc(UUID manuscriptId);
}
