package com.ainovel.app.v2;

import com.ainovel.app.story.model.Story;
import com.ainovel.app.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/v2")
public class V2ModelController {
    private final V2AccessGuard accessGuard;

    private final ConcurrentMap<String, Map<String, Object>> modelsByKey = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Map<String, Object>> modelsById = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Map<String, Object>> routingByTaskType = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ConcurrentMap<String, Map<String, Object>>> preferencesByUser = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, CopyOnWriteArrayList<Map<String, Object>>> usageByUser = new ConcurrentHashMap<>();

    public V2ModelController(V2AccessGuard accessGuard) {
        this.accessGuard = accessGuard;
        seedModels();
        seedRouting();
    }

    @GetMapping("/models")
    public List<Map<String, Object>> listModels(@AuthenticationPrincipal UserDetails principal) {
        accessGuard.currentUser(principal);
        List<Map<String, Object>> list = new ArrayList<>(modelsByKey.values());
        list.sort(Comparator.comparingInt(model -> -intVal(model.get("priority"), 0)));
        return list;
    }

    @GetMapping("/models/{modelKey}")
    public Map<String, Object> getModel(@AuthenticationPrincipal UserDetails principal,
                                        @PathVariable String modelKey) {
        accessGuard.currentUser(principal);
        Map<String, Object> model = modelsByKey.get(modelKey);
        if (model == null) {
            throw new RuntimeException("模型不存在");
        }
        return model;
    }

