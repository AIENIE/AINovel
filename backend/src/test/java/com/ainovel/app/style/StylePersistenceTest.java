package com.ainovel.app.style;

import com.ainovel.app.story.model.CharacterCard;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.style.model.CharacterVoice;
import com.ainovel.app.style.model.StyleAnalysisJob;
import com.ainovel.app.style.model.StyleProfile;
import com.ainovel.app.style.model.StyleProfileSceneOverride;
import com.ainovel.app.style.repo.CharacterVoiceRepository;
import com.ainovel.app.style.repo.StyleAnalysisJobRepository;
import com.ainovel.app.style.repo.StyleProfileRepository;
import com.ainovel.app.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class StylePersistenceTest {
    @Autowired
    private org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager entityManager;

    @Autowired
    private StyleProfileRepository styleProfileRepository;

    @Autowired
    private CharacterVoiceRepository characterVoiceRepository;

    @Autowired
    private StyleAnalysisJobRepository styleAnalysisJobRepository;

    @Test
    void styleProfileShouldPersistSceneOverridesAndActiveState() {
        User user = persistUser("style-user");
        Story story = persistStory(user);

        StyleProfile profile = new StyleProfile();
        profile.setUser(user);
        profile.setStory(story);
        profile.setName("冷峻悬疑画像");
        profile.setProfileType("narrative");
        profile.setDimensionsJson("{\"formality\":7,\"pacing\":8}");
        profile.setSampleText("雨水压低了巷口的声音。");
        profile.setAiAnalysisJson("{\"summary\":\"冷峻克制\"}");
        profile.setActive(true);

        StyleProfileSceneOverride override = new StyleProfileSceneOverride();
        override.setSceneType("dialogue");
        override.setOverrideJson("{\"dialogue_ratio\":8}");
        profile.addSceneOverride(override);

        StyleProfile saved = styleProfileRepository.saveAndFlush(profile);
        entityManager.clear();

        StyleProfile found = styleProfileRepository.findByStoryAndId(story, saved.getId()).orElseThrow();
        assertTrue(found.isActive());
        assertEquals("冷峻悬疑画像", found.getName());
        assertEquals(1, found.getSceneOverrides().size());
        assertEquals("dialogue", found.getSceneOverrides().get(0).getSceneType());
        assertEquals("{\"dialogue_ratio\":8}", found.getSceneOverrides().get(0).getOverrideJson());
    }

    @Test
    void characterVoiceAndStyleAnalysisJobShouldPersistJsonPayloads() {
        User user = persistUser("voice-user");
        Story story = persistStory(user);
        CharacterCard card = persistCharacter(story, "林烬");

        CharacterVoice voice = new CharacterVoice();
        voice.setStory(story);
        voice.setCharacterCard(card);
        voice.setSpeechPattern("短句、反问、重视证据");
        voice.setVocabularyLevel("colloquial");
        voice.setCatchphrasesJson("[\"这事不对劲\"]");
        voice.setEmotionalRangeJson("[\"冷静\",\"讽刺\"]");
        voice.setDialect("无");
        voice.setSampleDialoguesJson("[{\"context\":\"调查\",\"line\":\"先别急。\"}]");
        voice.setAiAnalysisJson("{\"source\":\"test\"}");

        CharacterVoice savedVoice = characterVoiceRepository.saveAndFlush(voice);

        StyleAnalysisJob job = new StyleAnalysisJob();
        job.setUser(user);
        job.setSourceType("uploaded_text");
        job.setSourceReference("settings-style");
        job.setStatus("completed");
        job.setResultJson("{\"dimensions\":{\"pacing\":8}}");
        styleAnalysisJobRepository.saveAndFlush(job);
        entityManager.clear();

        CharacterVoice foundVoice = characterVoiceRepository.findByStoryAndId(story, savedVoice.getId()).orElseThrow();
        assertEquals("林烬", foundVoice.getCharacterCard().getName());
        assertEquals("[\"这事不对劲\"]", foundVoice.getCatchphrasesJson());
        assertEquals("短句、反问、重视证据", foundVoice.getSpeechPattern());
        assertEquals(1, styleAnalysisJobRepository.findByUserOrderByCreatedAtDesc(user).size());
        assertTrue(styleAnalysisJobRepository.findByUserOrderByCreatedAtDesc(user).get(0).getResultJson().contains("\"pacing\":8"));
    }

    private User persistUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("x");
        user.setRemoteUid((long) username.hashCode());
        entityManager.persist(user);
        return user;
    }

    private Story persistStory(User user) {
        Story story = new Story();
        story.setUser(user);
        story.setTitle("雨城疑案");
        story.setGenre("悬疑");
        story.setTone("冷峻");
        story.setStatus("draft");
        entityManager.persist(story);
        return story;
    }

    private CharacterCard persistCharacter(Story story, String name) {
        CharacterCard card = new CharacterCard();
        card.setStory(story);
        card.setName(name);
        card.setSynopsis("谨慎的调查者");
        entityManager.persist(card);
        return card;
    }
}
