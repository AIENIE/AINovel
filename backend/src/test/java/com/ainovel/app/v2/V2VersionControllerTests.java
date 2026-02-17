package com.ainovel.app.v2;

import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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
}
