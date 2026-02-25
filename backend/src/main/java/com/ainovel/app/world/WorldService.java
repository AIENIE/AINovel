package com.ainovel.app.world;

import com.ainovel.app.world.dto.*;
import com.ainovel.app.world.model.World;
import com.ainovel.app.world.repo.WorldRepository;
import com.ainovel.app.user.User;
import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.dto.AiRefineRequest;
import com.ainovel.app.ai.dto.AiRefineResponse;
import com.ainovel.app.security.ResourceAccessGuard;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class WorldService {
    @Autowired
    private WorldRepository worldRepository;
    @Autowired
    private AiService aiService;
    @Autowired
    private ResourceAccessGuard accessGuard;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<WorldDefinitionDto> definitions() {
        return List.of(
                new WorldDefinitionDto("geography", "地理环境", "定义世界的地形、气候、重要地点", List.of(
                        new WorldDefinitionDto.Field("terrain", "地形地貌", "textarea", "例如：群山、海岛"),
                        new WorldDefinitionDto.Field("climate", "气候特征", "textarea", "例如：季风、极寒"),
                        new WorldDefinitionDto.Field("locations", "重要地点", "textarea", "列出关键城市")
                )),
                new WorldDefinitionDto("society", "社会体系", "政治、经济、文化", List.of(
                        new WorldDefinitionDto.Field("politics", "政治体制", "textarea", "君主制/联邦"),
                        new WorldDefinitionDto.Field("economy", "经济结构", "textarea", "主要产业"),
                        new WorldDefinitionDto.Field("culture", "文化", "textarea", "风俗")
                )),
                new WorldDefinitionDto("magic_tech", "魔法/科技", "魔法或科技体系", List.of(
                        new WorldDefinitionDto.Field("system_name", "体系名称", "text", ""),
                        new WorldDefinitionDto.Field("rules", "规则", "textarea", "力量来源"),
                        new WorldDefinitionDto.Field("limitations", "限制", "textarea", "代价")
                ))
        );
    }

    public List<WorldDto> list(User user) {
        accessGuard.assertCurrentUserEquals(user.getUsername());
        return worldRepository.findByUser(user).stream()
                .map(w -> new WorldDto(w.getId(), w.getName(), w.getTagline(), w.getStatus(), w.getVersion(), w.getUpdatedAt()))
                .toList();
    }

    @Transactional
    public WorldDetailDto create(User user, WorldCreateRequest request) {
        World world = new World();
        world.setUser(user);
        world.setName(request.name());
        world.setTagline(request.tagline());
        world.setThemesJson(writeJson(request.themes()));
        world.setCreativeIntent(request.creativeIntent());
        world.setNotes(request.notes());
        world.setStatus("draft");
        world.setVersion("0.1.0");
        world.setModulesJson(writeJson(new HashMap<String, Map<String, String>>()));
        world.setModuleProgressJson(writeJson(new HashMap<String, String>()));
        worldRepository.save(world);
        return toDetail(world);
    }

    public WorldDetailDto get(UUID id) {
        World world = worldRepository.findById(id).orElseThrow(() -> new RuntimeException("世界不存在"));
        accessGuard.assertOwner(world.getUser());
        return toDetail(world);
    }

    @Transactional
    public WorldDetailDto update(UUID id, WorldUpdateRequest request) {
        World world = worldRepository.findById(id).orElseThrow(() -> new RuntimeException("世界不存在"));
        accessGuard.assertOwner(world.getUser());
        if (request.name() != null) world.setName(request.name());
        if (request.tagline() != null) world.setTagline(request.tagline());
        if (request.themes() != null) world.setThemesJson(writeJson(request.themes()));
        if (request.creativeIntent() != null) world.setCreativeIntent(request.creativeIntent());
        if (request.notes() != null) world.setNotes(request.notes());
        worldRepository.save(world);
        return toDetail(world);
    }

    @Transactional
    public void delete(UUID id) {
        World world = worldRepository.findById(id).orElseThrow();
        accessGuard.assertOwner(world.getUser());
        if (!"draft".equalsIgnoreCase(world.getStatus())) {
            throw new RuntimeException("仅草稿世界可删除");
        }
        worldRepository.delete(world);
    }

    @Transactional
    public WorldDetailDto updateModules(UUID id, WorldModulesUpdateRequest request) {
        World world = worldRepository.findById(id).orElseThrow();
        accessGuard.assertOwner(world.getUser());
        Map<String, Map<String, String>> modules = readModules(world.getModulesJson());
        if (request.modules() != null) modules.putAll(request.modules());
        world.setModulesJson(writeJson(modules));
        worldRepository.save(world);
        return toDetail(world);
    }

    @Transactional
    public WorldDetailDto updateModule(UUID id, String moduleKey, WorldModuleUpdateRequest request) {
        World world = worldRepository.findById(id).orElseThrow();
        accessGuard.assertOwner(world.getUser());
        Map<String, Map<String, String>> modules = readModules(world.getModulesJson());
        modules.put(moduleKey, request.fields());
        world.setModulesJson(writeJson(modules));
        worldRepository.save(world);
        return toDetail(world);
    }

    @Transactional
    public AiRefineResponse refineField(User user, UUID id, String moduleKey, String fieldKey, String text, String instruction) {
        World world = worldRepository.findById(id).orElseThrow(() -> new RuntimeException("世界不存在"));
        accessGuard.assertOwner(world.getUser());
        String prompt = (instruction == null ? "" : instruction).trim();
        if (prompt.isBlank()) {
            prompt = "请优化以下世界观字段表述，使其更清晰、更具细节且保持一致性。";
        }
        return aiService.refine(user, new AiRefineRequest(text, prompt, null));
    }

    public WorldPublishPreviewResponse preview(UUID id) {
        World world = worldRepository.findById(id).orElseThrow();
        accessGuard.assertOwner(world.getUser());
        Map<String, Map<String, String>> modules = readModules(world.getModulesJson());
        List<String> missing = new ArrayList<>();
        List<String> toGenerate = new ArrayList<>();
        for (WorldDefinitionDto def : definitions()) {
            Map<String, String> fields = modules.get(def.key());
            boolean hasAny = false;
            if (fields != null) {
                for (String v : fields.values()) {
                    if (v != null && !v.isBlank()) {
                        hasAny = true;
                        break;
                    }
                }
            }
            if (!hasAny) {
                missing.add(def.key());
                toGenerate.add(def.key());
            }
        }
        return new WorldPublishPreviewResponse(missing, toGenerate);
    }

    @Transactional
    public WorldDetailDto publish(UUID id) {
        World world = worldRepository.findById(id).orElseThrow();
        accessGuard.assertOwner(world.getUser());
        Map<String, Map<String, String>> modules = readModules(world.getModulesJson());

        Map<String, String> progress = new HashMap<>();
        boolean anyAwaiting = false;
        for (WorldDefinitionDto def : definitions()) {
            Map<String, String> fields = modules.get(def.key());
            boolean hasAny = false;
            if (fields != null) {
                for (String v : fields.values()) {
                    if (v != null && !v.isBlank()) {
                        hasAny = true;
                        break;
                    }
                }
            }
            if (hasAny) {
                progress.put(def.key(), "COMPLETED");
            } else {
                progress.put(def.key(), "AWAITING_GENERATION");
                anyAwaiting = true;
            }
        }

        if (anyAwaiting) {
            world.setStatus("generating");
        } else {
            if (!"active".equalsIgnoreCase(world.getStatus())) {
                world.setVersion(bumpPatch(world.getVersion()));
            }
            world.setStatus("active");
        }
        world.setModuleProgressJson(writeJson(progress));
        worldRepository.save(world);
        return toDetail(world);
    }

    public WorldGenerationStatus generationStatus(UUID id) {
        World world = worldRepository.findById(id).orElseThrow();
        accessGuard.assertOwner(world.getUser());
        Map<String, String> progress = readProgress(world.getModuleProgressJson());
        List<WorldGenerationStatus.ModuleStatus> modules = progress.entrySet().stream()
                .map(e -> new WorldGenerationStatus.ModuleStatus(e.getKey(), e.getValue(), 1, null))
                .toList();
        return new WorldGenerationStatus(modules);
    }

    @Transactional
    public WorldDetailDto generateModule(UUID id, String moduleKey) {
        World world = worldRepository.findById(id).orElseThrow();
        accessGuard.assertOwner(world.getUser());
        Map<String, String> progress = readProgress(world.getModuleProgressJson());
        progress.put(moduleKey, "COMPLETED");
        world.setModuleProgressJson(writeJson(progress));

        Map<String, Map<String, String>> modules = readModules(world.getModulesJson());
        Optional<WorldDefinitionDto> moduleDef = definitions().stream().filter(d -> d.key().equals(moduleKey)).findFirst();
        if (moduleDef.isPresent()) {
            Map<String, String> fields = new HashMap<>(modules.getOrDefault(moduleKey, new HashMap<>()));
            for (WorldDefinitionDto.Field f : moduleDef.get().fields()) {
                String existing = fields.get(f.key());
                if (existing == null || existing.isBlank()) {
                    fields.put(f.key(), "AI 生成的占位内容：" + f.label());
                }
            }
            modules.put(moduleKey, fields);
        } else {
            modules.putIfAbsent(moduleKey, new HashMap<>());
            modules.get(moduleKey).putIfAbsent("auto", "AI 生成的占位内容");
        }
        world.setModulesJson(writeJson(modules));

        boolean allDone = true;
        for (WorldDefinitionDto def : definitions()) {
            String st = progress.get(def.key());
            if (!"COMPLETED".equalsIgnoreCase(st)) {
                allDone = false;
                break;
            }
        }
        if (allDone && !"active".equalsIgnoreCase(world.getStatus())) {
            world.setStatus("active");
            world.setVersion(bumpPatch(world.getVersion()));
        }
        worldRepository.save(world);
        return toDetail(world);
    }

    @Transactional
    public WorldDetailDto retryModule(UUID id, String moduleKey) {
        return generateModule(id, moduleKey);
    }

    private WorldDetailDto toDetail(World world) {
        return new WorldDetailDto(
                world.getId(),
                world.getName(),
                world.getTagline(),
                world.getStatus(),
                world.getVersion(),
                readThemes(world.getThemesJson()),
                world.getCreativeIntent(),
                world.getNotes(),
                readModules(world.getModulesJson()),
                readProgress(world.getModuleProgressJson()),
                world.getUpdatedAt()
        );
    }

    private String bumpPatch(String version) {
        if (version == null || version.isBlank()) return "0.1.0";
        String[] parts = version.split("\\.");
        if (parts.length != 3) return version;
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            int patch = Integer.parseInt(parts[2]);
            return major + "." + minor + "." + (patch + 1);
        } catch (Exception e) {
            return version;
        }
    }

    private List<String> readThemes(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try { return objectMapper.readValue(json, new TypeReference<>() {});} catch (Exception e) { return new ArrayList<>(); }
    }

    private Map<String, Map<String, String>> readModules(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try { return objectMapper.readValue(json, new TypeReference<>() {});} catch (Exception e) { return new HashMap<>(); }
    }

    private Map<String, String> readProgress(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try { return objectMapper.readValue(json, new TypeReference<>() {});} catch (Exception e) { return new HashMap<>(); }
    }

    private String writeJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj);} catch (Exception e) { return "{}"; }
    }
}
