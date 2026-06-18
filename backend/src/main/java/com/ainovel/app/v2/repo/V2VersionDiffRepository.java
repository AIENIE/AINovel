package com.ainovel.app.v2.repo;

import com.ainovel.app.v2.model.V2VersionDiff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface V2VersionDiffRepository extends JpaRepository<V2VersionDiff, UUID> {
    Optional<V2VersionDiff> findByFromVersionIdAndToVersionId(UUID fromVersionId, UUID toVersionId);
}
