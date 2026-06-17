package com.ainovel.app.v2.repo;

import com.ainovel.app.v2.model.V2AutoSaveConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface V2AutoSaveConfigRepository extends JpaRepository<V2AutoSaveConfig, UUID> {
    Optional<V2AutoSaveConfig> findByUserId(UUID userId);
}
