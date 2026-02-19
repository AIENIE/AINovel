package com.ainovel.app.v2;

import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@RestController
@RequestMapping("/v2")
public class V2VersionController {
    private final V2AccessGuard accessGuard;
    private final ManuscriptRepository manuscriptRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ConcurrentMap<UUID, ConcurrentMap<UUID, Map<String, Object>>> branchByManuscript = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ConcurrentMap<UUID, Map<String, Object>>> versionByManuscript = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Map<String, Object>> diffCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Map<String, Object>> autoSaveByUser = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Object> initLocks = new ConcurrentHashMap<>();

    public V2VersionController(V2AccessGuard accessGuard, ManuscriptRepository manuscriptRepository) {
        this.accessGuard = accessGuard;
        this.manuscriptRepository = manuscriptRepository;
    }

    @GetMapping("/manuscripts/{manuscriptId}/versions")
    public List<Map<String, Object>> listVersions(@AuthenticationPrincipal UserDetails principal,
                                                  @PathVariable UUID manuscriptId) {
        User user = accessGuard.currentUser(principal);
        Manuscript manuscript = accessGuard.requireOwnedManuscript(manuscriptId, user);
        ensureMainBranchAndInitialVersion(manuscript, user);
        return sortedVersions(manuscriptId);
    }

    @PostMapping("/manuscripts/{manuscriptId}/versions")
    public Map<String, Object> createVersion(@AuthenticationPrincipal UserDetails principal,
                                             @PathVariable UUID manuscriptId,
                                             @RequestBody(required = false) Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        Manuscript manuscript = accessGuard.requireOwnedManuscript(manuscriptId, user);
        ensureMainBranchAndInitialVersion(manuscript, user);

        UUID branchId = manuscript.getCurrentBranchId();
        if (payload != null && payload.get("branchId") instanceof String branchIdText && !branchIdText.isBlank()) {
            branchId = UUID.fromString(branchIdText);
        }

        Map<String, Object> latestOnBranch = latestVersion(manuscriptId, branchId);
        String currentHash = sha256(str(manuscript.getSectionsJson(), "{}"));
        if (latestOnBranch != null && Objects.equals(latestOnBranch.get("contentHash"), currentHash)) {
            Map<String, Object> dedup = new HashMap<>(latestOnBranch);
            dedup.put("deduplicated", true);
            return dedup;
        }

        String snapshotType = str(payload == null ? null : payload.get("snapshotType"), "manual");
        Map<String, Object> version = buildVersion(manuscript, user, branchId,
                payload == null ? null : payload.get("label"),
                snapshotType,
                payload == null ? null : payload.get("metadata"));
        versionByManuscript.computeIfAbsent(manuscriptId, key -> new ConcurrentHashMap<>())
                .put((UUID) version.get("id"), version);
        if ("auto".equalsIgnoreCase(snapshotType)) {
            Map<String, Object> autoConfig = autoSaveByUser.computeIfAbsent(user.getId(), this::defaultAutoSave);
            int maxAutoVersions = intVal(autoConfig.get("maxAutoVersions"), 100);
            cleanupAutoSnapshots(manuscriptId, branchId, maxAutoVersions);
        }
        return version;
    }

    @GetMapping("/manuscripts/{manuscriptId}/versions/{versionId}")
    public Map<String, Object> getVersion(@AuthenticationPrincipal UserDetails principal,
                                          @PathVariable UUID manuscriptId,
                                          @PathVariable UUID versionId) {
        User user = accessGuard.currentUser(principal);
        Manuscript manuscript = accessGuard.requireOwnedManuscript(manuscriptId, user);
        ensureMainBranchAndInitialVersion(manuscript, user);
        Map<String, Object> version = versionByManuscript.computeIfAbsent(manuscriptId, key -> new ConcurrentHashMap<>()).get(versionId);
        if (version == null) {
            throw new RuntimeException("版本不存在");
        }
        return version;
    }

