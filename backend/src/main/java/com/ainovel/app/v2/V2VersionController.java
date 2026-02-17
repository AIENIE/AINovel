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

        Map<String, Object> version = buildVersion(manuscript, user, branchId,
                payload == null ? null : payload.get("label"),
                str(payload == null ? null : payload.get("snapshotType"), "manual"),
                payload == null ? null : payload.get("metadata"));
        versionByManuscript.computeIfAbsent(manuscriptId, key -> new ConcurrentHashMap<>())
                .put((UUID) version.get("id"), version);
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

        manuscript.setSectionsJson(str(version.get("sectionsJson"), "{}"));
        manuscriptRepository.save(manuscript);

        Map<String, Object> result = new HashMap<>();
        result.put("manuscriptId", manuscriptId);
        result.put("rolledBackTo", versionId);
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
                changes.add(Map.of(
                        "sceneId", sceneId,
                        "beforeLength", before.length(),
                        "afterLength", after.length(),
                        "delta", after.length() - before.length()
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

        branch.put("status", "merged");
        branch.put("updatedAt", Instant.now());

        Map<String, Object> mergeVersion = buildVersion(
                manuscript,
                user,
                manuscript.getCurrentBranchId(),
                payload == null ? "merge:" + branch.get("name") : payload.get("label"),
                "merge",
                Map.of("sourceBranchId", branchId)
        );
        versionByManuscript.computeIfAbsent(manuscriptId, key -> new ConcurrentHashMap<>())
                .put((UUID) mergeVersion.get("id"), mergeVersion);

        return Map.of(
                "manuscriptId", manuscriptId,
                "sourceBranchId", branchId,
                "targetBranchId", manuscript.getCurrentBranchId(),
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
        ConcurrentMap<UUID, Map<String, Object>> branches = branchByManuscript.computeIfAbsent(manuscriptId, key -> new ConcurrentHashMap<>());
        if (branches.isEmpty()) {
            UUID mainBranchId = UUID.randomUUID();
            Map<String, Object> main = new HashMap<>();
            main.put("id", mainBranchId);
            main.put("manuscriptId", manuscriptId);
            main.put("name", "main");
            main.put("description", "默认主分支");
            main.put("sourceVersionId", null);
            main.put("status", "active");
            main.put("isMain", true);
            main.put("createdAt", Instant.now());
            main.put("updatedAt", Instant.now());
            branches.put(mainBranchId, main);

            manuscript.setCurrentBranchId(mainBranchId);
            manuscriptRepository.save(manuscript);
        }

        if (manuscript.getCurrentBranchId() == null) {
            Optional<Map<String, Object>> main = branches.values().stream().filter(branch -> Boolean.TRUE.equals(branch.get("isMain"))).findFirst();
            if (main.isPresent()) {
                manuscript.setCurrentBranchId((UUID) main.get().get("id"));
                manuscriptRepository.save(manuscript);
            }
        }

        ConcurrentMap<UUID, Map<String, Object>> versions = versionByManuscript.computeIfAbsent(manuscriptId, key -> new ConcurrentHashMap<>());
        if (versions.isEmpty()) {
            Map<String, Object> initial = buildVersion(manuscript, user, manuscript.getCurrentBranchId(), "initial", "auto", Map.of("bootstrap", true));
            versions.put((UUID) initial.get("id"), initial);
        }
    }

    private Map<String, Object> buildVersion(Manuscript manuscript,
                                             User user,
                                             UUID branchId,
                                             Object label,
                                             String snapshotType,
                                             Object metadata) {
        UUID manuscriptId = manuscript.getId();
        UUID id = UUID.randomUUID();
        int versionNumber = nextVersionNumber(manuscriptId, branchId);
        UUID parentVersionId = latestVersionId(manuscriptId);
        String sectionsJson = str(manuscript.getSectionsJson(), "{}");

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
