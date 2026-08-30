package com.ainovel.app.material.repo;

import com.ainovel.app.material.model.MaterialIndexJob;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.*;

public interface MaterialIndexJobRepository extends JpaRepository<MaterialIndexJob,UUID> {
    Optional<MaterialIndexJob> findByMaterialId(UUID materialId);
    List<MaterialIndexJob> findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(String status, Instant now, Pageable pageable);
    List<MaterialIndexJob> findByStatus(String status);
}
