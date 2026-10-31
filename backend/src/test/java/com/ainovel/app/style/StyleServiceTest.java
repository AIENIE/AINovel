package com.ainovel.app.style;

import com.ainovel.app.common.JsonColumnCodec;
import com.ainovel.app.story.model.CharacterCard;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.story.repo.CharacterCardRepository;
import com.ainovel.app.style.model.CharacterVoice;
import com.ainovel.app.style.model.StyleProfile;
import com.ainovel.app.style.repo.CharacterVoiceRepository;
import com.ainovel.app.style.repo.StyleAnalysisJobRepository;
import com.ainovel.app.style.repo.StyleProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StyleServiceTest {
    @Test
    void activateStyleProfileShouldDeactivateOtherProfilesInSameStory() {
        StyleProfileRepository profileRepository = mock(StyleProfileRepository.class);
        StyleService service = new StyleService(
                profileRepository,
                mock(CharacterVoiceRepository.class),
                mock(StyleAnalysisJobRepository.class),
                mock(CharacterCardRepository.class),
                new JsonColumnCodec(new ObjectMapper())
        );
        Story story = story();
        StyleProfile selected = profile(UUID.randomUUID(), "目标画像", false);
        StyleProfile previous = profile(UUID.randomUUID(), "旧画像", true);

        when(profileRepository.findByStoryAndId(story, selected.getId())).thenReturn(Optional.of(selected));
        when(profileRepository.findByStoryOrderByCreatedAtDesc(story)).thenReturn(List.of(selected, previous));
        when(profileRepository.save(selected)).thenReturn(selected);

        Map<String, Object> response = service.activateStyleProfile(story, selected.getId());

        assertTrue(selected.isActive());
        assertFalse(previous.isActive());
        assertEquals("目标画像", response.get("name"));
        assertEquals(true, response.get("isActive"));
    }

    @Test
    void createCharacterVoiceShouldRejectDuplicateVoiceForSameCharacter() {
        CharacterVoiceRepository voiceRepository = mock(CharacterVoiceRepository.class);
        CharacterCardRepository cardRepository = mock(CharacterCardRepository.class);
        StyleService service = new StyleService(
                mock(StyleProfileRepository.class),
                voiceRepository,
                mock(StyleAnalysisJobRepository.class),
                cardRepository,
                new JsonColumnCodec(new ObjectMapper())
        );
        Story story = story();
        CharacterCard card = new CharacterCard();
        UUID characterId = UUID.randomUUID();
        card.setId(characterId);
        card.setStory(story);

        when(cardRepository.findByIdWithStoryUser(characterId)).thenReturn(Optional.of(card));
        when(voiceRepository.findByCharacterCard(card)).thenReturn(Optional.of(new CharacterVoice()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.createCharacterVoice(story, Map.of("characterCardId", characterId.toString()))
        );
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void malformedStoredShapesDoNotBecomeEmptySettings() {
        var profiles = mock(StyleProfileRepository.class);
        var voices = mock(CharacterVoiceRepository.class);
        var service = new StyleService(profiles, voices, mock(StyleAnalysisJobRepository.class),
                mock(CharacterCardRepository.class), new JsonColumnCodec(new ObjectMapper()));
        Story story = story();
        var profile = profile(UUID.randomUUID(), "保留画像", false);
        when(profiles.findByStoryOrderByCreatedAtDesc(story)).thenReturn(List.of(profile));
        profile.setDimensionsJson("[]");
        assertThrows(com.ainovel.app.common.ApiStatusException.class, () -> service.listStyleProfiles(story));
        profile.setDimensionsJson(null);
        assertEquals(Map.of(), service.listStyleProfiles(story).getFirst().get("dimensions"));
        var voice = new CharacterVoice(); voice.setSampleDialoguesJson("{}");
        when(voices.findByStoryOrderByCreatedAtDesc(story)).thenReturn(List.of(voice));
        assertThrows(com.ainovel.app.common.ApiStatusException.class, () -> service.listCharacterVoices(story));
        verify(profiles, never()).save(any()); verify(voices, never()).save(any());
    }

    @Test
    void optionalNullVoiceListsRemainValidEmptyLists() {
        var profiles = mock(StyleProfileRepository.class);
        var voices = mock(CharacterVoiceRepository.class);
        var cards = mock(CharacterCardRepository.class);
        var service = new StyleService(profiles, voices, mock(StyleAnalysisJobRepository.class),
                cards, new JsonColumnCodec(new ObjectMapper()));
        Story story = story();
        var card = new CharacterCard(); card.setId(UUID.randomUUID()); card.setStory(story);
        when(cards.findByIdWithStoryUser(card.getId())).thenReturn(Optional.of(card));
        when(voices.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("characterCardId", card.getId());
        payload.put("catchphrases", null); payload.put("emotionalRange", null); payload.put("sampleDialogues", null);
        Map<String, Object> response = service.createCharacterVoice(story, payload);
        assertEquals(List.of(), response.get("catchphrases"));
        assertEquals(List.of(), response.get("emotionalRange"));
        assertEquals(List.of(), response.get("sampleDialogues"));
    }

    private Story story() {
        Story story = new Story();
        story.setId(UUID.randomUUID());
        story.setTitle("雨城疑案");
        return story;
    }

    private StyleProfile profile(UUID id, String name, boolean active) {
        StyleProfile profile = new StyleProfile();
        profile.setId(id);
        profile.setName(name);
        profile.setProfileType("narrative");
        profile.setDimensionsJson("{}");
        profile.setAiAnalysisJson("{}");
        profile.setActive(active);
        return profile;
    }
}
