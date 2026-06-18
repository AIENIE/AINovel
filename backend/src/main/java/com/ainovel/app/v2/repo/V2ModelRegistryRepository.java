package com.ainovel.app.v2.repo;

import com.ainovel.app.v2.model.V2ModelRegistry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface V2ModelRegistryRepository extends JpaRepository<V2ModelRegistry, UUID> {
    Optional<V2ModelRegistry> findByModelKey(String modelKey);
    List<V2ModelRegistry> findByOrderByPriorityDesc();
}