    @PostMapping("/manuscripts/{manuscriptId}/versions/{versionId}/rollback")
    public Map<String, Object> rollback(@AuthenticationPrincipal UserDetails principal,
                                        @PathVariable UUID manuscriptId,
                                        @PathVariable UUID versionId) {
        User user = accessGuard.currentUser(principal);
        Manuscript manuscript = accessGuard.requireOwnedManuscript(manuscriptId, user);
        ensureMainBranchAndInitialVersion(manuscript, user);

        Map<String, Object> version = versionByManuscript.computeIfAbsent(manuscriptId, key -> new ConcurrentHashMap<>()).get(versionId);
        if (version == null) {
            throw new RuntimeException("版本不存在");
        }

        UUID currentBranchId = manuscript.getCurrentBranchId();
        if (currentBranchId == null) {
            currentBranchId = mainBranchId(manuscriptId);
        }
        Map<String, Object> backup = buildVersion(
                manuscript,
                user,
                currentBranchId,
                "回滚前自动备份",
                "manual",
                Map.of("rollbackTargetVersionId", versionId)
        );
        versionByManuscript.computeIfAbsent(manuscriptId, key -> new ConcurrentHashMap<>())
                .put((UUID) backup.get("id"), backup);

        manuscript.setSectionsJson(str(version.get("sectionsJson"), "{}"));
        manuscriptRepository.save(manuscript);

        String rollbackLabel = "回滚至 v" + intVal(version.get("versionNumber"), 0);
        Map<String, Object> rollbackVersion = buildVersion(
                manuscript,
                user,
                currentBranchId,
                rollbackLabel,
                "manual",
                Map.of("sourceVersionId", versionId)
        );
        versionByManuscript.computeIfAbsent(manuscriptId, key -> new ConcurrentHashMap<>())
                .put((UUID) rollbackVersion.get("id"), rollbackVersion);

        Map<String, Object> result = new HashMap<>();
        result.put("manuscriptId", manuscriptId);
        result.put("rolledBackTo", versionId);
        result.put("backupVersionId", backup.get("id"));
        result.put("rollbackVersionId", rollbackVersion.get("id"));
        result.put("message", "已回滚至 " + rollbackLabel + "，当前状态已备份");
        result.put("status", "completed");
        result.put("updatedAt", Instant.now());
        return result;
    }

    @GetMapping("/manuscripts/{manuscriptId}/versions/diff")
    public Map<String, Object> diff(@AuthenticationPrincipal UserDetails principal,
                                    @PathVariable UUID manuscriptId,
                                    @RequestParam UUID fromVersionId,
                                    @RequestParam UUID toVersionId) {
        User user = accessGuard.currentUser(principal);
        Manuscript manuscript = accessGuard.requireOwnedManuscript(manuscriptId, user);
        ensureMainBranchAndInitialVersion(manuscript, user);

        String cacheKey = fromVersionId + "->" + toVersionId;
        Map<String, Object> cached = diffCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        Map<String, Object> from = requireVersion(manuscriptId, fromVersionId);
        Map<String, Object> to = requireVersion(manuscriptId, toVersionId);

        Map<String, String> fromSections = parseSections(str(from.get("sectionsJson"), "{}"));
        Map<String, String> toSections = parseSections(str(to.get("sectionsJson"), "{}"));
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
                int beforeWords = before.trim().isEmpty() ? 0 : before.trim().split("\\s+").length;
                int afterWords = after.trim().isEmpty() ? 0 : after.trim().split("\\s+").length;
                changes.add(Map.of(
                        "sceneId", sceneId,
                        "beforeLength", before.length(),
                        "afterLength", after.length(),
                        "beforeWordCount", beforeWords,
                        "afterWordCount", afterWords,
                        "delta", after.length() - before.length(),
                        "beforeContent", before,
                        "afterContent", after
                ));
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("fromVersionId", fromVersionId);
        result.put("toVersionId", toVersionId);
        result.put("changedScenes", changed);
        result.put("changes", changes);
        result.put("generatedAt", Instant.now());
        diffCache.put(cacheKey, result);
        return result;
    }

