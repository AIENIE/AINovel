package com.ainovel.app.v2;

import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class V2VersionControllerTests {

    private ResourceAccessGuard accessGuard;
    private V2VersionPersistenceService versionService;
    private V2VersionController controller;
    private UserDetails principal;
    private User user;
    private Manuscript manuscript;
    private UUID manuscriptId;

    @BeforeEach
    void setUp() {
        accessGuard = mock(ResourceAccessGuard.class);
        versionService = mock(V2VersionPersistenceService.class);
        controller = new V2VersionController(accessGuard, versionService);

        principal = mock(UserDetails.class);
        user = new User();
        user.setId(UUID.randomUUID());

        manuscript = new Manuscript();
        manuscriptId = UUID.randomUUID();
        manuscript.setId(manuscriptId);

        when(accessGuard.currentUser(any())).thenReturn(user);
        when(accessGuard.requireOwnedManuscript(manuscriptId, user)).thenReturn(manuscript);
    }

    @Test
    void controllerShouldNotKeepInMemoryFallbackState() {
        List<String> fieldNames = Arrays.stream(V2VersionController.class.getDeclaredFields())
                .map(Field::getName)
                .toList();

        assertFalse(fieldNames.contains("branchByManuscript"));
        assertFalse(fieldNames.contains("versionByManuscript"));
        assertFalse(fieldNames.contains("diffCache"));
        assertFalse(fieldNames.contains("autoSaveByUser"));
        assertFalse(fieldNames.contains("initLocks"));
    }

    @Test
    void createVersionShouldDelegateEmptyPayloadWhenBodyMissing() {
        Map<String, Object> version = Map.of("id", UUID.randomUUID(), "label", "v1");
        when(versionService.createVersion(manuscript, user, Map.of())).thenReturn(version);

        Map<String, Object> result = controller.createVersion(principal, manuscriptId, null);

        assertEquals(version.get("id"), result.get("id"));
        verify(accessGuard).requireOwnedManuscript(manuscriptId, user);
        verify(versionService).createVersion(manuscript, user, Map.of());
    }

    @Test
    void rollbackShouldDelegateWithResolvedManuscript() {
        UUID versionId = UUID.randomUUID();
        Map<String, Object> rollback = Map.of("rolledBackTo", versionId, "status", "completed");
        when(versionService.rollback(manuscript, user, versionId)).thenReturn(rollback);

        Map<String, Object> result = controller.rollback(principal, manuscriptId, versionId);

        assertEquals("completed", result.get("status"));
        verify(accessGuard).requireOwnedManuscript(manuscriptId, user);
        verify(versionService).rollback(manuscript, user, versionId);
    }

    @Test
    void updateBranchShouldCheckOwnershipAndDelegate() {
        UUID branchId = UUID.randomUUID();
        Map<String, Object> payload = Map.of("name", "rewrite");
        Map<String, Object> branch = Map.of("id", branchId, "name", "rewrite");
        when(versionService.updateBranch(manuscriptId, branchId, payload)).thenReturn(branch);

        Map<String, Object> result = controller.updateBranch(principal, manuscriptId, branchId, payload);

        assertEquals("rewrite", result.get("name"));
        verify(accessGuard).requireOwnedManuscript(manuscriptId, user);
        verify(versionService).updateBranch(manuscriptId, branchId, payload);
    }

    @Test
    void mergeBranchShouldDelegateEmptyPayloadWhenBodyMissing() {
        UUID branchId = UUID.randomUUID();
        UUID mergeVersionId = UUID.randomUUID();
        Map<String, Object> merged = Map.of("mergeVersionId", mergeVersionId, "status", "merged");
        when(versionService.mergeBranch(manuscript, user, branchId, Map.of())).thenReturn(merged);

        Map<String, Object> result = controller.mergeBranch(principal, manuscriptId, branchId, null);

        assertEquals("merged", result.get("status"));
        verify(accessGuard).requireOwnedManuscript(manuscriptId, user);
        verify(versionService).mergeBranch(manuscript, user, branchId, Map.of());
    }

    @Test
    void abandonBranchShouldDelegateAndReturnNoContent() {
        UUID branchId = UUID.randomUUID();

        var response = controller.abandonBranch(principal, manuscriptId, branchId);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(accessGuard).requireOwnedManuscript(manuscriptId, user);
        verify(versionService).abandonBranch(manuscriptId, branchId);
    }

    @Test
    void updateAutoSaveShouldDelegatePayload() {
        Map<String, Object> payload = Map.of(
                "autoSaveIntervalSeconds", 30,
                "maxAutoVersions", 10
        );
        Map<String, Object> config = Map.of(
                "autoSaveIntervalSeconds", 30,
                "maxAutoVersions", 10
        );
        when(versionService.updateAutoSave(user, payload)).thenReturn(config);

        Map<String, Object> result = controller.updateAutoSave(principal, payload);

        assertEquals(30, result.get("autoSaveIntervalSeconds"));
        verify(versionService).updateAutoSave(user, payload);
    }
}