    @GetMapping("/admin/model-routing")
    public List<Map<String, Object>> listRouting(@AuthenticationPrincipal UserDetails principal) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireAdmin(user);
        return new ArrayList<>(routingByTaskType.values());
    }

    @PutMapping("/admin/model-routing/{taskType}")
    public Map<String, Object> updateRouting(@AuthenticationPrincipal UserDetails principal,
                                             @PathVariable String taskType,
                                             @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireAdmin(user);

        UUID recommendedId = uuid(payload.get("recommendedModelId"));
        if (recommendedId == null || !modelsById.containsKey(recommendedId)) {
            throw new RuntimeException("recommendedModelId 无效");
        }
        UUID fallbackId = uuid(payload.get("fallbackModelId"));
        if (fallbackId != null && !modelsById.containsKey(fallbackId)) {
            throw new RuntimeException("fallbackModelId 无效");
        }

        Map<String, Object> rule = routingByTaskType.computeIfAbsent(taskType, key -> new HashMap<>());
        rule.put("id", rule.getOrDefault("id", UUID.randomUUID()));
        rule.put("taskType", taskType);
        rule.put("recommendedModelId", recommendedId);
        rule.put("fallbackModelId", fallbackId);
        rule.put("routingStrategy", str(payload.get("routingStrategy"), "fixed"));
        rule.put("config", payload.getOrDefault("config", Map.of()));
        rule.put("updatedAt", Instant.now());
        return rule;
    }

    @GetMapping("/users/me/model-preferences")
    public List<Map<String, Object>> listPreferences(@AuthenticationPrincipal UserDetails principal) {
        User user = accessGuard.currentUser(principal);
        return new ArrayList<>(preferencesByUser.computeIfAbsent(user.getId(), uid -> new ConcurrentHashMap<>()).values());
    }

    @PutMapping("/users/me/model-preferences/{taskType}")
    public Map<String, Object> putPreference(@AuthenticationPrincipal UserDetails principal,
                                             @PathVariable String taskType,
                                             @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        UUID preferredModelId = uuid(payload.get("preferredModelId"));
        if (preferredModelId != null && !modelsById.containsKey(preferredModelId)) {
            throw new RuntimeException("preferredModelId 无效");
        }

        Map<String, Object> pref = new HashMap<>();
        pref.put("id", UUID.randomUUID());
        pref.put("userId", user.getId());
        pref.put("taskType", taskType);
        pref.put("preferredModelId", preferredModelId);
        pref.put("createdAt", Instant.now());
        pref.put("updatedAt", Instant.now());

        preferencesByUser.computeIfAbsent(user.getId(), uid -> new ConcurrentHashMap<>()).put(taskType, pref);
        return pref;
    }

    @DeleteMapping("/users/me/model-preferences/{taskType}")
    public ResponseEntity<Void> resetPreference(@AuthenticationPrincipal UserDetails principal,
                                                @PathVariable String taskType) {
        User user = accessGuard.currentUser(principal);
        preferencesByUser.computeIfAbsent(user.getId(), uid -> new ConcurrentHashMap<>()).remove(taskType);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/me/model-usage")
    public Map<String, Object> usageSummary(@AuthenticationPrincipal UserDetails principal) {
        User user = accessGuard.currentUser(principal);
        List<Map<String, Object>> logs = usageByUser.computeIfAbsent(user.getId(), uid -> new CopyOnWriteArrayList<>());
        int inputTokens = 0;
        int outputTokens = 0;
        BigDecimal cost = BigDecimal.ZERO;
        for (Map<String, Object> log : logs) {
            inputTokens += intVal(log.get("inputTokens"), 0);
            outputTokens += intVal(log.get("outputTokens"), 0);
            cost = cost.add(decimal(log.get("costEstimate")));
        }
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalCalls", logs.size());
        summary.put("totalInputTokens", inputTokens);
        summary.put("totalOutputTokens", outputTokens);
        summary.put("totalCost", cost.setScale(6, RoundingMode.HALF_UP));
        return summary;
    }

    @GetMapping("/users/me/model-usage/details")
    public List<Map<String, Object>> usageDetails(@AuthenticationPrincipal UserDetails principal,
                                                  @RequestParam(required = false, defaultValue = "100") int limit) {
        User user = accessGuard.currentUser(principal);
        List<Map<String, Object>> logs = new ArrayList<>(usageByUser.computeIfAbsent(user.getId(), uid -> new CopyOnWriteArrayList<>()));
        logs.sort((a, b) -> ((Instant) b.get("createdAt")).compareTo((Instant) a.get("createdAt")));
        return logs.subList(0, Math.min(Math.max(limit, 1), logs.size()));
    }

    @PostMapping("/stories/{storyId}/compare-models")
    public Map<String, Object> compareModels(@AuthenticationPrincipal UserDetails principal,
                                             @PathVariable UUID storyId,
                                             @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        Story story = accessGuard.requireOwnedStory(storyId, user);

        Map<String, Object> routing = routingByTaskType.getOrDefault(str(payload.get("taskType"), "draft_generation"), Map.of());
        UUID defaultModelId = uuid(routing.get("recommendedModelId"));
        UUID modelAId = uuid(payload.get("modelAId"));
        UUID modelBId = uuid(payload.get("modelBId"));
        if (modelAId == null) {
            modelAId = defaultModelId;
        }
        if (modelBId == null) {
            modelBId = uuid(routing.get("fallbackModelId"));
        }
        if (modelAId == null || modelBId == null) {
            throw new RuntimeException("缺少可用模型");
        }

        Map<String, Object> modelA = modelsById.get(modelAId);
        Map<String, Object> modelB = modelsById.get(modelBId);
        if (modelA == null || modelB == null) {
            throw new RuntimeException("模型不存在");
        }

        String prompt = str(payload.get("prompt"), "请生成一段小说正文");
        Map<String, Object> outputA = buildComparisonOutput(story, modelA, prompt, "A");
        Map<String, Object> outputB = buildComparisonOutput(story, modelB, prompt, "B");

        logUsage(user.getId(), story.getId(), modelA, str(payload.get("taskType"), "draft_generation"), 220, 340, 580);
        logUsage(user.getId(), story.getId(), modelB, str(payload.get("taskType"), "draft_generation"), 210, 330, 560);

        return Map.of(
                "storyId", storyId,
                "taskType", str(payload.get("taskType"), "draft_generation"),
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

    private void logUsage(UUID userId,
                          UUID storyId,
                          Map<String, Object> model,
                          String taskType,
                          int inputTokens,
                          int outputTokens,
                          int latencyMs) {
        BigDecimal inputCost = decimal(model.get("costPer1kInput")).multiply(BigDecimal.valueOf(inputTokens)).divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
        BigDecimal outputCost = decimal(model.get("costPer1kOutput")).multiply(BigDecimal.valueOf(outputTokens)).divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
        Map<String, Object> usage = new HashMap<>();
        usage.put("id", UUID.randomUUID());
        usage.put("userId", userId);
        usage.put("storyId", storyId);
        usage.put("modelId", model.get("id"));
        usage.put("taskType", taskType);
        usage.put("inputTokens", inputTokens);
        usage.put("outputTokens", outputTokens);
        usage.put("latencyMs", latencyMs);
        usage.put("costEstimate", inputCost.add(outputCost));
        usage.put("success", true);
        usage.put("errorMessage", null);
        usage.put("createdAt", Instant.now());
        usageByUser.computeIfAbsent(userId, uid -> new CopyOnWriteArrayList<>()).add(usage);
    }

    private void seedModels() {
        registerModel("gpt-4o", "GPT-4o", "openai", List.of("chat", "draft_generation", "analysis"), 128000, 16384, 0.005, 0.015, true, true, 100);
        registerModel("deepseek-chat", "DeepSeek Chat", "deepseek", List.of("chat", "analysis"), 64000, 8192, 0.001, 0.002, true, true, 80);
        registerModel("claude-sonnet", "Claude Sonnet", "anthropic", List.of("chat", "style", "analysis"), 200000, 8192, 0.003, 0.015, true, true, 90);
    }

    private void registerModel(String modelKey,
                               String displayName,
                               String provider,
                               List<String> capabilities,
                               int maxContext,
                               int maxOutput,
                               double costIn,
                               double costOut,
                               boolean streaming,
                               boolean available,
                               int priority) {
        UUID id = UUID.randomUUID();
        Map<String, Object> model = new HashMap<>();
        model.put("id", id);
        model.put("modelKey", modelKey);
        model.put("displayName", displayName);
        model.put("provider", provider);
        model.put("capabilities", capabilities);
        model.put("maxContextTokens", maxContext);
        model.put("maxOutputTokens", maxOutput);
        model.put("costPer1kInput", BigDecimal.valueOf(costIn));
        model.put("costPer1kOutput", BigDecimal.valueOf(costOut));
        model.put("supportsStreaming", streaming);
        model.put("isAvailable", available);
        model.put("priority", priority);
        model.put("createdAt", Instant.now());
        model.put("updatedAt", Instant.now());

        modelsByKey.put(modelKey, model);
        modelsById.put(id, model);
    }

    private void seedRouting() {
        UUID recommended = uuid(findModelByKey("gpt-4o").get("id"));
        UUID fallback = uuid(findModelByKey("deepseek-chat").get("id"));
        Map<String, Object> routing = new HashMap<>();
        routing.put("id", UUID.randomUUID());
        routing.put("taskType", "draft_generation");
        routing.put("recommendedModelId", recommended);
        routing.put("fallbackModelId", fallback);
        routing.put("routingStrategy", "fixed");
        routing.put("config", Map.of());
        routing.put("createdAt", Instant.now());
        routing.put("updatedAt", Instant.now());
        routingByTaskType.put("draft_generation", routing);
    }

    private Map<String, Object> findModelByKey(String key) {
        Map<String, Object> model = modelsByKey.get(key);
        if (model == null) {
            throw new RuntimeException("模型不存在: " + key);
        }
        return model;
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

    private int intVal(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private BigDecimal decimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal b) {
            return b;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
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