    @GetMapping("/manuscripts/{manuscriptId}/branches")
    public List<Map<String, Object>> listBranches(@AuthenticationPrincipal UserDetails principal,
                                                  @PathVariable UUID manuscriptId) {
        User user = accessGuard.currentUser(principal);
        Manuscript manuscript = accessGuard.requireOwnedManuscript(manuscriptId, user);
        ensureMainBranchAndInitialVersion(manuscript, user);
        return sortedBranches(manuscriptId);
    }

    @PostMapping("/manuscripts/{manuscriptId}/branches/{branchId}/checkout")
    public Map<String, Object> checkoutBranch(@AuthenticationPrincipal UserDetails principal,
                                              @PathVariable UUID manuscriptId,
                                              @PathVariable UUID branchId) {
        User user = accessGuard.currentUser(principal);
        Manuscript manuscript = accessGuard.requireOwnedManuscript(manuscriptId, user);
        ensureMainBranchAndInitialVersion(manuscript, user);
        Map<String, Object> branch = requireBranch(manuscriptId, branchId);
        if (!"active".equals(branch.get("status"))) {
            throw new RuntimeException("仅 active 分支可切换");
        }
        manuscript.setCurrentBranchId(branchId);
        manuscriptRepository.save(manuscript);

        Map<String, Object> latest = latestVersion(manuscriptId, branchId);
        if (latest != null) {
            manuscript.setSectionsJson(str(latest.get("sectionsJson"), manuscript.getSectionsJson()));
            manuscriptRepository.save(manuscript);
        }

        return Map.of(
                "manuscriptId", manuscriptId,
                "currentBranchId", branchId,
                "status", "checked_out",
                "checkedOutAt", Instant.now()
        );
    }

    @PostMapping("/manuscripts/{manuscriptId}/branches")
    public Map<String, Object> createBranch(@AuthenticationPrincipal UserDetails principal,
                                            @PathVariable UUID manuscriptId,
                                            @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        Manuscript manuscript = accessGuard.requireOwnedManuscript(manuscriptId, user);
        ensureMainBranchAndInitialVersion(manuscript, user);

        String name = str(payload.get("name"), "branch-" + UUID.randomUUID().toString().substring(0, 8));
        for (Map<String, Object> existing : branchByManuscript.computeIfAbsent(manuscriptId, key -> new ConcurrentHashMap<>()).values()) {
            if (name.equals(existing.get("name")) && !"abandoned".equals(existing.get("status"))) {
                throw new RuntimeException("分支名称重复");
            }
        }

        UUID branchId = UUID.randomUUID();
        UUID sourceVersionId = payload.get("sourceVersionId") == null
                ? latestVersionId(manuscriptId)
                : UUID.fromString(payload.get("sourceVersionId").toString());

        Map<String, Object> branch = new HashMap<>();
        branch.put("id", branchId);
        branch.put("manuscriptId", manuscriptId);
        branch.put("name", name);
        branch.put("description", str(payload.get("description"), ""));
        branch.put("sourceVersionId", sourceVersionId);
        branch.put("status", "active");
        branch.put("isMain", false);
        branch.put("createdAt", Instant.now());
        branch.put("updatedAt", Instant.now());

        branchByManuscript.computeIfAbsent(manuscriptId, key -> new ConcurrentHashMap<>()).put(branchId, branch);

        Map<String, Object> sourceVersion = requireVersion(manuscriptId, sourceVersionId);
        Map<String, Object> seedVersion = buildVersionWithSections(
                manuscriptId,
                user,
                branchId,
                "从 v" + intVal(sourceVersion.get("versionNumber"), 0) + " 分支",
                "branch_point",
                str(sourceVersion.get("sectionsJson"), "{}"),
                Map.of("sourceVersionId", sourceVersionId)
        );
        versionByManuscript.computeIfAbsent(manuscriptId, key -> new ConcurrentHashMap<>())
                .put((UUID) seedVersion.get("id"), seedVersion);
        return branch;
    }

