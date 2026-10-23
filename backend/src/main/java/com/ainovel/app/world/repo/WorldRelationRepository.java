package com.ainovel.app.world.repo;

import com.ainovel.app.world.model.WorldRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface WorldRelationRepository extends JpaRepository<WorldRelation, UUID> {
    List<WorldRelation> findByWorldIdOrderById(UUID worldId);
}
