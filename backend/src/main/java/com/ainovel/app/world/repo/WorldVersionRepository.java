package com.ainovel.app.world.repo;

import com.ainovel.app.world.model.WorldVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface WorldVersionRepository extends JpaRepository<WorldVersion, UUID> {
    List<WorldVersion> findByWorldIdOrderBySequenceNumberDesc(UUID worldId);
    java.util.Optional<WorldVersion> findByWorldIdAndRequestKey(UUID worldId, String requestKey);
    java.util.Optional<WorldVersion> findFirstByWorldIdAndOriginOrderBySequenceNumberDesc(UUID worldId, String origin);
    long countByWorldId(UUID worldId);
}
