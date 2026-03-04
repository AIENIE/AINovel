package com.ainovel.app.story.repo;

import com.ainovel.app.story.model.CharacterCard;
import com.ainovel.app.story.model.Story;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CharacterCardRepository extends JpaRepository<CharacterCard, UUID> {
    List<CharacterCard> findByStory(Story story);

    @Query("select c from CharacterCard c join fetch c.story s join fetch s.user where c.id = :id")
    Optional<CharacterCard> findByIdWithStoryUser(@Param("id") UUID id);
}
