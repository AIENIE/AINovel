package com.ainovel.app.style;

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
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;
    private final JsonColumnCodec jsonColumnCodec;

    public StyleService(StyleProfileRepository styleProfileRepository,
                        CharacterVoiceRepository characterVoiceRepository,
                        StyleAnalysisJobRepository styleAnalysisJobRepository,
                        CharacterCardRepository characterCardRepository,
                        ObjectMapper objectMapper,
                        JsonColumnCodec jsonColumnCodec) {
        this.styleProfileRepository = styleProfileRepository;
        this.characterVoiceRepository = characterVoiceRepository;
        this.styleAnalysisJobRepository = styleAnalysisJobRepository;
        this.characterCardRepository = characterCardRepository;
        this.objectMapper = objectMapper;
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
        Map<String, Integer> dimensions = analyzeDimensionScores(sampleText);
        Map<String, Object> result = new LinkedHashMap<>();
        result.putAll(dimensions);
        result.put("dimensions", dimensions);
        result.put("tone", "balanced");
        result.put("rhythm", dimensions.getOrDefault("pacing", 6) * 10);
        result.put("imagery", dimensions.getOrDefault("descriptiveness", 6) * 10);
        result.put("dialogueDensity", dimensions.getOrDefault("dialogue_ratio", 6) * 10);
        result.put("summary", "文本整体偏叙事描写，节奏稳定，建议继续保持情绪推进与场景细节平衡。");
        result.put("suggestedProfileName", "分析画像-" + Instant.now().toString().substring(11, 19));
        result.put("recommendations", List.of(
                "关键冲突段可适度提升情感强度",
                "对话场景可提高对话占比并缩短句长",
                "回忆段落可增加修辞频率与描写密度"
        ));

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

    @Transactional
    public Map<String, Object> generateCharacterVoice(Story story, UUID id, Map<String, Object> payload) {
        CharacterVoice voice = requireVoice(story, id);
        String characterName = payload == null ? "角色" : str(payload.get("characterName"), "角色");
        voice.setSpeechPattern(characterName + "偏好短句、反问句与口语化表达");
        voice.setVocabularyLevel("colloquial");
        voice.setCatchphrasesJson(writeJson(List.of("这事不对劲", "让我想想")));
        voice.setSampleDialoguesJson(writeJson(List.of(characterName + "：先别急，我们再看一眼线索。")));
        voice.setAiAnalysisJson(writeJson(Map.of("source", "auto-generated", "version", "v2")));
        return voiceResponse(characterVoiceRepository.save(voice));
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
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<StyleProfileSceneOverride> overrides = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
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
            voice.setCatchphrasesJson(writeJson(payload.getOrDefault("catchphrases", List.of())));
        }
        if (payload.containsKey("emotionalRange") || voice.getEmotionalRangeJson() == null) {
            voice.setEmotionalRangeJson(writeJson(payload.getOrDefault("emotionalRange", List.of())));
        }
        if (payload.containsKey("dialect") || voice.getDialect() == null) {
            voice.setDialect(str(payload.get("dialect"), ""));
        }
        if (payload.containsKey("sampleDialogues") || voice.getSampleDialoguesJson() == null) {
            voice.setSampleDialoguesJson(writeJson(payload.getOrDefault("sampleDialogues", List.of())));
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
        result.put("dimensions", readJson(profile.getDimensionsJson(), Map.of()));
        result.put("sampleText", profile.getSampleText());
        result.put("aiAnalysis", readJson(profile.getAiAnalysisJson(), Map.of()));
        result.put("sceneOverrides", profile.getSceneOverrides().stream().map(this::sceneOverrideResponse).toList());
        result.put("isActive", profile.isActive());
        result.put("createdAt", profile.getCreatedAt());
        result.put("updatedAt", profile.getUpdatedAt());
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sceneOverrideResponse(StyleProfileSceneOverride override) {
        Object parsed = readJson(override.getOverrideJson(), Map.of());
        if (parsed instanceof Map<?, ?> raw) {
            Map<String, Object> result = new LinkedHashMap<>((Map<String, Object>) raw);
            result.putIfAbsent("sceneType", override.getSceneType());
            return result;
        }
        return new LinkedHashMap<>(Map.of("sceneType", override.getSceneType(), "dimensions", Map.of()));
    }

    private Map<String, Object> voiceResponse(CharacterVoice voice) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", voice.getId());
        result.put("storyId", voice.getStory() == null ? null : voice.getStory().getId());
        result.put("characterCardId", voice.getCharacterCard() == null ? null : voice.getCharacterCard().getId());
        result.put("speechPattern", voice.getSpeechPattern());
        result.put("vocabularyLevel", voice.getVocabularyLevel());
        result.put("catchphrases", readJson(voice.getCatchphrasesJson(), List.of()));
        result.put("emotionalRange", readJson(voice.getEmotionalRangeJson(), List.of()));
        result.put("dialect", voice.getDialect());
        result.put("sampleDialogues", readJson(voice.getSampleDialoguesJson(), List.of()));
        result.put("aiAnalysis", readJson(voice.getAiAnalysisJson(), Map.of()));
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
        result.put("result", readJson(job.getResultJson(), Map.of()));
        result.put("errorMessage", job.getErrorMessage());
        result.put("createdAt", job.getCreatedAt());
        result.put("updatedAt", job.getUpdatedAt());
        return result;
    }

    private Map<String, Integer> analyzeDimensionScores(String sampleText) {
        String text = sampleText == null ? "" : sampleText.trim();
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
        if (source == null || source.isEmpty()) {
            return 0;
        }
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
        if (source == null || source.isEmpty()) {
            return count;
        }
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isEmpty() && source.contains(keyword)) {
                count++;
            }
        }
        return count;
    }

    private boolean containsAny(String source, String... keywords) {
        return countContains(source, keywords) > 0;
    }

    private int uniqueCharacterCount(String source) {
        if (source == null || source.isEmpty()) {
            return 0;
        }
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
        return jsonColumnCodec.write(value == null ? Map.of() : value, "{}");
    }

    private Object readJson(String json, Object fallback) {
        return jsonColumnCodec.read(json, new TypeReference<>() {}, fallback);
    }
}
