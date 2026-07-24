package com.ainovel.app.v2;

import com.ainovel.app.common.BusinessException;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.common.JsonColumnCodec;
import com.ainovel.app.user.User;
import com.ainovel.app.v2.model.V2AutoSaveConfig;
import com.ainovel.app.v2.model.V2ManuscriptBranch;
import com.ainovel.app.v2.model.V2ManuscriptVersion;
import com.ainovel.app.v2.model.V2VersionDiff;
import com.ainovel.app.v2.repo.V2AutoSaveConfigRepository;
import com.ainovel.app.v2.repo.V2ManuscriptBranchRepository;
import com.ainovel.app.v2.repo.V2ManuscriptVersionRepository;
import com.ainovel.app.v2.repo.V2VersionDiffRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class V2VersionPersistenceService {
    private final V2ManuscriptBranchRepository branchRepository;
    private final V2ManuscriptVersionRepository versionRepository;
    private final V2VersionDiffRepository diffRepository;
    private final V2AutoSaveConfigRepository autoSaveRepository;
    private final ManuscriptRepository manuscriptRepository;
    private final ObjectMapper objectMapper;
    private final V2Json v2Json;
    private final JsonColumnCodec jsonColumnCodec;

    public V2VersionPersistenceService(V2ManuscriptBranchRepository branchRepository,
                                       V2ManuscriptVersionRepository versionRepository,
                                       V2VersionDiffRepository diffRepository,
                                       V2AutoSaveConfigRepository autoSaveRepository,
                                       ManuscriptRepository manuscriptRepository,
                                       ObjectMapper objectMapper,
                                       V2Json v2Json,
                                       JsonColumnCodec jsonColumnCodec) {
        this.branchRepository = branchRepository;
        this.versionRepository = versionRepository;
        this.diffRepository = diffRepository;
        this.autoSaveRepository = autoSaveRepository;
        this.manuscriptRepository = manuscriptRepository;
        this.objectMapper = objectMapper;
        this.v2Json = v2Json;
        this.jsonColumnCodec = jsonColumnCodec;
    }

    @Transactional
    public List<Map<String, Object>> listVersions(Manuscript manuscript, User user) {
        ensureMainBranchAndInitialVersion(manuscript, user);
        return listVersions(manuscript.getId());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listVersions(UUID manuscriptId) {
        return versionRepository.findByManuscriptIdOrderByCreatedAtDesc(manuscriptId).stream().map(this::versionMap).toList();
    }

    @Transactional
    public Map<String, Object> createVersion(Manuscript manuscript, User user, Map<String, Object> payload) {
        ensureMainBranchAndInitialVersion(manuscript, user);
        UUID branchId = manuscript.getCurrentBranchId();
        if (payload != null && payload.get("branchId") != null && !payload.get("branchId").toString().isBlank()) {
            branchId = UUID.fromString(payload.get("branchId").toString());
        }

        V2ManuscriptVersion latest = latestVersion(manuscript.getId(), branchId);
        String currentHash = sha256(str(manuscript.getSectionsJson(), "{}"));
        if (latest != null && Objects.equals(latest.getContentHash(), currentHash)) {
            Map<String, Object> dedup = versionMap(latest);
            dedup.put("deduplicated", true);
            return dedup;
        }

        String snapshotType = str(payload == null ? null : payload.get("snapshotType"), "manual");
        V2ManuscriptVersion version = buildVersion(
                manuscript,
                user,
                requireBranch(manuscript.getId(), branchId),
                payload == null ? null : payload.get("label"),
                snapshotType,
                str(manuscript.getSectionsJson(), "{}"),
                payload == null ? null : payload.get("metadata")
        );
        version = versionRepository.saveAndFlush(version);
        if ("auto".equalsIgnoreCase(snapshotType)) {
            cleanupAutoSnapshots(manuscript.getId(), branchId, intVal(getAutoSave(user).get("maxAutoVersions"), 100));
        }
        return versionMap(version);
    }

    @Transactional
    public Map<String, Object> getVersion(Manuscript manuscript, User user, UUID versionId) {
        ensureMainBranchAndInitialVersion(manuscript, user);
        return versionMap(requireVersion(manuscript.getId(), versionId));
    }

    @Transactional
    public Map<String, Object> rollback(Manuscript manuscript, User user, UUID versionId) {
        ensureMainBranchAndInitialVersion(manuscript, user);
        V2ManuscriptVersion target = requireVersion(manuscript.getId(), versionId);
        UUID currentBranchId = manuscript.getCurrentBranchId() == null ? mainBranchId(manuscript.getId()) : manuscript.getCurrentBranchId();
        V2ManuscriptBranch branch = requireBranch(manuscript.getId(), currentBranchId);

        V2ManuscriptVersion backup = versionRepository.saveAndFlush(buildVersion(
                manuscript,
                user,
                branch,
                "回滚前自动备份",
                "manual",
                str(manuscript.getSectionsJson(), "{}"),
                Map.of("rollbackTargetVersionId", versionId)
        ));

        manuscript.setSectionsJson(str(target.getSectionsJson(), "{}"));
        manuscriptRepository.save(manuscript);

        V2ManuscriptVersion rollback = versionRepository.saveAndFlush(buildVersion(
                manuscript,
                user,
                branch,
                "回滚至 v" + target.getVersionNumber(),
                "manual",
                str(manuscript.getSectionsJson(), "{}"),
                Map.of("sourceVersionId", versionId)
        ));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("manuscriptId", manuscript.getId());
        out.put("rolledBackTo", versionId);
        out.put("backupVersionId", backup.getId());
        out.put("rollbackVersionId", rollback.getId());
        out.put("message", "已回滚至 v" + target.getVersionNumber() + "，当前状态已备份");
        out.put("status", "completed");
        out.put("updatedAt", Instant.now());
        return out;
    }

    @Transactional
    public Map<String, Object> diff(Manuscript manuscript, User user, UUID fromVersionId, UUID toVersionId) {
        ensureMainBranchAndInitialVersion(manuscript, user);
        Optional<V2VersionDiff> cached = diffRepository.findByFromVersionIdAndToVersionId(fromVersionId, toVersionId);
        if (cached.isPresent()) {
            return v2Json.map(cached.get().getDiffJson());
        }
        V2ManuscriptVersion from = requireVersion(manuscript.getId(), fromVersionId);
        V2ManuscriptVersion to = requireVersion(manuscript.getId(), toVersionId);
        Map<String, Object> result = buildDiff(from, to);

        V2VersionDiff diff = new V2VersionDiff();
        diff.setFromVersion(from);
        diff.setToVersion(to);
        diff.setDiffJson(v2Json.write(result));
        diffRepository.saveAndFlush(diff);
        return result;
    }

    @Transactional
    public List<Map<String, Object>> listBranches(Manuscript manuscript, User user) {
        ensureMainBranchAndInitialVersion(manuscript, user);
        return sortedBranches(manuscript.getId()).stream().map(this::branchMap).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listBranches(UUID manuscriptId) {
        return sortedBranches(manuscriptId).stream().map(this::branchMap).toList();
    }

    @Transactional
    public Map<String, Object> checkoutBranch(Manuscript manuscript, User user, UUID branchId) {
        ensureMainBranchAndInitialVersion(manuscript, user);
        V2ManuscriptBranch branch = requireBranch(manuscript.getId(), branchId);
        if (!"active".equals(branch.getStatus())) {
            throw new BusinessException("仅 active 分支可切换");
        }
        manuscript.setCurrentBranchId(branchId);
        V2ManuscriptVersion latest = latestVersion(manuscript.getId(), branchId);
        if (latest != null) {
            manuscript.setSectionsJson(str(latest.getSectionsJson(), manuscript.getSectionsJson()));
        }
        manuscriptRepository.save(manuscript);
        return Map.of(
                "manuscriptId", manuscript.getId(),
                "currentBranchId", branchId,
                "status", "checked_out",
                "checkedOutAt", Instant.now()
        );
    }

    @Transactional
    public Map<String, Object> createBranch(Manuscript manuscript, User user, Map<String, Object> payload) {
        ensureMainBranchAndInitialVersion(manuscript, user);
        String name = str(payload.get("name"), "branch-" + UUID.randomUUID().toString().substring(0, 8));
        boolean duplicate = branchRepository.findByManuscriptId(manuscript.getId()).stream()
                .anyMatch(existing -> name.equals(existing.getName()) && !"abandoned".equals(existing.getStatus()));
        if (duplicate) {
            throw new BusinessException("分支名称重复");
        }
        UUID sourceVersionId = payload.get("sourceVersionId") == null
                ? latestVersionId(manuscript.getId())
                : UUID.fromString(payload.get("sourceVersionId").toString());
        V2ManuscriptVersion sourceVersion = requireVersion(manuscript.getId(), sourceVersionId);

        V2ManuscriptBranch branch = new V2ManuscriptBranch();
        branch.setManuscript(manuscript);
        branch.setName(name);
        branch.setDescription(str(payload.get("description"), ""));
        branch.setSourceVersionId(sourceVersionId);
        branch.setStatus("active");
        branch.setMain(false);
        branch = branchRepository.saveAndFlush(branch);

        V2ManuscriptVersion seedVersion = buildVersion(
                manuscript,
                user,
                branch,
                "从 v" + sourceVersion.getVersionNumber() + " 分支",
                "branch_point",
                str(sourceVersion.getSectionsJson(), "{}"),
                Map.of("sourceVersionId", sourceVersionId)
        );
        versionRepository.saveAndFlush(seedVersion);
        return branchMap(branch);
    }

    @Transactional
    public Map<String, Object> updateBranch(UUID manuscriptId, UUID branchId, Map<String, Object> payload) {
        V2ManuscriptBranch branch = requireBranch(manuscriptId, branchId);
        if (branch.isMain() && payload.containsKey("status") && "abandoned".equals(payload.get("status"))) {
            throw new BusinessException("主分支不能废弃");
        }
        if (payload.containsKey("name")) {
            branch.setName(payload.get("name").toString());
        }
        if (payload.containsKey("description")) {
            branch.setDescription(str(payload.get("description"), ""));
        }
        if (payload.containsKey("status")) {
            branch.setStatus(payload.get("status").toString());
        }
        return branchMap(branchRepository.saveAndFlush(branch));
    }

    @Transactional
    public Map<String, Object> mergeBranch(Manuscript manuscript, User user, UUID branchId, Map<String, Object> payload) {
        ensureMainBranchAndInitialVersion(manuscript, user);
        V2ManuscriptBranch sourceBranch = requireBranch(manuscript.getId(), branchId);
        if (sourceBranch.isMain()) {
            throw new BusinessException("主分支无需合并");
        }
        if (!"active".equals(sourceBranch.getStatus())) {
            throw new BusinessException("仅 active 分支可合并");
        }
        UUID mainBranchId = mainBranchId(manuscript.getId());
        V2ManuscriptVersion sourceVersion = latestVersion(manuscript.getId(), branchId);
        V2ManuscriptVersion targetVersion = latestVersion(manuscript.getId(), mainBranchId);
        if (sourceVersion == null) {
            throw new BusinessException("分支没有可合并版本");
        }
        String strategy = str(payload == null ? null : payload.get("strategy"), "REPLACE_ALL");
        Map<String, String> sourceSections = parseSections(str(sourceVersion.getSectionsJson(), "{}"));
        Map<String, String> targetSections = parseSections(targetVersion == null ? "{}" : str(targetVersion.getSectionsJson(), "{}"));
        Map<String, String> mergedSections = new LinkedHashMap<>(targetSections);
        List<Map<String, Object>> conflicts = new ArrayList<>();
        if ("SCENE_SELECT".equalsIgnoreCase(strategy)) {
            Map<String, Object> resolutions = payload == null ? Map.of() : map(payload.get("sceneResolutions"));
            Set<String> allSceneIds = new LinkedHashSet<>();
            allSceneIds.addAll(targetSections.keySet());
            allSceneIds.addAll(sourceSections.keySet());
            for (String sceneId : allSceneIds) {
                String fromTarget = targetSections.getOrDefault(sceneId, "");
                String fromSource = sourceSections.getOrDefault(sceneId, "");
                if (Objects.equals(fromTarget, fromSource)) {
                    continue;
                }
                String choose = str(resolutions.get(sceneId), "");
                if (choose.isBlank()) {
                    conflicts.add(Map.of(
                            "sceneId", sceneId,
                            "targetLength", fromTarget.length(),
                            "sourceLength", fromSource.length(),
                            "mainContent", fromTarget,
                            "branchContent", fromSource,
                            "reason", "主线和分支均修改了此场景，缺少解决策略"
                    ));
                    continue;
                }
                mergedSections.put(sceneId, "target".equalsIgnoreCase(choose) ? fromTarget : fromSource);
            }
        } else {
            mergedSections.clear();
            mergedSections.putAll(sourceSections);
        }
        if (!conflicts.isEmpty()) {
            return Map.of("manuscriptId", manuscript.getId(), "sourceBranchId", branchId, "targetBranchId", mainBranchId, "status", "conflict", "conflicts", conflicts);
        }
        manuscript.setCurrentBranchId(mainBranchId);
        manuscript.setSectionsJson(writeSections(mergedSections));
        manuscriptRepository.save(manuscript);
        sourceBranch.setStatus("merged");
        branchRepository.save(sourceBranch);

        V2ManuscriptVersion mergeVersion = versionRepository.saveAndFlush(buildVersion(
                manuscript,
                user,
                requireBranch(manuscript.getId(), mainBranchId),
                payload == null ? "merge:" + sourceBranch.getName() : payload.get("label"),
                "merge",
                str(manuscript.getSectionsJson(), "{}"),
                Map.of("sourceBranchId", branchId, "strategy", strategy)
        ));
        return Map.of("manuscriptId", manuscript.getId(), "sourceBranchId", branchId, "targetBranchId", mainBranchId, "mergeVersionId", mergeVersion.getId(), "status", "merged");
    }

    @Transactional
    public void abandonBranch(UUID manuscriptId, UUID branchId) {
        V2ManuscriptBranch branch = requireBranch(manuscriptId, branchId);
        if (branch.isMain()) {
            throw new BusinessException("主分支不能废弃");
        }
        branch.setStatus("abandoned");
        branchRepository.save(branch);
    }

    @Transactional
    public Map<String, Object> getAutoSave(User user) {
        return autoSaveMap(requireAutoSave(user));
    }

    @Transactional
    public Map<String, Object> updateAutoSave(User user, Map<String, Object> payload) {
        V2AutoSaveConfig config = requireAutoSave(user);
        int interval = intVal(payload.get("autoSaveIntervalSeconds"), config.getAutoSaveIntervalSeconds());
        int maxAuto = intVal(payload.get("maxAutoVersions"), config.getMaxAutoVersions());
        if (interval < 30) {
            throw new BusinessException("autoSaveIntervalSeconds 不能小于 30");
        }
        if (maxAuto < 10) {
            throw new BusinessException("maxAutoVersions 不能小于 10");
        }
        config.setAutoSaveIntervalSeconds(interval);
        config.setMaxAutoVersions(maxAuto);
        return autoSaveMap(autoSaveRepository.saveAndFlush(config));
    }

    @Transactional
    public void ensureMainBranchAndInitialVersion(Manuscript manuscript, User user) {
        Manuscript lockedManuscript = manuscriptRepository.findByIdForUpdate(manuscript.getId()).orElse(manuscript);
        List<V2ManuscriptBranch> branches = branchRepository.findByManuscriptId(lockedManuscript.getId());
        V2ManuscriptBranch main = branches.stream()
                .filter(V2ManuscriptBranch::isMain)
                .min(Comparator.comparing(branch -> safeInstant(branch.getCreatedAt())))
                .orElse(null);
        if (main == null) {
            main = new V2ManuscriptBranch();
            main.setManuscript(lockedManuscript);
            main.setName("main");
            main.setDescription("默认主分支");
            main.setStatus("active");
            main.setMain(true);
            main = branchRepository.saveAndFlush(main);
        }
        for (V2ManuscriptBranch branch : branches) {
            if (!branch.getId().equals(main.getId()) && branch.isMain()) {
                branch.setMain(false);
                branchRepository.save(branch);
            }
        }
        if (lockedManuscript.getCurrentBranchId() == null || branchRepository.findByManuscriptIdAndId(lockedManuscript.getId(), lockedManuscript.getCurrentBranchId()).isEmpty()) {
            lockedManuscript.setCurrentBranchId(main.getId());
            manuscriptRepository.save(lockedManuscript);
        }
        if (versionRepository.findByManuscriptIdOrderByCreatedAtDesc(lockedManuscript.getId()).isEmpty()) {
            versionRepository.saveAndFlush(buildVersion(lockedManuscript, user, main, "initial", "auto", str(lockedManuscript.getSectionsJson(), "{}"), Map.of("bootstrap", true)));
        }
        manuscript.setCurrentBranchId(lockedManuscript.getCurrentBranchId());
        manuscript.setSectionsJson(lockedManuscript.getSectionsJson());
    }

    private V2ManuscriptVersion buildVersion(Manuscript manuscript, User user, V2ManuscriptBranch branch, Object label,
                                             String snapshotType, String sectionsJson, Object metadata) {
        int versionNumber = nextVersionNumber(manuscript.getId(), branch.getId());
        V2ManuscriptVersion version = new V2ManuscriptVersion();
        version.setManuscript(manuscript);
        version.setBranch(branch);
        version.setVersionNumber(versionNumber);
        version.setLabel(label == null ? "v" + versionNumber : label.toString());
        version.setSnapshotType(snapshotType);
        version.setContentHash(sha256(sectionsJson));
        version.setSectionsJson(sectionsJson);
        version.setMetadataJson(v2Json.write(metadata == null ? Map.of() : metadata));
        UUID parentVersionId = latestVersionId(manuscript.getId());
        if (parentVersionId != null) {
            versionRepository.findById(parentVersionId).ifPresent(version::setParentVersion);
        }
        version.setCreatedBy(user);
        return version;
    }

    private void cleanupAutoSnapshots(UUID manuscriptId, UUID branchId, int maxAutoVersions) {
        List<V2ManuscriptVersion> autos = versionRepository.findByManuscriptIdAndBranchId(manuscriptId, branchId).stream()
                .filter(version -> "auto".equalsIgnoreCase(str(version.getSnapshotType(), "")))
                .sorted(Comparator.comparing(version -> safeInstant(version.getCreatedAt())))
                .toList();
        int overflow = autos.size() - maxAutoVersions;
        for (int i = 0; i < overflow; i++) {
            versionRepository.delete(autos.get(i));
        }
    }

    private int nextVersionNumber(UUID manuscriptId, UUID branchId) {
        return versionRepository.findByManuscriptIdAndBranchId(manuscriptId, branchId).stream()
                .mapToInt(V2ManuscriptVersion::getVersionNumber)
                .max()
                .orElse(0) + 1;
    }

    private UUID latestVersionId(UUID manuscriptId) {
        return versionRepository.findTopByManuscriptIdOrderByCreatedAtDesc(manuscriptId).map(V2ManuscriptVersion::getId).orElse(null);
    }

    private V2ManuscriptVersion latestVersion(UUID manuscriptId, UUID branchId) {
        return versionRepository.findByManuscriptIdOrderByCreatedAtDesc(manuscriptId).stream()
                .filter(version -> branchId == null || Objects.equals(version.getBranch().getId(), branchId))
                .findFirst()
                .orElse(null);
    }

    private UUID mainBranchId(UUID manuscriptId) {
        return branchRepository.findByManuscriptId(manuscriptId).stream()
                .filter(V2ManuscriptBranch::isMain)
                .findFirst()
                .map(V2ManuscriptBranch::getId)
                .orElse(null);
    }

    private V2ManuscriptVersion requireVersion(UUID manuscriptId, UUID versionId) {
        return versionRepository.findByManuscriptIdAndId(manuscriptId, versionId).orElseThrow(() -> new BusinessException("版本不存在"));
    }

    private V2ManuscriptBranch requireBranch(UUID manuscriptId, UUID branchId) {
        return branchRepository.findByManuscriptIdAndId(manuscriptId, branchId).orElseThrow(() -> new BusinessException("分支不存在"));
    }

    private List<V2ManuscriptBranch> sortedBranches(UUID manuscriptId) {
        return branchRepository.findByManuscriptId(manuscriptId).stream()
                .sorted((a, b) -> {
                    if (a.isMain() == b.isMain()) {
                        return safeInstant(a.getCreatedAt()).compareTo(safeInstant(b.getCreatedAt()));
                    }
                    return a.isMain() ? -1 : 1;
                })
                .toList();
    }

    private V2AutoSaveConfig requireAutoSave(User user) {
        return autoSaveRepository.findByUserId(user.getId()).orElseGet(() -> {
            V2AutoSaveConfig config = new V2AutoSaveConfig();
            config.setUser(user);
            config.setAutoSaveIntervalSeconds(300);
            config.setMaxAutoVersions(100);
            return autoSaveRepository.saveAndFlush(config);
        });
    }

    private Map<String, Object> buildDiff(V2ManuscriptVersion from, V2ManuscriptVersion to) {
        Map<String, String> fromSections = parseSections(str(from.getSectionsJson(), "{}"));
        Map<String, String> toSections = parseSections(str(to.getSectionsJson(), "{}"));
        Set<String> allSceneIds = new LinkedHashSet<>();
        allSceneIds.addAll(fromSections.keySet());
        allSceneIds.addAll(toSections.keySet());
        List<Map<String, Object>> changes = new ArrayList<>();
        int changed = 0;
        for (String sceneId : allSceneIds) {
            String before = fromSections.getOrDefault(sceneId, "");
            String after = toSections.getOrDefault(sceneId, "");
            if (!Objects.equals(before, after)) {
                changed++;
                changes.add(Map.of(
                        "sceneId", sceneId,
                        "beforeLength", before.length(),
                        "afterLength", after.length(),
                        "beforeWordCount", before.trim().isEmpty() ? 0 : before.trim().split("\\s+").length,
                        "afterWordCount", after.trim().isEmpty() ? 0 : after.trim().split("\\s+").length,
                        "delta", after.length() - before.length(),
                        "beforeContent", before,
                        "afterContent", after
                ));
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fromVersionId", from.getId());
        result.put("toVersionId", to.getId());
        result.put("changedScenes", changed);
        result.put("changes", changes);
        result.put("generatedAt", Instant.now());
        return result;
    }

    private Map<String, Object> versionMap(V2ManuscriptVersion version) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", version.getId());
        out.put("manuscriptId", version.getManuscript().getId());
        out.put("branchId", version.getBranch().getId());
        out.put("versionNumber", version.getVersionNumber());
        out.put("label", version.getLabel());
        out.put("snapshotType", version.getSnapshotType());
        out.put("contentHash", version.getContentHash());
        out.put("sectionsJson", version.getSectionsJson());
        out.put("metadata", v2Json.map(version.getMetadataJson()));
        out.put("parentVersionId", version.getParentVersion() == null ? null : version.getParentVersion().getId());
        out.put("createdBy", version.getCreatedBy().getId());
        out.put("createdAt", version.getCreatedAt());
        return out;
    }

    private Map<String, Object> branchMap(V2ManuscriptBranch branch) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", branch.getId());
        out.put("manuscriptId", branch.getManuscript().getId());
        out.put("name", branch.getName());
        out.put("description", branch.getDescription());
        out.put("sourceVersionId", branch.getSourceVersionId());
        out.put("status", branch.getStatus());
        out.put("isMain", branch.isMain());
        out.put("createdAt", branch.getCreatedAt());
        out.put("updatedAt", branch.getUpdatedAt());
        return out;
    }

    private Map<String, Object> autoSaveMap(V2AutoSaveConfig config) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", config.getId());
        out.put("userId", config.getUser().getId());
        out.put("autoSaveIntervalSeconds", config.getAutoSaveIntervalSeconds());
        out.put("maxAutoVersions", config.getMaxAutoVersions());
        out.put("createdAt", config.getCreatedAt());
        out.put("updatedAt", config.getUpdatedAt());
        return out;
    }

    private Map<String, String> parseSections(String json) {
        return jsonColumnCodec.read(json, new TypeReference<>() {}, new LinkedHashMap<>());
    }

    private String writeSections(Map<String, String> sections) {
        return jsonColumnCodec.write(sections, "{}");
    }

    private Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return result;
        }
        return new LinkedHashMap<>();
    }

    private int intVal(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private String str(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? fallback : text;
    }

    private Instant safeInstant(Instant instant) {
        return instant == null ? Instant.EPOCH : instant;
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            return UUID.randomUUID().toString().replace("-", "");
        }
    }
}
