package com.ainovel.app.style;

import com.ainovel.app.common.BusinessException;

import com.ainovel.app.common.JsonColumnCodec;
import com.ainovel.app.story.model.CharacterCard;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.story.repo.CharacterCardRepository;
import com.ainovel.app.style.model.CharacterVoice;
import com.ainovel.app.style.model.StyleAnalysisJob;
import com.ainovel.app.style.model.StyleProfile;
import com.ainovel.app.style.model.StyleProfileSceneOverride;
import com.ainovel.app.style.repo.CharacterVoiceRepository;
import com.ainovel.app.style.repo.StyleAnalysisJobRepository;
import com.ainovel.app.style.repo.StyleProfileRepository;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

@Service
public class StyleService {
    private final StyleProfileRepository styleProfileRepository;
    private final CharacterVoiceRepository characterVoiceRepository;
    private final StyleAnalysisJobRepository styleAnalysisJobRepository;
    private final CharacterCardRepository characterCardRepository;

    private final JsonColumnCodec jsonColumnCodec;

    public StyleService(StyleProfileRepository styleProfileRepository,
                        CharacterVoiceRepository characterVoiceRepository,
                        StyleAnalysisJobRepository styleAnalysisJobRepository,
                        CharacterCardRepository characterCardRepository,
                        JsonColumnCodec jsonColumnCodec) {
        this.styleProfileRepository = styleProfileRepository;
        this.characterVoiceRepository = characterVoiceRepository;
        this.styleAnalysisJobRepository = styleAnalysisJobRepository;
        this.characterCardRepository = characterCardRepository;

        this.jsonColumnCodec = jsonColumnCodec;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listStyleProfiles(Story story) {
        return styleProfileRepository.findByStoryOrderByCreatedAtDesc(story).stream()
                .map(this::profileResponse)
                .toList();
    }

    @Transactional
    public Map<String, Object> createStyleProfile(User user, Story story, Map<String, Object> payload) {
        StyleProfile profile = new StyleProfile();
        profile.setUser(user);
        profile.setStory(story);
        applyProfilePayload(profile, payload);
        profile.setActive(false);
        return profileResponse(styleProfileRepository.save(profile));
    }

    @Transactional
    public Map<String, Object> updateStyleProfile(Story story, UUID id, Map<String, Object> payload) {
        StyleProfile profile = requireProfile(story, id);
        applyProfilePayload(profile, payload);
        return profileResponse(styleProfileRepository.save(profile));
    }

    @Transactional
    public void deleteStyleProfile(Story story, UUID id) {
        StyleProfile profile = requireProfile(story, id);
        styleProfileRepository.delete(profile);
    }

    @Transactional
    public Map<String, Object> activateStyleProfile(Story story, UUID id) {
        StyleProfile selected = requireProfile(story, id);
        for (StyleProfile profile : styleProfileRepository.findByStoryOrderByCreatedAtDesc(story)) {
            profile.setActive(profile.getId().equals(selected.getId()));
        }
        return profileResponse(styleProfileRepository.save(selected));
    }

    @Transactional
    public Map<String, Object> analyzeStyle(User user, Map<String, Object> payload) {
        String sampleText = str(payload.get("sampleText"), "");
        if (sampleText.isBlank()) throw new BusinessException("sampleText 不能为空");
        Map<String, Integer> dimensions = analyzeDimensionScores(sampleText);
        Map<String, Object> result = new LinkedHashMap<>();
        result.putAll(dimensions);
        result.put("dimensions", dimensions);
        result.put("rhythm", dimensions.get("pacing") * 10);
        result.put("imagery", dimensions.get("descriptiveness") * 10);
        result.put("dialogueDensity", dimensions.get("dialogue_ratio") * 10);
        result.put("suggestedProfileName", "分析画像-" + Instant.now().toString().substring(11, 19));

        StyleAnalysisJob job = new StyleAnalysisJob();
        job.setUser(user);
        job.setSourceType(str(payload.get("sourceType"), "uploaded_text"));
        job.setSourceReference(str(payload.get("sourceReference"), "inline"));
        job.setStatus("completed");
        job.setResultJson(writeJson(result));
        job.setErrorMessage(null);
        return analysisJobResponse(styleAnalysisJobRepository.save(job));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCharacterVoices(Story story) {
        return characterVoiceRepository.findByStoryOrderByCreatedAtDesc(story).stream()
                .map(this::voiceResponse)
                .toList();
    }

    @Transactional
    public Map<String, Object> createCharacterVoice(Story story, Map<String, Object> payload) {
        CharacterCard card = requireCharacter(story, uuid(payload.get("characterCardId")));
        characterVoiceRepository.findByCharacterCard(card).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该角色已存在声音设定");
        });
        CharacterVoice voice = new CharacterVoice();
        voice.setStory(story);
        voice.setCharacterCard(card);
        applyVoicePayload(voice, payload);
        return voiceResponse(characterVoiceRepository.save(voice));
    }

