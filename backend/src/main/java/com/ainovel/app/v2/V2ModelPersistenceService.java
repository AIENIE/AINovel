package com.ainovel.app.v2;

import com.ainovel.app.common.BusinessException;
import com.ainovel.app.ai.AiModelPolicy;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.story.repo.StoryRepository;
import com.ainovel.app.user.User;
import com.ainovel.app.v2.model.V2ModelRegistry;
import com.ainovel.app.v2.model.V2ModelUsageLog;
import com.ainovel.app.v2.model.V2TaskModelRouting;
import com.ainovel.app.v2.model.V2UserModelPreference;
import com.ainovel.app.v2.repo.V2ModelRegistryRepository;
import com.ainovel.app.v2.repo.V2ModelUsageLogRepository;
import com.ainovel.app.v2.repo.V2TaskModelRoutingRepository;
import com.ainovel.app.v2.repo.V2UserModelPreferenceRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class V2ModelPersistenceService {
    private final V2ModelRegistryRepository modelRepository;
    private final V2TaskModelRoutingRepository routingRepository;
    private final V2UserModelPreferenceRepository preferenceRepository;
    private final V2ModelUsageLogRepository usageRepository;
    private final StoryRepository storyRepository;
    private final V2Json v2Json;

    public V2ModelPersistenceService(V2ModelRegistryRepository modelRepository,
                                     V2TaskModelRoutingRepository routingRepository,
                                     V2UserModelPreferenceRepository preferenceRepository,
                                     V2ModelUsageLogRepository usageRepository,
                                     StoryRepository storyRepository,
                                     V2Json v2Json) {
        this.modelRepository = modelRepository;
        this.routingRepository = routingRepository;
        this.preferenceRepository = preferenceRepository;
        this.usageRepository = usageRepository;
        this.storyRepository = storyRepository;
        this.v2Json = v2Json;
    }

    @Transactional
    public List<Map<String, Object>> listModels() {
        ensureSeeded();
        return modelRepository.findByOrderByPriorityDesc().stream().map(this::modelMap).toList();
    }

    @Transactional
    public Map<String, Object> findModel(String modelKey) {
        ensureSeeded();
        return modelMap(modelRepository.findByModelKey(modelKey).orElseThrow(() -> new BusinessException("模型不存在")));
    }

    @Transactional
    public List<Map<String, Object>> listRouting() {
        ensureSeeded();
        return routingRepository.findAll().stream().map(this::routingMap).toList();
    }

    @Transactional
    public Map<String, Object> updateRouting(String taskType, UUID recommendedModelId, UUID fallbackModelId, String strategy, Object config) {
        ensureSeeded();
        V2TaskModelRouting routing = routingRepository.findByTaskType(taskType).orElseGet(V2TaskModelRouting::new);
        routing.setTaskType(taskType);
        routing.setRecommendedModel(modelRepository.findById(recommendedModelId).orElseThrow(() -> new BusinessException("recommendedModelId 无效")));
        routing.setFallbackModel(fallbackModelId == null ? null : modelRepository.findById(fallbackModelId).orElseThrow(() -> new BusinessException("fallbackModelId 无效")));
        routing.setRoutingStrategy(blank(strategy, "fixed"));
        routing.setConfigJson(v2Json.write(config == null ? Map.of() : config));
        return routingMap(routingRepository.save(routing));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPreferences(User user) {
        return preferenceRepository.findByUserId(user.getId()).stream().map(this::preferenceMap).toList();
    }

    @Transactional
    public Map<String, Object> savePreference(User user, String taskType, UUID preferredModelId) {
        ensureSeeded();
        V2UserModelPreference pref = preferenceRepository.findByUserIdAndTaskType(user.getId(), taskType).orElseGet(V2UserModelPreference::new);
        pref.setUser(user);
        pref.setTaskType(taskType);
        pref.setPreferredModel(preferredModelId == null ? null : modelRepository.findById(preferredModelId).orElseThrow(() -> new BusinessException("preferredModelId 无效")));
        return preferenceMap(preferenceRepository.save(pref));
    }

    @Transactional
    public void resetPreference(User user, String taskType) {
        preferenceRepository.deleteByUserIdAndTaskType(user.getId(), taskType);
    }

    @Transactional
    public void logUsage(User user, UUID storyId, UUID modelId, String taskType, int inputTokens, int outputTokens,
                         int latencyMs, boolean success, String errorMessage) {
        ensureSeeded();
        V2ModelRegistry model = modelRepository.findById(modelId).orElseThrow(() -> new BusinessException("模型不存在"));
        V2ModelUsageLog log = new V2ModelUsageLog();
        log.setUser(user);
        Story story = storyId == null ? null : storyRepository.findById(storyId).orElse(null);
        log.setStory(story);
        log.setModel(model);
        log.setTaskType(taskType);
        log.setInputTokens(inputTokens);
        log.setOutputTokens(outputTokens);
        log.setLatencyMs(latencyMs);
        BigDecimal cost = model.getCostPer1kInput().multiply(BigDecimal.valueOf(inputTokens))
                .add(model.getCostPer1kOutput().multiply(BigDecimal.valueOf(outputTokens)))
                .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
        log.setCostEstimate(cost);
        log.setSuccess(success);
        log.setErrorMessage(errorMessage);
        usageRepository.save(log);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> usageSummary(User user) {
        List<V2ModelUsageLog> logs = usageRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        int input = 0;
        int output = 0;
        BigDecimal cost = BigDecimal.ZERO;
        for (V2ModelUsageLog log : logs) {
            input += log.getInputTokens();
            output += log.getOutputTokens();
            cost = cost.add(log.getCostEstimate());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalCalls", logs.size());
        out.put("totalInputTokens", input);
        out.put("totalOutputTokens", output);
        out.put("totalCost", cost.setScale(6, RoundingMode.HALF_UP));
        return out;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> usageDetails(User user, int limit) {
        return usageRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(0, Math.max(limit, 1)))
                .stream().map(this::usageMap).toList();
    }

    @Transactional
    public void ensureSeeded() {
        V2ModelRegistry model = modelRepository.findByModelKey(AiModelPolicy.REQUIRED_TEXT_MODEL_KEY).orElseGet(() -> {
            V2ModelRegistry created = new V2ModelRegistry();
            created.setModelKey(AiModelPolicy.REQUIRED_TEXT_MODEL_KEY);
            created.setDisplayName(AiModelPolicy.REQUIRED_TEXT_MODEL_DISPLAY_NAME);
            created.setProvider("deepseek");
            created.setCapabilitiesJson(v2Json.write(List.of("chat", "draft_generation", "analysis", "style")));
            created.setMaxContextTokens(64000);
            created.setMaxOutputTokens(8192);
            created.setCostPer1kInput(BigDecimal.valueOf(0.001));
            created.setCostPer1kOutput(BigDecimal.valueOf(0.002));
            created.setSupportsStreaming(true);
            created.setAvailable(true);
            created.setPriority(100);
            return modelRepository.save(created);
        });
        if (routingRepository.findByTaskType("draft_generation").isEmpty()) {
            V2TaskModelRouting routing = new V2TaskModelRouting();
            routing.setTaskType("draft_generation");
            routing.setRecommendedModel(model);
            routing.setFallbackModel(model);
            routing.setRoutingStrategy("fixed");
            routing.setConfigJson("{}");
            routingRepository.save(routing);
        }
    }

    private Map<String, Object> modelMap(V2ModelRegistry model) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", model.getId());
        out.put("modelKey", model.getModelKey());
        out.put("displayName", model.getDisplayName());
        out.put("provider", model.getProvider());
        out.put("capabilities", v2Json.list(model.getCapabilitiesJson()));
        out.put("maxContextTokens", model.getMaxContextTokens());
        out.put("maxOutputTokens", model.getMaxOutputTokens());
        out.put("costPer1kInput", model.getCostPer1kInput());
        out.put("costPer1kOutput", model.getCostPer1kOutput());
        out.put("supportsStreaming", model.isSupportsStreaming());
        out.put("isAvailable", model.isAvailable());
        out.put("priority", model.getPriority());
        out.put("createdAt", model.getCreatedAt());
        out.put("updatedAt", model.getUpdatedAt());
        return out;
    }

    private Map<String, Object> routingMap(V2TaskModelRouting routing) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", routing.getId());
        out.put("taskType", routing.getTaskType());
        out.put("recommendedModelId", routing.getRecommendedModel().getId());
        out.put("fallbackModelId", routing.getFallbackModel() == null ? null : routing.getFallbackModel().getId());
        out.put("routingStrategy", routing.getRoutingStrategy());
        out.put("config", v2Json.map(routing.getConfigJson()));
        out.put("createdAt", routing.getCreatedAt());
        out.put("updatedAt", routing.getUpdatedAt());
        return out;
    }

    private Map<String, Object> preferenceMap(V2UserModelPreference pref) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", pref.getId());
        out.put("userId", pref.getUser().getId());
        out.put("taskType", pref.getTaskType());
        out.put("preferredModelId", pref.getPreferredModel() == null ? null : pref.getPreferredModel().getId());
        out.put("createdAt", pref.getCreatedAt());
        out.put("updatedAt", pref.getUpdatedAt());
        return out;
    }

    private Map<String, Object> usageMap(V2ModelUsageLog log) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", log.getId());
        out.put("userId", log.getUser().getId());
        out.put("storyId", log.getStory() == null ? null : log.getStory().getId());
        out.put("modelId", log.getModel().getId());
        out.put("taskType", log.getTaskType());
        out.put("inputTokens", log.getInputTokens());
        out.put("outputTokens", log.getOutputTokens());
        out.put("latencyMs", log.getLatencyMs());
        out.put("costEstimate", log.getCostEstimate());
        out.put("success", log.isSuccess());
        out.put("errorMessage", log.getErrorMessage());
        out.put("createdAt", log.getCreatedAt());
        return out;
    }

    private static String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