    @PutMapping("/manuscripts/{manuscriptId}/branches/{branchId}")
    public Map<String, Object> updateBranch(@AuthenticationPrincipal UserDetails principal,
                                            @PathVariable UUID manuscriptId,
                                            @PathVariable UUID branchId,
                                            @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedManuscript(manuscriptId, user);
        Map<String, Object> branch = requireBranch(manuscriptId, branchId);
        if (Boolean.TRUE.equals(branch.get("isMain")) && payload.containsKey("status") && "abandoned".equals(payload.get("status"))) {
            throw new RuntimeException("主分支不能废弃");
        }
        if (payload.containsKey("name")) {
            branch.put("name", payload.get("name"));
        }
        if (payload.containsKey("description")) {
            branch.put("description", payload.get("description"));
        }
        if (payload.containsKey("status")) {
            branch.put("status", payload.get("status"));
        }
        branch.put("updatedAt", Instant.now());
        return branch;
    }

    @PostMapping("/manuscripts/{manuscriptId}/branches/{branchId}/merge")
    public Map<String, Object> mergeBranch(@AuthenticationPrincipal UserDetails principal,
                                           @PathVariable UUID manuscriptId,
                                           @PathVariable UUID branchId,
                                           @RequestBody(required = false) Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        Manuscript manuscript = accessGuard.requireOwnedManuscript(manuscriptId, user);
        ensureMainBranchAndInitialVersion(manuscript, user);
        Map<String, Object> branch = requireBranch(manuscriptId, branchId);
        if (Boolean.TRUE.equals(branch.get("isMain"))) {
            throw new RuntimeException("主分支无需合并");
        }
        if (!"active".equals(branch.get("status"))) {
            throw new RuntimeException("仅 active 分支可合并");
        }

        UUID mainBranchId = mainBranchId(manuscriptId);
        if (mainBranchId == null) {
            throw new RuntimeException("主分支不存在");
        }
        Map<String, Object> sourceVersion = latestVersion(manuscriptId, branchId);
        if (sourceVersion == null) {
            throw new RuntimeException("分支没有可合并版本");
        }
        Map<String, Object> targetVersion = latestVersion(manuscriptId, mainBranchId);
        String strategy = str(payload == null ? null : payload.get("strategy"), "REPLACE_ALL");
        Map<String, String> sourceSections = parseSections(str(sourceVersion.get("sectionsJson"), "{}"));
        Map<String, String> targetSections = parseSections(targetVersion == null ? "{}" : str(targetVersion.get("sectionsJson"), "{}"));

        Map<String, String> mergedSections = new LinkedHashMap<>(targetSections);
        List<Map<String, Object>> conflicts = new ArrayList<>();
        if ("SCENE_SELECT".equalsIgnoreCase(strategy)) {
            Map<String, Object> resolutions = map(payload == null ? null : payload.get("sceneResolutions"));
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
            return Map.of(
                    "manuscriptId", manuscriptId,
                    "sourceBranchId", branchId,
                    "targetBranchId", mainBranchId,
                    "status", "conflict",
                    "conflicts", conflicts
            );
        }

        manuscript.setCurrentBranchId(mainBranchId);
        manuscript.setSectionsJson(writeSections(mergedSections));
        manuscriptRepository.save(manuscript);

        branch.put("status", "merged");
        branch.put("updatedAt", Instant.now());

        Map<String, Object> mergeVersion = buildVersion(
                manuscript,
                user,
                mainBranchId,
                payload == null ? "merge:" + branch.get("name") : payload.get("label"),
                "merge",
                Map.of("sourceBranchId", branchId, "strategy", strategy)
        );
        versionByManuscript.computeIfAbsent(manuscriptId, key -> new ConcurrentHashMap<>())
                .put((UUID) mergeVersion.get("id"), mergeVersion);

        return Map.of(
                "manuscriptId", manuscriptId,
                "sourceBranchId", branchId,
                "targetBranchId", mainBranchId,
                "mergeVersionId", mergeVersion.get("id"),
                "status", "merged"
        );
    }