    @Transactional
    public Map<String, Object> updateCharacterVoice(Story story, UUID id, Map<String, Object> payload) {
        CharacterVoice voice = requireVoice(story, id);
        applyVoicePayload(voice, payload);
        return voiceResponse(characterVoiceRepository.save(voice));
    }

    @Transactional
    public void deleteCharacterVoice(Story story, UUID id) {
        characterVoiceRepository.delete(requireVoice(story, id));
    }

    private void applyProfilePayload(StyleProfile profile, Map<String, Object> payload) {
        profile.setName(str(payload.get("name"), profile.getName() == null ? "默认风格画像" : profile.getName()));
        profile.setProfileType(str(payload.get("profileType"), profile.getProfileType() == null ? "global" : profile.getProfileType()));
        if (payload.containsKey("dimensions")) {
            profile.setDimensionsJson(writeJson(payload.get("dimensions")));
        } else if (profile.getDimensionsJson() == null) {
            profile.setDimensionsJson("{}");
        }
        if (payload.containsKey("sampleText")) {
            profile.setSampleText(str(payload.get("sampleText"), ""));
        } else if (profile.getSampleText() == null) {
            profile.setSampleText("");
        }
        if (payload.containsKey("aiAnalysis")) {
            profile.setAiAnalysisJson(writeJson(payload.get("aiAnalysis")));
        } else if (profile.getAiAnalysisJson() == null) {
            profile.setAiAnalysisJson("{}");
        }
        if (payload.containsKey("sceneOverrides")) {
            profile.replaceSceneOverrides(toSceneOverrides(payload.get("sceneOverrides")));
        }
    }

