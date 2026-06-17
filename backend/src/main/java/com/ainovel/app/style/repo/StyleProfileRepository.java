package com.ainovel.app.style.repo;

import com.ainovel.app.story.model.Story;
import com.ainovel.app.style.model.StyleProfile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StyleProfileRepository extends JpaRepository<StyleProfile, UUID> {
    @EntityGraph(attributePaths = "sceneOverrides")
    List<StyleProfile> findByStoryOrderByCreatedAtDesc(Story story);

    @EntityGraph(attributePaths = "sceneOverrides")
    Optional<StyleProfile> findByStoryAndId(Story story, UUID id);

    @EntityGraph(attributePaths = "sceneOverrides")
    Optional<StyleProfile> findFirstByStoryAndActiveTrue(Story story);
}