    @DeleteMapping("/manuscripts/{manuscriptId}/branches/{branchId}")
    public ResponseEntity<Void> abandonBranch(@AuthenticationPrincipal UserDetails principal,
                                              @PathVariable UUID manuscriptId,
                                              @PathVariable UUID branchId) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedManuscript(manuscriptId, user);
        Map<String, Object> branch = requireBranch(manuscriptId, branchId);
        if (Boolean.TRUE.equals(branch.get("isMain"))) {
            throw new RuntimeException("主分支不能废弃");
        }
        branch.put("status", "abandoned");
        branch.put("updatedAt", Instant.now());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/me/auto-save-config")
    public Map<String, Object> getAutoSave(@AuthenticationPrincipal UserDetails principal) {
        User user = accessGuard.currentUser(principal);
        return autoSaveByUser.computeIfAbsent(user.getId(), uid -> defaultAutoSave(uid));
    }

    @PutMapping("/users/me/auto-save-config")
    public Map<String, Object> updateAutoSave(@AuthenticationPrincipal UserDetails principal,
                                              @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        Map<String, Object> config = autoSaveByUser.computeIfAbsent(user.getId(), uid -> defaultAutoSave(uid));
        int interval = intVal(payload.get("autoSaveIntervalSeconds"), intVal(config.get("autoSaveIntervalSeconds"), 300));
        int maxAuto = intVal(payload.get("maxAutoVersions"), intVal(config.get("maxAutoVersions"), 100));
        if (interval < 30) {
            throw new RuntimeException("autoSaveIntervalSeconds 不能小于 30");
        }
        if (maxAuto < 10) {
            throw new RuntimeException("maxAutoVersions 不能小于 10");
        }
        config.put("autoSaveIntervalSeconds", interval);
        config.put("maxAutoVersions", maxAuto);
        config.put("updatedAt", Instant.now());
        return config;
    }

    private void ensureMainBranchAndInitialVersion(Manuscript manuscript, User user) {
        UUID manuscriptId = manuscript.getId();
        Object lock = initLocks.computeIfAbsent(manuscriptId, key -> new Object());
        synchronized (lock) {
            ConcurrentMap<UUID, Map<String, Object>> branches = branchByManuscript.computeIfAbsent(manuscriptId, key -> new ConcurrentHashMap<>());

            Map<String, Object> mainBranch = null;
            if (branches.isEmpty()) {
                mainBranch = createMainBranch(manuscriptId);
                branches.put((UUID) mainBranch.get("id"), mainBranch);
            } else {
                List<Map<String, Object>> mains = branches.values().stream()
                        .filter(branch -> Boolean.TRUE.equals(branch.get("isMain")))
                        .sorted(Comparator.comparing(branch -> (Instant) branch.get("createdAt")))
                        .toList();
                if (mains.isEmpty()) {
                    mainBranch = createMainBranch(manuscriptId);
                    branches.put((UUID) mainBranch.get("id"), mainBranch);
                } else {
                    mainBranch = mains.get(0);
                    for (int i = 1; i < mains.size(); i++) {
                        mains.get(i).put("isMain", false);
                        mains.get(i).put("updatedAt", Instant.now());
                    }
                }
            }

            UUID currentBranchId = manuscript.getCurrentBranchId();
            if (currentBranchId == null || !branches.containsKey(currentBranchId)) {
                manuscript.setCurrentBranchId((UUID) mainBranch.get("id"));
                manuscriptRepository.save(manuscript);
            }

            ConcurrentMap<UUID, Map<String, Object>> versions = versionByManuscript.computeIfAbsent(manuscriptId, key -> new ConcurrentHashMap<>());
            if (versions.isEmpty()) {
                Map<String, Object> initial = buildVersion(manuscript, user, manuscript.getCurrentBranchId(), "initial", "auto", Map.of("bootstrap", true));
                versions.put((UUID) initial.get("id"), initial);
            }
        }
    }

