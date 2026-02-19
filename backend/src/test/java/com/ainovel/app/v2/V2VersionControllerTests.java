package com.ainovel.app.v2;

import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class V2VersionControllerTests {

    private V2AccessGuard accessGuard;
    private ManuscriptRepository manuscriptRepository;
    private V2VersionController controller;
    private UserDetails principal;
    private User user;
    private Manuscript manuscript;
    private UUID manuscriptId;

    @BeforeEach
    void setUp() {
        accessGuard = mock(V2AccessGuard.class);
        manuscriptRepository = mock(ManuscriptRepository.class);
        controller = new V2VersionController(accessGuard, manuscriptRepository);

        principal = mock(UserDetails.class);
        user = new User();
        user.setId(UUID.randomUUID());

        manuscript = new Manuscript();
        manuscriptId = UUID.randomUUID();
        manuscript.setId(manuscriptId);
        manuscript.setSectionsJson("{\"scene-1\":\"hello\"}");

        when(accessGuard.currentUser(any())).thenReturn(user);
        when(accessGuard.requireOwnedManuscript(manuscriptId, user)).thenReturn(manuscript);
        when(manuscriptRepository.save(any(Manuscript.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createVersionShouldBootstrapMainBranchAndInitialVersion() {
        controller.listVersions(principal, manuscriptId);
        manuscript.setSectionsJson("{\"scene-1\":\"checkpoint-content\"}");
        Map<String, Object> created = controller.createVersion(principal, manuscriptId, Map.of("label", "checkpoint"));
        List<Map<String, Object>> versions = controller.listVersions(principal, manuscriptId);

        assertNotNull(manuscript.getCurrentBranchId(), "应自动初始化 main 分支");
        assertEquals("checkpoint", created.get("label"));
        assertEquals(2, versions.size(), "应包含初始版本与新建版本");
    }

    @Test
    void updateAutoSaveShouldValidateLowerBound() {
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                controller.updateAutoSave(principal, Map.of(
                        "autoSaveIntervalSeconds", 10,
                        "maxAutoVersions", 100
                ))
        );
        assertTrue(ex.getMessage().contains("不能小于 30"));
    }

    @Test
    void mergeMainBranchShouldFail() {
        List<Map<String, Object>> branches = controller.listBranches(principal, manuscriptId);
        UUID mainBranchId = (UUID) branches.get(0).get("id");

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                controller.mergeBranch(principal, manuscriptId, mainBranchId, Map.of())
        );
        assertTrue(ex.getMessage().contains("主分支无需合并"));
    }

    @Test
    void rollbackShouldCreateBackupAndRollbackRecord() {
        List<Map<String, Object>> initial = controller.listVersions(principal, manuscriptId);
        UUID targetVersionId = (UUID) initial.get(0).get("id");

        manuscript.setSectionsJson("{\"scene-1\":\"changed\"}");
        controller.createVersion(principal, manuscriptId, Map.of("label", "changed"));

        Map<String, Object> rollback = controller.rollback(principal, manuscriptId, targetVersionId);
        List<Map<String, Object>> versions = controller.listVersions(principal, manuscriptId);

        assertNotNull(rollback.get("backupVersionId"));
        assertNotNull(rollback.get("rollbackVersionId"));
        assertEquals("{\"scene-1\":\"hello\"}", manuscript.getSectionsJson());
        assertTrue(versions.size() >= 4, "应包含初始版本、变更版本、回滚备份和回滚记录");
    }

    @Test
    void autoSnapshotsShouldRespectMaxLimit() {
        controller.updateAutoSave(principal, Map.of(
                "autoSaveIntervalSeconds", 30,
                "maxAutoVersions", 10
        ));

        for (int i = 0; i < 15; i++) {
            manuscript.setSectionsJson("{\"scene-1\":\"auto-" + i + "\"}");
            controller.createVersion(principal, manuscriptId, Map.of(
                    "snapshotType", "auto",
                    "label", "auto-" + i
            ));
        }

        List<Map<String, Object>> versions = controller.listVersions(principal, manuscriptId);
        long autoCount = versions.stream()
                .filter(v -> "auto".equals(v.get("snapshotType")))
                .count();
        assertTrue(autoCount <= 10, "自动快照数量应受 maxAutoVersions 限制");
    }

    @Test
    void ensureMainBranchShouldNormalizeDuplicateMainBranches() throws Exception {
        ConcurrentMap<UUID, ConcurrentMap<UUID, Map<String, Object>>> store = branchStore();
        ConcurrentMap<UUID, Map<String, Object>> branches = new ConcurrentHashMap<>();

        UUID firstMainId = UUID.randomUUID();
        UUID secondMainId = UUID.randomUUID();
        branches.put(firstMainId, branch(firstMainId, true, Instant.now().minusSeconds(60)));
        branches.put(secondMainId, branch(secondMainId, true, Instant.now()));
        store.put(manuscriptId, branches);
        manuscript.setCurrentBranchId(null);

        List<Map<String, Object>> branchList = controller.listBranches(principal, manuscriptId);
        long mainCount = branchList.stream().filter(b -> Boolean.TRUE.equals(b.get("isMain"))).count();

        assertEquals(1L, mainCount, "重复 main 分支应被自动收敛为一个");
        assertEquals(firstMainId, manuscript.getCurrentBranchId(), "应保留最早创建的 main 分支");
    }

    @SuppressWarnings("unchecked")
    private ConcurrentMap<UUID, ConcurrentMap<UUID, Map<String, Object>>> branchStore() throws Exception {
        Field field = V2VersionController.class.getDeclaredField("branchByManuscript");
        field.setAccessible(true);
        return (ConcurrentMap<UUID, ConcurrentMap<UUID, Map<String, Object>>>) field.get(controller);
    }

    private Map<String, Object> branch(UUID id, boolean isMain, Instant createdAt) {
        return new ConcurrentHashMap<>(Map.of(
                "id", id,
                "manuscriptId", manuscriptId,
                "name", "main",
                "description", "main branch",
                "sourceVersionId", "",
                "status", "active",
                "isMain", isMain,
                "createdAt", createdAt,
                "updatedAt", createdAt
        ));
    }
}
