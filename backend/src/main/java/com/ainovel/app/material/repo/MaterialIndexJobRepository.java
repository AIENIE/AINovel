package com.ainovel.app.material.repo;

import com.ainovel.app.material.model.MaterialIndexJob;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.*;

public interface MaterialIndexJobRepository extends JpaRepository<MaterialIndexJob,UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select j from MaterialIndexJob j where j.id=:id")
    Optional<MaterialIndexJob> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select j from MaterialIndexJob j where j.material.id=:materialId")
    Optional<MaterialIndexJob> findByMaterialIdForUpdate(@org.springframework.data.repository.query.Param("materialId") UUID materialId);
    Optional<MaterialIndexJob> findByMaterialId(UUID materialId);
    List<MaterialIndexJob> findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(String status, Instant now, Pageable pageable);
    List<MaterialIndexJob> findByStatus(String status);
}