    private Map<String, Object> createMainBranch(UUID manuscriptId) {
        Map<String, Object> main = new HashMap<>();
        main.put("id", UUID.randomUUID());
        main.put("manuscriptId", manuscriptId);
        main.put("name", "main");
        main.put("description", "默认主分支");
        main.put("sourceVersionId", null);
        main.put("status", "active");
        main.put("isMain", true);
        main.put("createdAt", Instant.now());
        main.put("updatedAt", Instant.now());
        return main;
    }

    private Map<String, Object> buildVersion(Manuscript manuscript,
                                             User user,
                                             UUID branchId,
                                             Object label,
                                             String snapshotType,
                                             Object metadata) {
        return buildVersionWithSections(
                manuscript.getId(),
                user,
                branchId,
                label,
                snapshotType,
                str(manuscript.getSectionsJson(), "{}"),
                metadata
        );
    }

    private Map<String, Object> buildVersionWithSections(UUID manuscriptId,
                                                         User user,
                                                         UUID branchId,
                                                         Object label,
                                                         String snapshotType,
                                                         String sectionsJson,
                                                         Object metadata) {
        UUID id = UUID.randomUUID();
        int versionNumber = nextVersionNumber(manuscriptId, branchId);
        UUID parentVersionId = latestVersionId(manuscriptId);

        Map<String, Object> version = new HashMap<>();
        version.put("id", id);
        version.put("manuscriptId", manuscriptId);
        version.put("branchId", branchId);
        version.put("versionNumber", versionNumber);
        version.put("label", label == null ? "v" + versionNumber : label.toString());
        version.put("snapshotType", snapshotType);
        version.put("contentHash", sha256(sectionsJson));
        version.put("sectionsJson", sectionsJson);
        version.put("metadata", metadata == null ? Map.of() : metadata);
        version.put("parentVersionId", parentVersionId);
        version.put("createdBy", user.getId());
        version.put("createdAt", Instant.now());
        return version;
    }

    private void cleanupAutoSnapshots(UUID manuscriptId, UUID branchId, int maxAutoVersions) {
        if (maxAutoVersions <= 0) {
            return;
        }
        List<Map<String, Object>> autos = new ArrayList<>();
        for (Map<String, Object> version : versionByManuscript.computeIfAbsent(manuscriptId, key -> new ConcurrentHashMap<>()).values()) {
            if (!Objects.equals(version.get("branchId"), branchId)) {
                continue;
            }
            if (!"auto".equalsIgnoreCase(str(version.get("snapshotType"), ""))) {
                continue;
            }
            autos.add(version);
        }
        autos.sort(Comparator.comparing(v -> (Instant) v.get("createdAt")));
        int overflow = autos.size() - maxAutoVersions;
        if (overflow <= 0) {
            return;
        }
        ConcurrentMap<UUID, Map<String, Object>> versions = versionByManuscript.computeIfAbsent(manuscriptId, key -> new ConcurrentHashMap<>());
        for (int i = 0; i < overflow; i++) {
            UUID id = (UUID) autos.get(i).get("id");
            versions.remove(id);
        }
    }

    private int nextVersionNumber(UUID manuscriptId, UUID branchId) {
        int max = 0;
        for (Map<String, Object> version : versionByManuscript.computeIfAbsent(manuscriptId, key -> new ConcurrentHashMap<>()).values()) {
            if (Objects.equals(version.get("branchId"), branchId)) {
                max = Math.max(max, intVal(version.get("versionNumber"), 0));
            }
        }
        return max + 1;
    }

