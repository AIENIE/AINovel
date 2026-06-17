package com.ainovel.app.v2.repo;

import com.ainovel.app.v2.model.V2LorebookEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface V2LorebookEntryRepository extends JpaRepository<V2LorebookEntry, UUID> {
    List<V2LorebookEntry> findByStoryIdOrderByPriorityDesc(UUID storyId);
    Optional<V2LorebookEntry> findByStoryIdAndId(UUID storyId, UUID id);
}
