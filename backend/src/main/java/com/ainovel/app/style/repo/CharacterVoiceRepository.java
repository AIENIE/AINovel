package com.ainovel.app.style.repo;

import com.ainovel.app.story.model.CharacterCard;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.style.model.CharacterVoice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CharacterVoiceRepository extends JpaRepository<CharacterVoice, UUID> {
    @EntityGraph(attributePaths = "characterCard")
    List<CharacterVoice> findByStoryOrderByCreatedAtDesc(Story story);

    @EntityGraph(attributePaths = "characterCard")
    Optional<CharacterVoice> findByStoryAndId(Story story, UUID id);

    Optional<CharacterVoice> findByCharacterCard(CharacterCard characterCard);
}