    private UUID latestVersionId(UUID manuscriptId) {
        Instant latest = null;
        UUID latestId = null;
        for (Map<String, Object> version : versionByManuscript.computeIfAbsent(manuscriptId, key -> new ConcurrentHashMap<>()).values()) {
            Object createdAt = version.get("createdAt");
            if (!(createdAt instanceof Instant instant)) {
                continue;
            }
            if (latest == null || instant.isAfter(latest)) {
                latest = instant;
                latestId = (UUID) version.get("id");
            }
        }
        return latestId;
    }

    private Map<String, Object> latestVersion(UUID manuscriptId, UUID branchId) {
        Map<String, Object> latestVersion = null;
        Instant latest = null;
        for (Map<String, Object> version : versionByManuscript.computeIfAbsent(manuscriptId, key -> new ConcurrentHashMap<>()).values()) {
            if (!Objects.equals(version.get("branchId"), branchId)) {
                continue;
            }
            Object createdAt = version.get("createdAt");
            if (!(createdAt instanceof Instant instant)) {
                continue;
            }
            if (latest == null || instant.isAfter(latest)) {
                latest = instant;
                latestVersion = version;
            }
        }
        return latestVersion;
    }

    private UUID mainBranchId(UUID manuscriptId) {
        for (Map<String, Object> branch : branchByManuscript.computeIfAbsent(manuscriptId, key -> new ConcurrentHashMap<>()).values()) {
            if (Boolean.TRUE.equals(branch.get("isMain"))) {
                return (UUID) branch.get("id");
            }
        }
        return null;
    }

    private Map<String, Object> requireVersion(UUID manuscriptId, UUID versionId) {
        Map<String, Object> version = versionByManuscript.computeIfAbsent(manuscriptId, key -> new ConcurrentHashMap<>()).get(versionId);
        if (version == null) {
            throw new RuntimeException("版本不存在");
        }
        return version;
    }

    private Map<String, Object> requireBranch(UUID manuscriptId, UUID branchId) {
        Map<String, Object> branch = branchByManuscript.computeIfAbsent(manuscriptId, key -> new ConcurrentHashMap<>()).get(branchId);
        if (branch == null) {
            throw new RuntimeException("分支不存在");
        }
        return branch;
    }

    private List<Map<String, Object>> sortedVersions(UUID manuscriptId) {
        List<Map<String, Object>> versions = new ArrayList<>(versionByManuscript.computeIfAbsent(manuscriptId, key -> new ConcurrentHashMap<>()).values());
        versions.sort((a, b) -> {
            Instant ai = (Instant) a.get("createdAt");
            Instant bi = (Instant) b.get("createdAt");
            return bi.compareTo(ai);
        });
        return versions;
    }

    private List<Map<String, Object>> sortedBranches(UUID manuscriptId) {
        List<Map<String, Object>> branches = new ArrayList<>(branchByManuscript.computeIfAbsent(manuscriptId, key -> new ConcurrentHashMap<>()).values());
        branches.sort((a, b) -> {
            if (Objects.equals(a.get("isMain"), b.get("isMain"))) {
                Instant ai = (Instant) a.get("createdAt");
                Instant bi = (Instant) b.get("createdAt");
                return ai.compareTo(bi);
            }
            return Boolean.TRUE.equals(a.get("isMain")) ? -1 : 1;
        });
        return branches;
    }

    private Map<String, Object> defaultAutoSave(UUID userId) {
        Map<String, Object> config = new HashMap<>();
        config.put("id", UUID.randomUUID());
        config.put("userId", userId);
        config.put("autoSaveIntervalSeconds", 300);
        config.put("maxAutoVersions", 100);
        config.put("createdAt", Instant.now());
        config.put("updatedAt", Instant.now());
        return config;
    }

    private Map<String, String> parseSections(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            return new HashMap<>();
        }
    }

    private String writeSections(Map<String, String> sections) {
        try {
            return objectMapper.writeValueAsString(sections);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> result = new HashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return result;
        }
        return new HashMap<>();
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

    private String str(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? fallback : text;
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
