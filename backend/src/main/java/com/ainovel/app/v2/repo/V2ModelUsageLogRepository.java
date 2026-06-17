package com.ainovel.app.v2.repo;

import com.ainovel.app.v2.model.V2ModelUsageLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface V2ModelUsageLogRepository extends JpaRepository<V2ModelUsageLog, UUID> {
    List<V2ModelUsageLog> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<V2ModelUsageLog> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
