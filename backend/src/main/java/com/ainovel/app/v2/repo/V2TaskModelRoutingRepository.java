package com.ainovel.app.v2.repo;

import com.ainovel.app.v2.model.V2TaskModelRouting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface V2TaskModelRoutingRepository extends JpaRepository<V2TaskModelRouting, UUID> {
    Optional<V2TaskModelRouting> findByTaskType(String taskType);
}
