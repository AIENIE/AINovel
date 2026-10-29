package com.ainovel.app.world.repo;

import com.ainovel.app.world.model.WorldEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface WorldEntryRepository extends JpaRepository<WorldEntry, UUID> {
    List<WorldEntry> findByWorldIdOrderById(UUID worldId);
    @org.springframework.data.jpa.repository.Query(value="select count(*) from world_entries e join worlds w on w.id=e.world_id where w.user_id=:userId and length(trim(e.content))>0",nativeQuery=true)
    long countFilledByUser(UUID userId);
}
