package com.ainovel.app.style;

import com.ainovel.app.story.model.CharacterCard;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.style.model.CharacterVoice;
import com.ainovel.app.style.model.StyleProfile;
import com.ainovel.app.style.repo.CharacterVoiceRepository;
import com.ainovel.app.style.repo.StyleProfileRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StyleContextProvider {
    private static final String EMPTY_CONTEXT = "暂无已配置的风格画像或角色声音。";

    private final StyleProfileRepository styleProfileRepository;
    private final CharacterVoiceRepository characterVoiceRepository;

    public StyleContextProvider(StyleProfileRepository styleProfileRepository,
                                CharacterVoiceRepository characterVoiceRepository) {
        this.styleProfileRepository = styleProfileRepository;
        this.characterVoiceRepository = characterVoiceRepository;
    }

    public String buildSlopContext(Story story) {
        if (story == null) {
            return EMPTY_CONTEXT;
        }
        StringBuilder builder = new StringBuilder();
        styleProfileRepository.findFirstByStoryAndActiveTrue(story).ifPresent(profile -> appendProfile(builder, profile));
        List<CharacterVoice> voices = characterVoiceRepository.findByStoryOrderByCreatedAtDesc(story);
        for (CharacterVoice voice : voices) {
            appendVoice(builder, voice);
        }
        return builder.isEmpty() ? EMPTY_CONTEXT : truncate(builder.toString().trim(), 1800);
    }

    private void appendProfile(StringBuilder builder, StyleProfile profile) {
        builder.append("Active style profile: ").append(safe(profile.getName(), "未命名画像"))
                .append(" type=").append(safe(profile.getProfileType(), "global"))
                .append(" dimensions=").append(safe(profile.getDimensionsJson(), "{}"));
        if (profile.getAiAnalysisJson() != null && !profile.getAiAnalysisJson().isBlank()) {
            builder.append(" analysis=").append(truncate(profile.getAiAnalysisJson(), 300));
        }
        if (profile.getSampleText() != null && !profile.getSampleText().isBlank()) {
            builder.append(" sample=").append(truncate(profile.getSampleText(), 180));
        }
        builder.append('\n');
    }

    private void appendVoice(StringBuilder builder, CharacterVoice voice) {
        CharacterCard card = voice.getCharacterCard();
        builder.append("Character voice: ")
                .append(card == null ? "未命名角色" : safe(card.getName(), "未命名角色"))
                .append(" speechPattern=").append(safe(voice.getSpeechPattern(), ""))
                .append(" vocabularyLevel=").append(safe(voice.getVocabularyLevel(), "neutral"));
        if (voice.getCatchphrasesJson() != null && !voice.getCatchphrasesJson().isBlank()) {
            builder.append(" catchphrases=").append(truncate(voice.getCatchphrasesJson(), 180));
        }
        if (voice.getSampleDialoguesJson() != null && !voice.getSampleDialoguesJson().isBlank()) {
            builder.append(" samples=").append(truncate(voice.getSampleDialoguesJson(), 240));
        }
        builder.append('\n');
    }

    private String safe(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...";
    }
}
