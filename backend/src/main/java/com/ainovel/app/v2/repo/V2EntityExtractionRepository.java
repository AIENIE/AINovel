package com.ainovel.app.v2.repo;

import com.ainovel.app.v2.model.V2EntityExtraction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface V2EntityExtractionRepository extends JpaRepository<V2EntityExtraction, UUID> {
    List<V2EntityExtraction> findByStoryIdOrderByCreatedAtDesc(UUID storyId);
    Optional<V2EntityExtraction> findByStoryIdAndId(UUID storyId, UUID id);
}