    private List<StyleProfileSceneOverride> toSceneOverrides(Object raw) {
        if (raw == null) return List.of();
        if (!(raw instanceof List<?> list)) throw new BusinessException("场景风格设置应为数组");
        List<StyleProfileSceneOverride> overrides = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) throw new BusinessException("场景风格设置应为对象");
            StyleProfileSceneOverride override = new StyleProfileSceneOverride();
            override.setSceneType(str(map.get("sceneType"), "custom"));
            override.setOverrideJson(writeJson(item));
            overrides.add(override);
        }
        return overrides;
    }

    private void applyVoicePayload(CharacterVoice voice, Map<String, Object> payload) {
        if (payload.containsKey("speechPattern") || voice.getSpeechPattern() == null) {
            voice.setSpeechPattern(str(payload.get("speechPattern"), ""));
        }
        if (payload.containsKey("vocabularyLevel") || voice.getVocabularyLevel() == null) {
            voice.setVocabularyLevel(str(payload.get("vocabularyLevel"), "neutral"));
        }
        if (payload.containsKey("catchphrases") || voice.getCatchphrasesJson() == null) {
            voice.setCatchphrasesJson(writeJson(Objects.requireNonNullElse(payload.get("catchphrases"), List.of())));
        }
        if (payload.containsKey("emotionalRange") || voice.getEmotionalRangeJson() == null) {
            voice.setEmotionalRangeJson(writeJson(Objects.requireNonNullElse(payload.get("emotionalRange"), List.of())));
        }
        if (payload.containsKey("dialect") || voice.getDialect() == null) {
            voice.setDialect(str(payload.get("dialect"), ""));
        }
        if (payload.containsKey("sampleDialogues") || voice.getSampleDialoguesJson() == null) {
            voice.setSampleDialoguesJson(writeJson(Objects.requireNonNullElse(payload.get("sampleDialogues"), List.of())));
        }
        if (payload.containsKey("aiAnalysis") || voice.getAiAnalysisJson() == null) {
            voice.setAiAnalysisJson(writeJson(payload.getOrDefault("aiAnalysis", Map.of())));
        }
    }

    private StyleProfile requireProfile(Story story, UUID id) {
        return styleProfileRepository.findByStoryAndId(story, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "风格画像不存在"));
    }

    private CharacterVoice requireVoice(Story story, UUID id) {
        return characterVoiceRepository.findByStoryAndId(story, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "角色声音不存在"));
    }

    private CharacterCard requireCharacter(Story story, UUID characterCardId) {
        if (characterCardId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择角色");
        }
        CharacterCard card = characterCardRepository.findByIdWithStoryUser(characterCardId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "角色不存在"));
        if (card.getStory() == null || !Objects.equals(card.getStory().getId(), story.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该角色");
        }
        return card;
    }

    private Map<String, Object> profileResponse(StyleProfile profile) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", profile.getId());
        result.put("storyId", profile.getStory() == null ? null : profile.getStory().getId());
        result.put("userId", profile.getUser() == null ? null : profile.getUser().getId());
        result.put("name", profile.getName());
        result.put("profileType", profile.getProfileType());
        result.put("dimensions", readMap(profile.getDimensionsJson()));
        result.put("sampleText", profile.getSampleText());
        result.put("aiAnalysis", readMap(profile.getAiAnalysisJson()));
        result.put("sceneOverrides", profile.getSceneOverrides().stream().map(this::sceneOverrideResponse).toList());
        result.put("isActive", profile.isActive());
        result.put("createdAt", profile.getCreatedAt());
        result.put("updatedAt", profile.getUpdatedAt());
        return result;
    }

    private Map<String, Object> sceneOverrideResponse(StyleProfileSceneOverride override) {
        Map<String, Object> result = new LinkedHashMap<>(readMap(override.getOverrideJson()));
        result.putIfAbsent("sceneType", override.getSceneType());
        return result;
    }

    private Map<String, Object> voiceResponse(CharacterVoice voice) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", voice.getId());
        result.put("storyId", voice.getStory() == null ? null : voice.getStory().getId());
        result.put("characterCardId", voice.getCharacterCard() == null ? null : voice.getCharacterCard().getId());
        result.put("speechPattern", voice.getSpeechPattern());
        result.put("vocabularyLevel", voice.getVocabularyLevel());
        result.put("catchphrases", readList(voice.getCatchphrasesJson()));
        result.put("emotionalRange", readList(voice.getEmotionalRangeJson()));
        result.put("dialect", voice.getDialect());
        result.put("sampleDialogues", readList(voice.getSampleDialoguesJson()));
        result.put("aiAnalysis", readMap(voice.getAiAnalysisJson()));
        result.put("createdAt", voice.getCreatedAt());
        result.put("updatedAt", voice.getUpdatedAt());
        return result;
    }

    private Map<String, Object> analysisJobResponse(StyleAnalysisJob job) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", job.getId());
        result.put("userId", job.getUser() == null ? null : job.getUser().getId());
        result.put("sourceType", job.getSourceType());
        result.put("sourceReference", job.getSourceReference());
        result.put("status", job.getStatus());
        result.put("result", readMap(job.getResultJson()));
        result.put("errorMessage", job.getErrorMessage());
        result.put("createdAt", job.getCreatedAt());
        result.put("updatedAt", job.getUpdatedAt());
        return result;
    }

    private Map<String, Integer> analyzeDimensionScores(String sampleText) {
        String text = sampleText.trim();
        int length = text.length();
        int punctuation = countChars(text, "，。！？；：");
        int quotes = countChars(text, "“”\"「」");
        int emphasis = countChars(text, "！？!");

        Map<String, Integer> dimensions = new LinkedHashMap<>();
        dimensions.put("formality", clamp(5 + (containsAny(text, "其", "乃", "并非", "故而") ? 1 : 0)));
        dimensions.put("sentence_length", clamp(3 + length / 120));
        dimensions.put("vocabulary_richness", clamp(4 + uniqueCharacterCount(text) / 120));
        dimensions.put("pacing", clamp(8 - dimensions.get("sentence_length") / 2 + (punctuation > 8 ? 1 : 0)));
        dimensions.put("descriptiveness", clamp(4 + countContains(text, "风", "雾", "光", "影", "色", "声") / 2));
        dimensions.put("dialogue_ratio", clamp(3 + quotes * 2));
        dimensions.put("emotional_intensity", clamp(4 + emphasis + countContains(text, "怒", "恨", "痛", "惊", "慌")));
        dimensions.put("rhetoric_frequency", clamp(4 + countContains(text, "仿佛", "像", "宛如", "如同")));
        return dimensions;
    }

    private int clamp(int value) {
        return Math.max(1, Math.min(10, value));
    }

    private int countChars(String source, String chars) {
        int count = 0;
        for (int i = 0; i < source.length(); i++) {
            if (chars.indexOf(source.charAt(i)) >= 0) {
                count++;
            }
        }
        return count;
    }

    private int countContains(String source, String... keywords) {
        int count = 0;
        for (String keyword : keywords) {
            if (source.contains(keyword)) {
                count++;
            }
        }
        return count;
    }

    private boolean containsAny(String source, String... keywords) {
        return countContains(source, keywords) > 0;
    }

    private int uniqueCharacterCount(String source) {
        Set<Character> chars = new HashSet<>();
        for (int i = 0; i < source.length(); i++) {
            chars.add(source.charAt(i));
        }
        return chars.size();
    }

    private UUID uuid(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return value instanceof UUID id ? id : UUID.fromString(value.toString());
        } catch (Exception ignored) {
            return null;
        }
    }

    private String str(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? fallback : text;
    }

    private String writeJson(Object value) {
        return jsonColumnCodec.writeRequired(value == null ? Map.of() : value);
    }

    private Map<String, Object> readMap(String json) {
        return jsonColumnCodec.read(json, new TypeReference<>() {}, Map.of());
    }

    private List<Object> readList(String json) {
        return jsonColumnCodec.read(json, new TypeReference<>() {}, List.of());
    }
}
