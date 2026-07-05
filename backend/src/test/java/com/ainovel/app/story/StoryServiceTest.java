package com.ainovel.app.story;

import com.ainovel.app.ai.AiService;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.story.dto.CharacterDto;
import com.ainovel.app.story.dto.CharacterRequest;
import com.ainovel.app.story.dto.StoryCreateRequest;
import com.ainovel.app.story.dto.StoryDto;
import com.ainovel.app.story.dto.StoryUpdateRequest;
import com.ainovel.app.story.model.CharacterCard;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.story.repo.CharacterCardRepository;
import com.ainovel.app.story.repo.StoryRepository;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoryServiceTest {

    @Test
    void createStoryShouldPersistDraftWithRequestFields() {
        StoryRepository storyRepository = mock(StoryRepository.class);
        StoryService service = service(storyRepository, mock(CharacterCardRepository.class), mock(ResourceAccessGuard.class), mock(AiService.class));
        User owner = user("story_author");
        ArgumentCaptor<Story> storyCaptor = ArgumentCaptor.forClass(Story.class);

        when(storyRepository.save(any(Story.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StoryDto dto = service.createStory(owner, new StoryCreateRequest(
                "雨城疑案",
                "主角在雨夜追查旧案。",
                "悬疑",
                "冷峻",
                "world-7",
                java.util.Map.of("goal", "快节奏")
        ));

        verify(storyRepository).save(storyCaptor.capture());
        Story saved = storyCaptor.getValue();
        assertSame(owner, saved.getUser());
        assertEquals("draft", saved.getStatus());
        assertEquals("雨城疑案", dto.title());
        assertEquals("world-7", dto.worldId());
    }

    @Test
    void updateStoryShouldOnlyReplaceProvidedFields() {
        StoryRepository storyRepository = mock(StoryRepository.class);
        ResourceAccessGuard accessGuard = mock(ResourceAccessGuard.class);
        StoryService service = service(storyRepository, mock(CharacterCardRepository.class), accessGuard, mock(AiService.class));
        User owner = user("story_editor");
        Story story = story(owner);
        story.setTitle("旧标题");
        story.setSynopsis("旧简介");
        story.setGenre("悬疑");
        story.setTone("克制");
        story.setStatus("draft");
        story.setWorldId("world-1");

        when(storyRepository.findByIdWithUser(story.getId())).thenReturn(Optional.of(story));
        when(storyRepository.save(any(Story.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StoryDto dto = service.updateStory(story.getId(), new StoryUpdateRequest(null, "新简介", null, "冷峻", "published", null));

        assertEquals("旧标题", dto.title());
        assertEquals("新简介", dto.synopsis());
        assertEquals("悬疑", dto.genre());
        assertEquals("冷峻", dto.tone());
        assertEquals("published", dto.status());
        assertEquals("world-1", dto.worldId());
        verify(accessGuard).assertOwner(owner);
    }

    @Test
    void deleteStoryShouldDeleteCharactersBeforeStoryRecord() {
        StoryRepository storyRepository = mock(StoryRepository.class);
        CharacterCardRepository characterCardRepository = mock(CharacterCardRepository.class);
        ResourceAccessGuard accessGuard = mock(ResourceAccessGuard.class);
        StoryService service = service(storyRepository, characterCardRepository, accessGuard, mock(AiService.class));
        User owner = user("story_owner");
        Story story = story(owner);
        List<CharacterCard> cards = List.of(character(story, "林烬"), character(story, "周燃"));

        when(storyRepository.findByIdWithUser(story.getId())).thenReturn(Optional.of(story));
        when(characterCardRepository.findByStory(story)).thenReturn(cards);

        service.deleteStory(story.getId());

        verify(accessGuard).assertOwner(owner);
        verify(characterCardRepository).deleteAll(cards);
        verify(storyRepository).delete(story);
    }

    @Test
    void addCharacterShouldAttachStoryAndReturnDto() {
        StoryRepository storyRepository = mock(StoryRepository.class);
        CharacterCardRepository characterCardRepository = mock(CharacterCardRepository.class);
        ResourceAccessGuard accessGuard = mock(ResourceAccessGuard.class);
        StoryService service = service(storyRepository, characterCardRepository, accessGuard, mock(AiService.class));
        User owner = user("character_author");
        Story story = story(owner);

        when(storyRepository.findByIdWithUser(story.getId())).thenReturn(Optional.of(story));
        when(characterCardRepository.save(any(CharacterCard.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CharacterDto dto = service.addCharacter(story.getId(), new CharacterRequest(
                "林烬",
                "旧案调查员",
                "习惯先做排除再下判断",
                "与周燃互相牵制"
        ));

        assertEquals("林烬", dto.name());
        assertEquals("旧案调查员", dto.synopsis());
        assertEquals("习惯先做排除再下判断", dto.details());
        verify(accessGuard).assertOwner(owner);
    }

    @Test
    void updateCharacterShouldKeepNameWhenRequestDoesNotProvideIt() {
        CharacterCardRepository characterCardRepository = mock(CharacterCardRepository.class);
        ResourceAccessGuard accessGuard = mock(ResourceAccessGuard.class);
        StoryService service = service(mock(StoryRepository.class), characterCardRepository, accessGuard, mock(AiService.class));
        User owner = user("character_editor");
        Story story = story(owner);
        CharacterCard card = character(story, "林烬");
        card.setSynopsis("旧简介");
        card.setDetails("旧细节");
        card.setRelationships("旧关系");

        when(characterCardRepository.findByIdWithStoryUser(card.getId())).thenReturn(Optional.of(card));
        when(characterCardRepository.save(any(CharacterCard.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CharacterDto dto = service.updateCharacter(card.getId(), new CharacterRequest(
                null,
                "新简介",
                "新细节",
                "新关系"
        ));

        assertEquals("林烬", dto.name());
        assertEquals("新简介", dto.synopsis());
        assertEquals("新细节", dto.details());
        assertEquals("新关系", dto.relationships());
        verify(accessGuard).assertOwner(owner);
    }

    @Test
    void conceptionShouldPersistGeneratedStoryFieldsAndFallbackCharacter() {
        StoryRepository storyRepository = mock(StoryRepository.class);
        CharacterCardRepository characterCardRepository = mock(CharacterCardRepository.class);
        ResourceAccessGuard accessGuard = mock(ResourceAccessGuard.class);
        AiService aiService = mock(AiService.class);
        StoryService service = service(storyRepository, characterCardRepository, accessGuard, aiService);
        User owner = user("concept_author");
        UUID storyId = UUID.randomUUID();
        Story persistedStory = story(owner);
        persistedStory.setId(storyId);

        when(storyRepository.save(any(Story.class))).thenAnswer(invocation -> {
            Story story = invocation.getArgument(0);
            if (story.getId() == null) {
                story.setId(storyId);
            }
            return story;
        });
        when(storyRepository.findByIdWithUser(any(UUID.class))).thenReturn(Optional.of(persistedStory));
        when(characterCardRepository.save(any(CharacterCard.class))).thenAnswer(invocation -> {
            CharacterCard card = invocation.getArgument(0);
            if (card.getId() == null) {
                card.setId(UUID.randomUUID());
            }
            return card;
        });
        when(aiService.chat(eq(owner), any())).thenReturn(new com.ainovel.app.ai.dto.AiChatResponse(
                "assistant",
                """
                {"title":"AI 雨城疑案","synopsis":"更新后的梗概","genre":"悬疑","tone":"冷峻","characters":[]}
                """,
                null,
                0
        ));

        Map<String, Object> result = service.conception(owner, new StoryCreateRequest(
                "旧标题",
                "旧梗概",
                "都市悬疑",
                "克制",
                "world-9",
                Map.of("corePromise", "错位真相")
        ));

        StoryDto storyCard = (StoryDto) result.get("storyCard");
        @SuppressWarnings("unchecked")
        List<CharacterDto> characters = (List<CharacterDto>) result.get("characterCards");
        @SuppressWarnings("unchecked")
        Map<String, Object> generated = (Map<String, Object>) result.get("generated");

        assertEquals("AI 雨城疑案", storyCard.title());
        assertEquals("更新后的梗概", storyCard.synopsis());
        assertEquals(2, characters.size());
        assertEquals("主角", characters.get(0).name());
        assertEquals("镜像角色", characters.get(1).name());
        assertTrue(generated.containsKey("plotPlanning"));
        assertTrue(generated.containsKey("outlineSeed"));
    }

    private StoryService service(
            StoryRepository storyRepository,
            CharacterCardRepository characterCardRepository,
            ResourceAccessGuard accessGuard,
            AiService aiService
    ) {
        StoryService service = new StoryService();
        ReflectionTestUtils.setField(service, "storyRepository", storyRepository);
        ReflectionTestUtils.setField(service, "characterCardRepository", characterCardRepository);
        ReflectionTestUtils.setField(service, "accessGuard", accessGuard);
        ReflectionTestUtils.setField(service, "aiService", aiService);
        ReflectionTestUtils.setField(service, "storyConceptionService", storyConceptionService(aiService));
        return service;
    }

    private StoryConceptionService storyConceptionService(AiService aiService) {
        StoryConceptionService service = new StoryConceptionService();
        ReflectionTestUtils.setField(service, "aiService", aiService);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(service, "promptFactory", new StoryConceptionPromptFactory());
        ReflectionTestUtils.setField(service, "projectionBuilder", new StoryConceptionProjectionBuilder());
        ReflectionTestUtils.setField(service, "draftNormalizer", new StoryConceptionDraftNormalizer());
        return service;
    }

    private Story story(User owner) {
        Story story = new Story();
        story.setId(UUID.randomUUID());
        story.setUser(owner);
        story.setTitle("旧故事");
        return story;
    }

    private CharacterCard character(Story story, String name) {
        CharacterCard card = new CharacterCard();
        card.setId(UUID.randomUUID());
        card.setStory(story);
        card.setName(name);
        return card;
    }

    private User user(String username) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("hash");
        user.setRemoteUid(10002L);
        return user;
    }
}
