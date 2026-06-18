package com.ainovel.app.v2.repo;

import com.ainovel.app.v2.model.V2ExportJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface V2ExportJobRepository extends JpaRepository<V2ExportJob, UUID> {
    List<V2ExportJob> findByManuscriptIdOrderByCreatedAtDesc(UUID manuscriptId);
    Optional<V2ExportJob> findByManuscriptIdAndId(UUID manuscriptId, UUID id);
    List<V2ExportJob> findByUserId(UUID userId);
    List<V2ExportJob> findByExpiresAtBeforeAndStatusNot(Instant now, String status);
}
