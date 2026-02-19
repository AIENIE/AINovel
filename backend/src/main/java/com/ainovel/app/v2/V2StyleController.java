package com.ainovel.app.v2;

import com.ainovel.app.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@RestController
@RequestMapping("/v2")
public class V2StyleController {
    private final V2AccessGuard accessGuard;

    private final ConcurrentMap<UUID, ConcurrentMap<UUID, Map<String, Object>>> profileByStory = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ConcurrentMap<UUID, Map<String, Object>>> voiceByStory = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Map<String, Object>> analysisJobs = new ConcurrentHashMap<>();

    public V2StyleController(V2AccessGuard accessGuard) {
        this.accessGuard = accessGuard;
    }

    @GetMapping("/stories/{storyId}/style-profiles")
    public List<Map<String, Object>> listStyleProfiles(@AuthenticationPrincipal UserDetails principal,
                                                       @PathVariable UUID storyId) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);
        return listByStory(profileByStory, storyId);
    }

    @PostMapping("/stories/{storyId}/style-profiles")
    public Map<String, Object> createStyleProfile(@AuthenticationPrincipal UserDetails principal,
                                                  @PathVariable UUID storyId,
                                                  @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);

        UUID id = UUID.randomUUID();
        Map<String, Object> profile = new HashMap<>();
        profile.put("id", id);
        profile.put("storyId", storyId);
        profile.put("userId", user.getId());
        profile.put("name", str(payload.get("name"), "默认风格画像"));
        profile.put("profileType", str(payload.get("profileType"), "global"));
        profile.put("dimensions", payload.getOrDefault("dimensions", Map.of()));
        profile.put("sampleText", str(payload.get("sampleText"), ""));
        profile.put("aiAnalysis", payload.getOrDefault("aiAnalysis", Map.of()));
        profile.put("sceneOverrides", payload.getOrDefault("sceneOverrides", List.of()));
        profile.put("isActive", false);
        profile.put("createdAt", Instant.now());
        profile.put("updatedAt", Instant.now());

        profileByStory.computeIfAbsent(storyId, key -> new ConcurrentHashMap<>()).put(id, profile);
        return profile;
    }

    @PutMapping("/stories/{storyId}/style-profiles/{id}")
    public Map<String, Object> updateStyleProfile(@AuthenticationPrincipal UserDetails principal,
                                                  @PathVariable UUID storyId,
                                                  @PathVariable UUID id,
                                                  @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);
        Map<String, Object> profile = requireById(profileByStory, storyId, id, "风格画像不存在");
        mergeIfPresent(payload, profile, "name", "profileType", "sampleText");
        if (payload.containsKey("dimensions")) {
            profile.put("dimensions", payload.get("dimensions"));
        }
        if (payload.containsKey("aiAnalysis")) {
            profile.put("aiAnalysis", payload.get("aiAnalysis"));
        }
        if (payload.containsKey("sceneOverrides")) {
            profile.put("sceneOverrides", payload.get("sceneOverrides"));
        }
        profile.put("updatedAt", Instant.now());
        return profile;
    }

    @DeleteMapping("/stories/{storyId}/style-profiles/{id}")
    public ResponseEntity<Void> deleteStyleProfile(@AuthenticationPrincipal UserDetails principal,
                                                   @PathVariable UUID storyId,
                                                   @PathVariable UUID id) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);
        profileByStory.computeIfAbsent(storyId, key -> new ConcurrentHashMap<>()).remove(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/stories/{storyId}/style-profiles/{id}/activate")
    public Map<String, Object> activateStyleProfile(@AuthenticationPrincipal UserDetails principal,
                                                    @PathVariable UUID storyId,
                                                    @PathVariable UUID id) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);
        ConcurrentMap<UUID, Map<String, Object>> profiles = profileByStory.computeIfAbsent(storyId, key -> new ConcurrentHashMap<>());
        Map<String, Object> selected = profiles.get(id);
        if (selected == null) {
            throw new RuntimeException("风格画像不存在");
        }
        for (Map<String, Object> profile : profiles.values()) {
            profile.put("isActive", false);
            profile.put("updatedAt", Instant.now());
        }
        selected.put("isActive", true);
        selected.put("updatedAt", Instant.now());
        return selected;
    }

    @PostMapping("/style-analysis")
    public Map<String, Object> analyzeStyle(@AuthenticationPrincipal UserDetails principal,
                                            @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        UUID jobId = UUID.randomUUID();
        String sampleText = str(payload.get("sampleText"), "");
        Map<String, Integer> dimensions = analyzeDimensionScores(sampleText);

        Map<String, Object> result = new HashMap<>();
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

        Map<String, Object> job = new HashMap<>();
        job.put("id", jobId);
        job.put("userId", user.getId());
        job.put("sourceType", str(payload.get("sourceType"), "uploaded_text"));
        job.put("sourceReference", str(payload.get("sourceReference"), "inline"));
        job.put("status", "completed");
        job.put("result", result);
        job.put("errorMessage", null);
        job.put("createdAt", Instant.now());
        job.put("updatedAt", Instant.now());
        analysisJobs.put(jobId, job);

        return job;
    }

    @GetMapping("/stories/{storyId}/character-voices")
    public List<Map<String, Object>> listCharacterVoices(@AuthenticationPrincipal UserDetails principal,
                                                         @PathVariable UUID storyId) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);
        return listByStory(voiceByStory, storyId);
    }

    @PostMapping("/stories/{storyId}/character-voices")
    public Map<String, Object> createCharacterVoice(@AuthenticationPrincipal UserDetails principal,
                                                    @PathVariable UUID storyId,
                                                    @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);

        UUID id = UUID.randomUUID();
        Map<String, Object> voice = new HashMap<>();
        voice.put("id", id);
        voice.put("storyId", storyId);
        voice.put("characterCardId", payload.get("characterCardId"));
        voice.put("speechPattern", str(payload.get("speechPattern"), ""));
        voice.put("vocabularyLevel", str(payload.get("vocabularyLevel"), "neutral"));
        voice.put("catchphrases", payload.getOrDefault("catchphrases", List.of()));
        voice.put("emotionalRange", payload.getOrDefault("emotionalRange", List.of()));
        voice.put("dialect", str(payload.get("dialect"), ""));
        voice.put("sampleDialogues", payload.getOrDefault("sampleDialogues", List.of()));
        voice.put("aiAnalysis", payload.getOrDefault("aiAnalysis", Map.of()));
        voice.put("createdAt", Instant.now());
        voice.put("updatedAt", Instant.now());

        voiceByStory.computeIfAbsent(storyId, key -> new ConcurrentHashMap<>()).put(id, voice);
        return voice;
    }

    @PutMapping("/stories/{storyId}/character-voices/{id}")
    public Map<String, Object> updateCharacterVoice(@AuthenticationPrincipal UserDetails principal,
                                                    @PathVariable UUID storyId,
                                                    @PathVariable UUID id,
                                                    @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);
        Map<String, Object> voice = requireById(voiceByStory, storyId, id, "角色声音不存在");
        mergeIfPresent(payload, voice, "speechPattern", "vocabularyLevel", "dialect");
        if (payload.containsKey("catchphrases")) {
            voice.put("catchphrases", payload.get("catchphrases"));
        }
        if (payload.containsKey("emotionalRange")) {
            voice.put("emotionalRange", payload.get("emotionalRange"));
        }
        if (payload.containsKey("sampleDialogues")) {
            voice.put("sampleDialogues", payload.get("sampleDialogues"));
        }
        if (payload.containsKey("aiAnalysis")) {
            voice.put("aiAnalysis", payload.get("aiAnalysis"));
        }
        voice.put("updatedAt", Instant.now());
        return voice;
    }

    @DeleteMapping("/stories/{storyId}/character-voices/{id}")
    public ResponseEntity<Void> deleteCharacterVoice(@AuthenticationPrincipal UserDetails principal,
                                                     @PathVariable UUID storyId,
                                                     @PathVariable UUID id) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);
        voiceByStory.computeIfAbsent(storyId, key -> new ConcurrentHashMap<>()).remove(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/stories/{storyId}/character-voices/{id}/generate")
    public Map<String, Object> generateCharacterVoice(@AuthenticationPrincipal UserDetails principal,
                                                      @PathVariable UUID storyId,
                                                      @PathVariable UUID id,
                                                      @RequestBody(required = false) Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);
        Map<String, Object> voice = requireById(voiceByStory, storyId, id, "角色声音不存在");
        String characterName = payload == null ? "角色" : str(payload.get("characterName"), "角色");
        voice.put("speechPattern", characterName + "偏好短句、反问句与口语化表达");
        voice.put("vocabularyLevel", "colloquial");
        voice.put("catchphrases", List.of("这事不对劲", "让我想想"));
        voice.put("sampleDialogues", List.of(characterName + "：先别急，我们再看一眼线索。"));
        voice.put("aiAnalysis", Map.of("source", "auto-generated", "version", "v2"));
        voice.put("updatedAt", Instant.now());
        return voice;
    }

    private List<Map<String, Object>> listByStory(ConcurrentMap<UUID, ConcurrentMap<UUID, Map<String, Object>>> store,
                                                  UUID storyId) {
        return new ArrayList<>(store.computeIfAbsent(storyId, key -> new ConcurrentHashMap<>()).values());
    }

    private Map<String, Object> requireById(ConcurrentMap<UUID, ConcurrentMap<UUID, Map<String, Object>>> store,
                                            UUID storyId,
                                            UUID id,
                                            String errorMessage) {
        Map<String, Object> value = store.computeIfAbsent(storyId, key -> new ConcurrentHashMap<>()).get(id);
        if (value == null) {
            throw new RuntimeException(errorMessage);
        }
        return value;
    }

    private void mergeIfPresent(Map<String, Object> source, Map<String, Object> target, String... keys) {
        for (String key : keys) {
            if (source.containsKey(key)) {
                target.put(key, source.get(key));
            }
        }
    }

    private String str(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? fallback : text;
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
}
