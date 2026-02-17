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
        Map<String, Object> result = new HashMap<>();
        result.put("tone", "balanced");
        result.put("rhythm", 78);
        result.put("imagery", 82);
        result.put("dialogueDensity", 64);

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
}
