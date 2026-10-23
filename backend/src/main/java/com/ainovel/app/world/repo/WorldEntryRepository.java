package com.ainovel.app.world.repo;

import com.ainovel.app.world.model.WorldEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface WorldEntryRepository extends JpaRepository<WorldEntry, UUID> {
    List<WorldEntry> findByWorldIdOrderById(UUID worldId);
}
