package com.ainovel.app.v2.repo;

import com.ainovel.app.v2.model.V2ContinuityIssue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface V2ContinuityIssueRepository extends JpaRepository<V2ContinuityIssue, UUID> {
    List<V2ContinuityIssue> findByStoryIdOrderByCreatedAtDesc(UUID storyId);
    Optional<V2ContinuityIssue> findByStoryIdAndId(UUID storyId, UUID id);
}
