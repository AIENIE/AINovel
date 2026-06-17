package com.ainovel.app.style;

import com.ainovel.app.story.model.CharacterCard;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.style.model.CharacterVoice;
import com.ainovel.app.style.model.StyleProfile;
import com.ainovel.app.style.repo.CharacterVoiceRepository;
import com.ainovel.app.style.repo.StyleProfileRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StyleContextProviderTest {
    @Test
    void buildSlopContextShouldIncludeActiveProfileAndCharacterVoices() {
        StyleProfileRepository profileRepository = mock(StyleProfileRepository.class);
        CharacterVoiceRepository voiceRepository = mock(CharacterVoiceRepository.class);
        StyleContextProvider provider = new StyleContextProvider(profileRepository, voiceRepository);
        Story story = new Story();
        story.setTitle("雨城疑案");

        StyleProfile profile = new StyleProfile();
        profile.setName("冷峻悬疑画像");
        profile.setProfileType("narrative");
        profile.setDimensionsJson("{\"formality\":7,\"pacing\":8}");
        profile.setAiAnalysisJson("{\"summary\":\"冷峻、短句、低抒情\"}");
        profile.setSampleText("雨水压低了巷口的声音。");
        when(profileRepository.findFirstByStoryAndActiveTrue(story)).thenReturn(Optional.of(profile));

        CharacterCard card = new CharacterCard();
        card.setName("林烬");
        CharacterVoice voice = new CharacterVoice();
        voice.setCharacterCard(card);
        voice.setSpeechPattern("短句、反问、重视证据");
        voice.setVocabularyLevel("colloquial");
        voice.setCatchphrasesJson("[\"这事不对劲\"]");
        voice.setSampleDialoguesJson("[{\"context\":\"调查\",\"line\":\"先别急。\"}]");
        when(voiceRepository.findByStoryOrderByCreatedAtDesc(story)).thenReturn(List.of(voice));

        String context = provider.buildSlopContext(story);

        assertTrue(context.contains("Active style profile: 冷峻悬疑画像"));
        assertTrue(context.contains("dimensions={\"formality\":7,\"pacing\":8}"));
        assertTrue(context.contains("Character voice: 林烬"));
        assertTrue(context.contains("短句、反问、重视证据"));
        assertTrue(context.contains("这事不对劲"));
    }

    @Test
    void buildSlopContextShouldReturnFallbackWhenNoStyleDataExists() {
        StyleProfileRepository profileRepository = mock(StyleProfileRepository.class);
        CharacterVoiceRepository voiceRepository = mock(CharacterVoiceRepository.class);
        StyleContextProvider provider = new StyleContextProvider(profileRepository, voiceRepository);
        Story story = new Story();

        when(profileRepository.findFirstByStoryAndActiveTrue(story)).thenReturn(Optional.empty());
        when(voiceRepository.findByStoryOrderByCreatedAtDesc(story)).thenReturn(List.of());

        assertEquals("暂无已配置的风格画像或角色声音。", provider.buildSlopContext(story));
    }
}
