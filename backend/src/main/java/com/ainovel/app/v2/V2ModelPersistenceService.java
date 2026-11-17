package com.ainovel.app.v2;

import com.ainovel.app.common.BusinessException;
import com.ainovel.app.user.User;
import com.ainovel.app.v2.model.V2ModelRegistry;
import com.ainovel.app.v2.repo.V2ModelRegistryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class V2ModelPersistenceService {
    @org.springframework.beans.factory.annotation.Autowired
    private com.ainovel.app.modelconfig.AiModelConfigurationService modelConfiguration;
    private final V2ModelRegistryRepository modelRepository;
    private final V2Json v2Json;

    public V2ModelPersistenceService(V2ModelRegistryRepository modelRepository,
                                     V2Json v2Json) {
        this.modelRepository = modelRepository;
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

    @Transactional(readOnly = true)
    public Map<String, Object> usageSummary(User user) {
        return modelConfiguration.usageSummary(user);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> usageDetails(User user, int limit) {
        return modelConfiguration.usageDetails(user,limit);
    }

    @Transactional
    public void ensureSeeded() {
        modelConfiguration.ensureInitialized();
    }

    private Map<String, Object> modelMap(V2ModelRegistry model) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", model.getId());
        out.put("modelKey", model.getModelKey());
        out.put("displayName", model.getDisplayName());
        out.put("provider", model.getProvider());
        out.put("capabilities", v2Json.modelCapabilities(model.getCapabilitiesJson()));
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
}
