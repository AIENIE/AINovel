package com.ainovel.app.v2;

import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@Tag(name = "V2", description = "AINovel v2 and quality APIs")
@RestController
@RequestMapping("/v2")
public class V2ModelController {
    private final ResourceAccessGuard accessGuard;
    private final V2ModelPersistenceService persistenceService;

    @Autowired
    public V2ModelController(ResourceAccessGuard accessGuard, V2ModelPersistenceService persistenceService) {
        this.accessGuard = accessGuard;
        this.persistenceService = persistenceService;
    }

    @Operation(summary = "v2 API endpoint")

    @GetMapping("/models")
    public List<Map<String, Object>> listModels(@AuthenticationPrincipal UserDetails principal) {
        accessGuard.currentUser(principal);
        return persistenceService.listModels();
    }

    @Operation(summary = "v2 API endpoint")

    @GetMapping("/models/{modelKey}")
    public Map<String, Object> getModel(@AuthenticationPrincipal UserDetails principal,
                                        @PathVariable String modelKey) {
        accessGuard.currentUser(principal);
        return persistenceService.findModel(modelKey);
    }

    @Operation(summary = "v2 API endpoint")

    @GetMapping("/admin/model-routing")
    public List<Map<String, Object>> listRouting(@AuthenticationPrincipal UserDetails principal) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireAdmin(user);
        return persistenceService.listRouting();
    }

    @Operation(summary = "v2 API endpoint")

    @PutMapping("/admin/model-routing/{taskType}")
    public Map<String, Object> updateRouting(@AuthenticationPrincipal UserDetails principal,
                                             @PathVariable String taskType,
                                             @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireAdmin(user);

        return persistenceService.updateRouting(
                taskType,
                uuid(payload.get("recommendedModelId")),
                uuid(payload.get("fallbackModelId")),
                str(payload.get("routingStrategy"), "fixed"),
                payload.getOrDefault("config", Map.of())
        );
    }

    @Operation(summary = "v2 API endpoint")

    @GetMapping("/users/me/model-preferences")
    public List<Map<String, Object>> listPreferences(@AuthenticationPrincipal UserDetails principal) {
        User user = accessGuard.currentUser(principal);
        return persistenceService.listPreferences(user);
    }

    @Operation(summary = "v2 API endpoint")

    @PutMapping("/users/me/model-preferences/{taskType}")
    public Map<String, Object> putPreference(@AuthenticationPrincipal UserDetails principal,
                                             @PathVariable String taskType,
                                             @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        return persistenceService.savePreference(user, taskType, uuid(payload.get("preferredModelId")));
    }

    @Operation(summary = "v2 API endpoint")

    @DeleteMapping("/users/me/model-preferences/{taskType}")
    public ResponseEntity<Void> resetPreference(@AuthenticationPrincipal UserDetails principal,
                                                @PathVariable String taskType) {
        User user = accessGuard.currentUser(principal);
        persistenceService.resetPreference(user, taskType);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "v2 API endpoint")

    @GetMapping("/users/me/model-usage")
    public Map<String, Object> usageSummary(@AuthenticationPrincipal UserDetails principal) {
        User user = accessGuard.currentUser(principal);
        return persistenceService.usageSummary(user);
    }

    @Operation(summary = "v2 API endpoint")

    @GetMapping("/users/me/model-usage/details")
    public List<Map<String, Object>> usageDetails(@AuthenticationPrincipal UserDetails principal,
                                                  @RequestParam(required = false, defaultValue = "100") int limit) {
        User user = accessGuard.currentUser(principal);
        return persistenceService.usageDetails(user, limit);
    }

    @Operation(summary = "v2 API endpoint")

    @PostMapping("/stories/{storyId}/compare-models")
    public Map<String, Object> compareModels(@AuthenticationPrincipal UserDetails principal,
                                             @PathVariable UUID storyId,
                                             @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        Story story = accessGuard.requireOwnedStory(storyId, user);

        List<Map<String, Object>> models = persistenceService.listModels();
        if (models.isEmpty()) {
            throw new RuntimeException("缺少可用模型");
        }
        Map<String, Object> modelA = models.get(0);
        Map<String, Object> modelB = models.size() > 1 ? models.get(1) : models.get(0);
        UUID modelAId = uuid(payload.get("modelAId"));
        UUID modelBId = uuid(payload.get("modelBId"));
        if (modelAId != null) {
            modelA = models.stream().filter(model -> modelAId.equals(model.get("id"))).findFirst()
                    .orElseThrow(() -> new RuntimeException("模型不存在"));
        }
        if (modelBId != null) {
            modelB = models.stream().filter(model -> modelBId.equals(model.get("id"))).findFirst()
                    .orElseThrow(() -> new RuntimeException("模型不存在"));
        }

        String taskType = str(payload.get("taskType"), "draft_generation");
        String prompt = str(payload.get("prompt"), "请生成一段小说正文");
        Map<String, Object> outputA = buildComparisonOutput(story, modelA, prompt, "A");
        Map<String, Object> outputB = buildComparisonOutput(story, modelB, prompt, "B");
        persistenceService.logUsage(user, story.getId(), (UUID) modelA.get("id"), taskType, 220, 340, 580, true, null);
        persistenceService.logUsage(user, story.getId(), (UUID) modelB.get("id"), taskType, 210, 330, 560, true, null);

        return Map.of(
                "storyId", storyId,
                "taskType", taskType,
                "prompt", prompt,
                "candidates", List.of(outputA, outputB),
                "generatedAt", Instant.now()
            );
    }

    private Map<String, Object> buildComparisonOutput(Story story,
                                                      Map<String, Object> model,
                                                      String prompt,
                                                      String slot) {
        return Map.of(
                "slot", slot,
                "modelId", model.get("id"),
                "modelKey", model.get("modelKey"),
                "text", "[" + model.get("displayName") + "] 针对《" + story.getTitle() + "》生成结果：" + prompt,
                "score", 80 + ("A".equals(slot) ? 3 : 1)
        );
    }

    private UUID uuid(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID id) {
            return id;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException ex) {
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
}
