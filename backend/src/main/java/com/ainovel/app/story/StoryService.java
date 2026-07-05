package com.ainovel.app.story;

import com.ainovel.app.common.BusinessException;
import com.ainovel.app.common.RefineRequest;
import com.ainovel.app.story.dto.*;
import com.ainovel.app.story.model.CharacterCard;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.story.repo.CharacterCardRepository;
import com.ainovel.app.story.repo.StoryRepository;
import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.dto.AiRefineRequest;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;

@Service
public class StoryService {
    @Autowired
    private StoryRepository storyRepository;
    @Autowired
    private CharacterCardRepository characterCardRepository;
    @Autowired
    private AiService aiService;
    @Autowired
    private ResourceAccessGuard accessGuard;
    @Autowired
    private StoryConceptionService storyConceptionService;

    public List<StoryDto> listStories(User user) {
        accessGuard.assertCurrentUserEquals(user.getUsername());
        return storyRepository.findByUser(user).stream().map(this::toDto).toList();
    }

    public StoryDto getStory(UUID id) {
        Story story = storyRepository.findByIdWithUser(id).orElseThrow(() -> new BusinessException("故事不存在"));
        accessGuard.assertOwner(story.getUser());
        return toDto(story);
    }

    @Transactional
    public StoryDto createStory(User user, StoryCreateRequest request) {
        Story story = new Story();
        story.setUser(user);
        story.setTitle(request.title());
        story.setSynopsis(request.synopsis());
        story.setGenre(request.genre());
        story.setTone(request.tone());
        story.setWorldId(request.worldId());
        story.setStatus("draft");
        storyRepository.save(story);
        return toDto(story);
    }

    @Transactional
    public StoryDto updateStory(UUID id, StoryUpdateRequest request) {
        Story story = storyRepository.findByIdWithUser(id).orElseThrow(() -> new BusinessException("故事不存在"));
        accessGuard.assertOwner(story.getUser());
        if (request.title() != null) story.setTitle(request.title());
        if (request.synopsis() != null) story.setSynopsis(request.synopsis());
        if (request.genre() != null) story.setGenre(request.genre());
        if (request.tone() != null) story.setTone(request.tone());
        if (request.status() != null) story.setStatus(request.status());
        if (request.worldId() != null) story.setWorldId(request.worldId());
        storyRepository.save(story);
        return toDto(story);
    }

    @Transactional
    public void deleteStory(UUID id) {
        Story story = storyRepository.findByIdWithUser(id).orElseThrow(() -> new BusinessException("故事不存在"));
        accessGuard.assertOwner(story.getUser());
        characterCardRepository.deleteAll(characterCardRepository.findByStory(story));
        storyRepository.delete(story);
    }

    public List<CharacterDto> listCharacters(UUID storyId) {
        Story story = storyRepository.findByIdWithUser(storyId).orElseThrow();
        accessGuard.assertOwner(story.getUser());
        return characterCardRepository.findByStory(story).stream().map(this::toCharacterDto).toList();
    }

    @Transactional
    public CharacterDto addCharacter(UUID storyId, CharacterRequest request) {
        Story story = storyRepository.findByIdWithUser(storyId).orElseThrow();
        accessGuard.assertOwner(story.getUser());
        CharacterCard card = new CharacterCard();
        card.setStory(story);
        card.setName(request.name());
        card.setSynopsis(request.synopsis());
        card.setDetails(request.details());
        card.setRelationships(request.relationships());
        characterCardRepository.save(card);
        return toCharacterDto(card);
    }

    @Transactional
    public CharacterDto updateCharacter(UUID id, CharacterRequest request) {
        CharacterCard card = characterCardRepository.findByIdWithStoryUser(id).orElseThrow(() -> new BusinessException("角色不存在"));
        accessGuard.assertOwner(card.getStory().getUser());
        if (request.name() != null) card.setName(request.name());
        card.setSynopsis(request.synopsis());
        card.setDetails(request.details());
        card.setRelationships(request.relationships());
        characterCardRepository.save(card);
        return toCharacterDto(card);
    }

    @Transactional
    public void deleteCharacter(UUID id) {
        CharacterCard card = characterCardRepository.findByIdWithStoryUser(id).orElseThrow(() -> new BusinessException("角色不存在"));
        accessGuard.assertOwner(card.getStory().getUser());
        characterCardRepository.delete(card);
    }

    public String refineStory(User user, UUID storyId, RefineRequest request) {
        Story story = storyRepository.findByIdWithUser(storyId).orElseThrow(() -> new BusinessException("故事不存在"));
        accessGuard.assertOwner(story.getUser());
        String instruction = request.instruction() == null ? "" : request.instruction();
        return aiService.refine(user, new AiRefineRequest(request.text(), instruction, null)).result();
    }

    public String refineCharacter(User user, UUID characterId, RefineRequest request) {
        CharacterCard card = characterCardRepository.findByIdWithStoryUser(characterId).orElseThrow(() -> new BusinessException("角色不存在"));
        accessGuard.assertOwner(card.getStory().getUser());
        String instruction = request.instruction() == null ? "" : request.instruction();
        return aiService.refine(user, new AiRefineRequest(request.text(), instruction, null)).result();
    }

    @Transactional
    public Map<String, Object> conception(User user, StoryCreateRequest request) {
        StoryDto story = createStory(user, request);
        StoryConceptionService.ConceptionDraft draft = storyConceptionService.draftConception(user, request);
        StoryDto updatedStory = story;
        if (!draft.generated().isEmpty()) {
            updatedStory = updateStory(story.id(), draft.storyUpdate());
        }
        List<CharacterDto> createdCharacters = new ArrayList<>();
        for (CharacterRequest characterRequest : draft.characterRequests()) {
            createdCharacters.add(addCharacter(updatedStory.id(), characterRequest));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("generatedAt", Instant.now().toString());
        result.put("storyCard", updatedStory);
        result.put("characterCards", createdCharacters);
        result.put("plotPlanning", draft.plotPlanning());
        result.put("outlineSeed", draft.outlineSeed());
        result.put("generated", draft.generated());
        return result;
    }

    private StoryDto toDto(Story story) {
        return new StoryDto(story.getId(), story.getTitle(), story.getSynopsis(), story.getGenre(), story.getTone(), story.getStatus(), story.getWorldId(), story.getUpdatedAt());
    }

    private CharacterDto toCharacterDto(CharacterCard card) {
        return new CharacterDto(card.getId(), card.getName(), card.getSynopsis(), card.getDetails(), card.getRelationships(), card.getUpdatedAt());
    }
}
