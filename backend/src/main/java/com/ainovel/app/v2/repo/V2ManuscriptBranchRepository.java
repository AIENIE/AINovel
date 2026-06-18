package com.ainovel.app.v2.repo;

import com.ainovel.app.v2.model.V2ManuscriptBranch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface V2ManuscriptBranchRepository extends JpaRepository<V2ManuscriptBranch, UUID> {
    List<V2ManuscriptBranch> findByManuscriptId(UUID manuscriptId);
    Optional<V2ManuscriptBranch> findByManuscriptIdAndId(UUID manuscriptId, UUID id);
}
