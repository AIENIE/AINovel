package com.ainovel.app.v2.repo;

import com.ainovel.app.v2.model.V2ExportJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface V2ExportJobRepository extends JpaRepository<V2ExportJob, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select j from V2ExportJob j where j.id=:id")
    Optional<V2ExportJob> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
    List<V2ExportJob> findByManuscriptIdOrderByCreatedAtDesc(UUID manuscriptId);
    Optional<V2ExportJob> findByManuscriptIdAndId(UUID manuscriptId, UUID id);
    List<V2ExportJob> findByUserId(UUID userId);
    List<V2ExportJob> findByExpiresAtBeforeAndStatusNot(Instant now, String status);
    List<V2ExportJob> findTop100ByStatusOrderByCreatedAtAsc(String status);
    List<V2ExportJob> findByStatus(String status);
    long countByUserIdAndStatusIn(UUID userId, java.util.Collection<String> statuses);
}
