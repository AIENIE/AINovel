package com.ainovel.app.v2.repo;

import com.ainovel.app.v2.model.V2AnalysisJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface V2AnalysisJobRepository extends JpaRepository<V2AnalysisJob, UUID> {
    List<V2AnalysisJob> findByStoryIdOrderByCreatedAtDesc(UUID storyId);
    Optional<V2AnalysisJob> findByStoryIdAndId(UUID storyId, UUID id);
}
