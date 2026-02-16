package com.ainovel.app.settings;

import com.ainovel.app.settings.dto.*;
import com.ainovel.app.settings.model.PromptTemplatesEntity;
import com.ainovel.app.settings.model.GlobalSettings;
import com.ainovel.app.settings.model.WorldPromptTemplatesEntity;
import com.ainovel.app.settings.repo.PromptTemplatesRepository;
import com.ainovel.app.settings.repo.GlobalSettingsRepository;
import com.ainovel.app.settings.repo.WorldPromptTemplatesRepository;
import com.ainovel.app.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class SettingsService {
    @Value("${app.ai.base-url:https://api.openai.com/v1}")
    private String defaultAiBaseUrl;
    @Value("${app.ai.model:gpt-4o}")
    private String defaultAiModel;
    @Value("${app.ai.api-key:}")
    private String defaultAiApiKey;
    @Value("${spring.mail.host:}")
    private String defaultSmtpHost;
    @Value("${spring.mail.port:587}")
    private Integer defaultSmtpPort;
    @Value("${spring.mail.username:}")
    private String defaultSmtpUsername;
    @Value("${spring.mail.password:}")
    private String defaultSmtpPassword;
    @Autowired
    private GlobalSettingsRepository globalSettingsRepository;
    @Autowired
    private PromptTemplatesRepository promptTemplatesRepository;
    @Autowired
    private WorldPromptTemplatesRepository worldPromptTemplatesRepository;

    public GlobalSettings getGlobalSettings() {
        return getOrCreateGlobalSettings();
    }

    private GlobalSettings getOrCreateGlobalSettings() {
        return globalSettingsRepository.findTopByOrderByUpdatedAtDesc().orElseGet(() -> {
            GlobalSettings g = new GlobalSettings();
            if (defaultSmtpHost != null && !defaultSmtpHost.isBlank()) g.setSmtpHost(defaultSmtpHost);
            if (defaultSmtpPort != null) g.setSmtpPort(defaultSmtpPort);
            if (defaultSmtpUsername != null && !defaultSmtpUsername.isBlank()) g.setSmtpUsername(defaultSmtpUsername);
            if (defaultSmtpPassword != null && !defaultSmtpPassword.isBlank()) g.setSmtpPassword(defaultSmtpPassword);
            if (defaultAiBaseUrl != null && !defaultAiBaseUrl.isBlank()) g.setLlmBaseUrl(defaultAiBaseUrl);
            if (defaultAiModel != null && !defaultAiModel.isBlank()) g.setLlmModelName(defaultAiModel);
            if (defaultAiApiKey != null && !defaultAiApiKey.isBlank()) g.setLlmApiKeyEncrypted(defaultAiApiKey);
            return globalSettingsRepository.save(g);
        });
    }

    public PromptTemplatesResponse getPromptTemplates(User user) {
        PromptTemplatesEntity entity = promptTemplatesRepository.findByUser(user)
                .orElseGet(() -> promptTemplatesRepository.save(defaultPromptTemplates(user)));
        return new PromptTemplatesResponse(entity.getStoryCreation(), entity.getOutlineChapter(), entity.getManuscriptSection(), entity.getRefineWithInstruction(), entity.getRefineWithoutInstruction());
    }

    @Transactional
    public PromptTemplatesResponse updatePromptTemplates(User user, PromptTemplatesUpdateRequest request) {
        PromptTemplatesEntity entity = promptTemplatesRepository.findByUser(user)
                .orElseGet(() -> defaultPromptTemplates(user));
        if (request.storyCreation() != null) entity.setStoryCreation(request.storyCreation());
        if (request.outlineChapter() != null) entity.setOutlineChapter(request.outlineChapter());
        if (request.manuscriptSection() != null) entity.setManuscriptSection(request.manuscriptSection());
        if (request.refineWithInstruction() != null) entity.setRefineWithInstruction(request.refineWithInstruction());
        if (request.refineWithoutInstruction() != null) entity.setRefineWithoutInstruction(request.refineWithoutInstruction());
        promptTemplatesRepository.save(entity);
        return getPromptTemplates(user);
    }

    @Transactional
    public PromptTemplatesResponse resetPromptTemplates(User user) {
        PromptTemplatesEntity entity = promptTemplatesRepository.findByUser(user)
                .orElseGet(() -> defaultPromptTemplates(user));
        PromptTemplatesEntity defaults = defaultPromptTemplates(user);
        entity.setStoryCreation(defaults.getStoryCreation());
        entity.setOutlineChapter(defaults.getOutlineChapter());
        entity.setManuscriptSection(defaults.getManuscriptSection());
        entity.setRefineWithInstruction(defaults.getRefineWithInstruction());
        entity.setRefineWithoutInstruction(defaults.getRefineWithoutInstruction());
        promptTemplatesRepository.save(entity);
        return getPromptTemplates(user);
    }

    public WorldPromptTemplatesResponse getWorldPromptTemplates(User user) {
        WorldPromptTemplatesEntity entity = worldPromptTemplatesRepository.findByUser(user)
                .orElseGet(() -> worldPromptTemplatesRepository.save(defaultWorldPrompts(user)));
        return new WorldPromptTemplatesResponse(jsonToMap(entity.getModulesJson()), jsonToMap(entity.getFinalTemplatesJson()), entity.getFieldRefine());
    }

    @Transactional
    public WorldPromptTemplatesResponse updateWorldPrompts(User user, WorldPromptTemplatesUpdateRequest request) {
        WorldPromptTemplatesEntity entity = worldPromptTemplatesRepository.findByUser(user)
                .orElseGet(() -> defaultWorldPrompts(user));
        if (request.modules() != null) entity.setModulesJson(mapToJson(request.modules()));
        if (request.finalTemplates() != null) entity.setFinalTemplatesJson(mapToJson(request.finalTemplates()));
        if (request.fieldRefine() != null) entity.setFieldRefine(request.fieldRefine());
        worldPromptTemplatesRepository.save(entity);
        return getWorldPromptTemplates(user);
    }

    @Transactional
    public WorldPromptTemplatesResponse resetWorldPrompts(User user) {
        WorldPromptTemplatesEntity defaults = defaultWorldPrompts(user);
        WorldPromptTemplatesEntity entity = worldPromptTemplatesRepository.findByUser(user)
                .orElseGet(() -> defaults);
        entity.setModulesJson(defaults.getModulesJson());
        entity.setFinalTemplatesJson(defaults.getFinalTemplatesJson());
        entity.setFieldRefine(defaults.getFieldRefine());
        worldPromptTemplatesRepository.save(entity);
        return getWorldPromptTemplates(user);
    }

    public PromptMetadataResponse getPromptMetadata() {
        return new PromptMetadataResponse(
                java.util.List.of(
                        new PromptMetadataResponse.SyntaxTip("插值", "使用 {variable} 形式插入上下文"),
                        new PromptMetadataResponse.SyntaxTip("函数", "使用 {{fn:name}} 调用后端函数")
                ),
                java.util.List.of(
                        new PromptMetadataResponse.PromptFunction("randomName", "生成随机角色名", "{{fn:randomName locale=zh}}"),
                        new PromptMetadataResponse.PromptFunction("timeline", "输出时间线", "{{fn:timeline storyId}}")
                ),
                java.util.List.of(
                        new PromptMetadataResponse.TemplateMetadata("storyCreation", java.util.List.of(
                                new PromptMetadataResponse.Variable("idea", "string", "创意/灵感"),
                                new PromptMetadataResponse.Variable("genre", "string", "体裁"),
                                new PromptMetadataResponse.Variable("tone", "string", "基调")
                        )),
                        new PromptMetadataResponse.TemplateMetadata("manuscriptSection", java.util.List.of(
                                new PromptMetadataResponse.Variable("sceneSummary", "string", "场景摘要"),
                                new PromptMetadataResponse.Variable("worldContext", "string", "世界观描述")
                        ))
                ),
                java.util.List.of(
                        "示例：请根据 {idea} 生成一个故事大纲，包含 3 章，每章 2 个场景。"
                )
        );
    }

    public WorldPromptMetadataResponse getWorldPromptMetadata() {
        return new WorldPromptMetadataResponse(
                java.util.List.of(
                        new WorldPromptMetadataResponse.Variable("worldName", "string", "世界名称"),
                        new WorldPromptMetadataResponse.Variable("themes", "string", "世界主题")
                ),
                java.util.List.of(
                        new WorldPromptMetadataResponse.FunctionItem("history", "获取历史时间线", "{{fn:history worldId}}")
                ),
                java.util.List.of(
                        new WorldPromptMetadataResponse.Module("geography", "地理环境", java.util.List.of(
                                new WorldPromptMetadataResponse.ModuleField("terrain", "地形地貌", 150),
                                new WorldPromptMetadataResponse.ModuleField("climate", "气候特征", 120)
                        )),
                        new WorldPromptMetadataResponse.Module("society", "社会体系", java.util.List.of(
                                new WorldPromptMetadataResponse.ModuleField("politics", "政治体制", 150),
                                new WorldPromptMetadataResponse.ModuleField("economy", "经济", 120)
                        ))
                ),
                java.util.List.of("示例：请为 {worldName} 设计一段宗教体系描述。")
        );
    }

    private PromptTemplatesEntity defaultPromptTemplates(User user) {
        PromptTemplatesEntity entity = new PromptTemplatesEntity();
        entity.setUser(user);
        entity.setStoryCreation("你是一个小说策划，请根据灵感生成故事概要和角色卡。");
        entity.setOutlineChapter("为故事生成章节与场景，输出结构化列表。");
        entity.setManuscriptSection("根据场景描述撰写正文，保持语气 {tone}。");
        entity.setRefineWithInstruction("根据指令优化文本：{instruction}");
        entity.setRefineWithoutInstruction("润色以下文本，使其更流畅。");
        return entity;
    }

    private WorldPromptTemplatesEntity defaultWorldPrompts(User user) {
        Map<String, String> modules = new HashMap<>();
        modules.put("geography", "描述地形、气候和关键地点");
        modules.put("society", "描述政治、经济、文化");
        WorldPromptTemplatesEntity entity = new WorldPromptTemplatesEntity();
        entity.setUser(user);
        entity.setModulesJson(mapToJson(modules));
        entity.setFinalTemplatesJson(mapToJson(Map.of()));
        entity.setFieldRefine("优化世界观字段表述");
        return entity;
    }

    private Map<String, String> jsonToMap(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private String mapToJson(Map<String, String> map) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }
}
