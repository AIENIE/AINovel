package com.ainovel.app.v2;

import com.ainovel.app.story.model.Story;
import com.ainovel.app.style.StyleService;
import com.ainovel.app.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "V2", description = "AINovel v2 and quality APIs")
@RestController
@RequestMapping("/v2")
public class V2StyleController {
    private final V2AccessGuard accessGuard;
    private final StyleService styleService;

    public V2StyleController(V2AccessGuard accessGuard, StyleService styleService) {
        this.accessGuard = accessGuard;
        this.styleService = styleService;
    }

    @Operation(summary = "v2 API endpoint")
    @GetMapping("/stories/{storyId}/style-profiles")
    public List<Map<String, Object>> listStyleProfiles(@AuthenticationPrincipal UserDetails principal,
                                                       @PathVariable UUID storyId) {
        User user = accessGuard.currentUser(principal);
        Story story = accessGuard.requireOwnedStory(storyId, user);
        return styleService.listStyleProfiles(story);
    }

    @Operation(summary = "v2 API endpoint")
    @PostMapping("/stories/{storyId}/style-profiles")
    public Map<String, Object> createStyleProfile(@AuthenticationPrincipal UserDetails principal,
                                                  @PathVariable UUID storyId,
                                                  @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        Story story = accessGuard.requireOwnedStory(storyId, user);
        return styleService.createStyleProfile(user, story, payload);
    }

    @Operation(summary = "v2 API endpoint")
    @PutMapping("/stories/{storyId}/style-profiles/{id}")
    public Map<String, Object> updateStyleProfile(@AuthenticationPrincipal UserDetails principal,
                                                  @PathVariable UUID storyId,
                                                  @PathVariable UUID id,
                                                  @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        Story story = accessGuard.requireOwnedStory(storyId, user);
        return styleService.updateStyleProfile(story, id, payload);
    }

    @Operation(summary = "v2 API endpoint")
    @DeleteMapping("/stories/{storyId}/style-profiles/{id}")
    public ResponseEntity<Void> deleteStyleProfile(@AuthenticationPrincipal UserDetails principal,
                                                   @PathVariable UUID storyId,
                                                   @PathVariable UUID id) {
        User user = accessGuard.currentUser(principal);
        Story story = accessGuard.requireOwnedStory(storyId, user);
        styleService.deleteStyleProfile(story, id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "v2 API endpoint")
    @PostMapping("/stories/{storyId}/style-profiles/{id}/activate")
    public Map<String, Object> activateStyleProfile(@AuthenticationPrincipal UserDetails principal,
                                                    @PathVariable UUID storyId,
                                                    @PathVariable UUID id) {
        User user = accessGuard.currentUser(principal);
        Story story = accessGuard.requireOwnedStory(storyId, user);
        return styleService.activateStyleProfile(story, id);
    }

    @Operation(summary = "v2 API endpoint")
    @PostMapping("/style-analysis")
    public Map<String, Object> analyzeStyle(@AuthenticationPrincipal UserDetails principal,
                                            @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        return styleService.analyzeStyle(user, payload);
    }

    @Operation(summary = "v2 API endpoint")
    @GetMapping("/stories/{storyId}/character-voices")
    public List<Map<String, Object>> listCharacterVoices(@AuthenticationPrincipal UserDetails principal,
                                                         @PathVariable UUID storyId) {
        User user = accessGuard.currentUser(principal);
        Story story = accessGuard.requireOwnedStory(storyId, user);
        return styleService.listCharacterVoices(story);
    }

    @Operation(summary = "v2 API endpoint")
    @PostMapping("/stories/{storyId}/character-voices")
    public Map<String, Object> createCharacterVoice(@AuthenticationPrincipal UserDetails principal,
                                                    @PathVariable UUID storyId,
                                                    @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        Story story = accessGuard.requireOwnedStory(storyId, user);
        return styleService.createCharacterVoice(story, payload);
    }

    @Operation(summary = "v2 API endpoint")
    @PutMapping("/stories/{storyId}/character-voices/{id}")
    public Map<String, Object> updateCharacterVoice(@AuthenticationPrincipal UserDetails principal,
                                                    @PathVariable UUID storyId,
                                                    @PathVariable UUID id,
                                                    @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        Story story = accessGuard.requireOwnedStory(storyId, user);
        return styleService.updateCharacterVoice(story, id, payload);
    }

    @Operation(summary = "v2 API endpoint")
    @DeleteMapping("/stories/{storyId}/character-voices/{id}")
    public ResponseEntity<Void> deleteCharacterVoice(@AuthenticationPrincipal UserDetails principal,
                                                     @PathVariable UUID storyId,
                                                     @PathVariable UUID id) {
        User user = accessGuard.currentUser(principal);
        Story story = accessGuard.requireOwnedStory(storyId, user);
        styleService.deleteCharacterVoice(story, id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "v2 API endpoint")
    @PostMapping("/stories/{storyId}/character-voices/{id}/generate")
    public Map<String, Object> generateCharacterVoice(@AuthenticationPrincipal UserDetails principal,
                                                      @PathVariable UUID storyId,
                                                      @PathVariable UUID id,
                                                      @RequestBody(required = false) Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        Story story = accessGuard.requireOwnedStory(storyId, user);
        return styleService.generateCharacterVoice(story, id, payload);
    }
}
