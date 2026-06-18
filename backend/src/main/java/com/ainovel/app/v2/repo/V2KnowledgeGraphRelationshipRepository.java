package com.ainovel.app.v2.repo;

import com.ainovel.app.v2.model.V2KnowledgeGraphRelationship;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface V2KnowledgeGraphRelationshipRepository extends JpaRepository<V2KnowledgeGraphRelationship, UUID> {
    List<V2KnowledgeGraphRelationship> findByStoryId(UUID storyId);
    void deleteByStoryIdAndId(UUID storyId, UUID id);
    void deleteByStoryIdAndSourceIdOrStoryIdAndTargetId(UUID storyIdA, UUID sourceId, UUID storyIdB, UUID targetId);
}
