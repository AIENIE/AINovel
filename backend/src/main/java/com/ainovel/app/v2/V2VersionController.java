package com.ainovel.app.v2;

import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "V2", description = "AINovel v2 and quality APIs")
@RestController
@RequestMapping("/v2")
public class V2VersionController {
    private final ResourceAccessGuard accessGuard;
    private final V2VersionPersistenceService versionService;

    @Autowired
    public V2VersionController(ResourceAccessGuard accessGuard,
                               V2VersionPersistenceService versionService) {
        this.accessGuard = accessGuard;
        this.versionService = versionService;
    }

    @Operation(summary = "v2 API endpoint")
    @GetMapping("/manuscripts/{manuscriptId}/versions")
    public List<Map<String, Object>> listVersions(@AuthenticationPrincipal UserDetails principal,
                                                  @PathVariable UUID manuscriptId) {
        User user = accessGuard.currentUser(principal);
        Manuscript manuscript = accessGuard.requireOwnedManuscript(manuscriptId, user);
        return versionService.listVersions(manuscript, user);
    }

    @Operation(summary = "v2 API endpoint")
    @PostMapping("/manuscripts/{manuscriptId}/versions")
    public Map<String, Object> createVersion(@AuthenticationPrincipal UserDetails principal,
                                             @PathVariable UUID manuscriptId,
                                             @RequestBody(required = false) Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        Manuscript manuscript = accessGuard.requireOwnedManuscript(manuscriptId, user);
        return versionService.createVersion(manuscript, user, payload == null ? Map.of() : payload);
    }

    @Operation(summary = "v2 API endpoint")
    @GetMapping("/manuscripts/{manuscriptId}/versions/{versionId}")
    public Map<String, Object> getVersion(@AuthenticationPrincipal UserDetails principal,
                                          @PathVariable UUID manuscriptId,
                                          @PathVariable UUID versionId) {
        User user = accessGuard.currentUser(principal);
        Manuscript manuscript = accessGuard.requireOwnedManuscript(manuscriptId, user);
        return versionService.getVersion(manuscript, user, versionId);
    }

    @Operation(summary = "v2 API endpoint")
    @PostMapping("/manuscripts/{manuscriptId}/versions/{versionId}/rollback")
    public Map<String, Object> rollback(@AuthenticationPrincipal UserDetails principal,
                                        @PathVariable UUID manuscriptId,
                                        @PathVariable UUID versionId) {
        User user = accessGuard.currentUser(principal);
        Manuscript manuscript = accessGuard.requireOwnedManuscript(manuscriptId, user);
        return versionService.rollback(manuscript, user, versionId);
    }

    @Operation(summary = "v2 API endpoint")
    @GetMapping("/manuscripts/{manuscriptId}/versions/diff")
    public Map<String, Object> diff(@AuthenticationPrincipal UserDetails principal,
                                    @PathVariable UUID manuscriptId,
                                    @RequestParam UUID fromVersionId,
                                    @RequestParam UUID toVersionId) {
        User user = accessGuard.currentUser(principal);
        Manuscript manuscript = accessGuard.requireOwnedManuscript(manuscriptId, user);
        return versionService.diff(manuscript, user, fromVersionId, toVersionId);
    }

    @Operation(summary = "v2 API endpoint")
    @GetMapping("/manuscripts/{manuscriptId}/branches")
    public List<Map<String, Object>> listBranches(@AuthenticationPrincipal UserDetails principal,
                                                  @PathVariable UUID manuscriptId) {
        User user = accessGuard.currentUser(principal);
        Manuscript manuscript = accessGuard.requireOwnedManuscript(manuscriptId, user);
        return versionService.listBranches(manuscript, user);
    }

    @Operation(summary = "v2 API endpoint")
    @PostMapping("/manuscripts/{manuscriptId}/branches/{branchId}/checkout")
    public Map<String, Object> checkoutBranch(@AuthenticationPrincipal UserDetails principal,
                                              @PathVariable UUID manuscriptId,
                                              @PathVariable UUID branchId) {
        User user = accessGuard.currentUser(principal);
        Manuscript manuscript = accessGuard.requireOwnedManuscript(manuscriptId, user);
        return versionService.checkoutBranch(manuscript, user, branchId);
    }

    @Operation(summary = "v2 API endpoint")
    @PostMapping("/manuscripts/{manuscriptId}/branches")
    public Map<String, Object> createBranch(@AuthenticationPrincipal UserDetails principal,
                                            @PathVariable UUID manuscriptId,
                                            @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        Manuscript manuscript = accessGuard.requireOwnedManuscript(manuscriptId, user);
        return versionService.createBranch(manuscript, user, payload);
    }

    @Operation(summary = "v2 API endpoint")
    @PutMapping("/manuscripts/{manuscriptId}/branches/{branchId}")
    public Map<String, Object> updateBranch(@AuthenticationPrincipal UserDetails principal,
                                            @PathVariable UUID manuscriptId,
                                            @PathVariable UUID branchId,
                                            @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedManuscript(manuscriptId, user);
        return versionService.updateBranch(manuscriptId, branchId, payload);
    }

    @Operation(summary = "v2 API endpoint")
    @PostMapping("/manuscripts/{manuscriptId}/branches/{branchId}/merge")
    public Map<String, Object> mergeBranch(@AuthenticationPrincipal UserDetails principal,
                                           @PathVariable UUID manuscriptId,
                                           @PathVariable UUID branchId,
                                           @RequestBody(required = false) Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        Manuscript manuscript = accessGuard.requireOwnedManuscript(manuscriptId, user);
        return versionService.mergeBranch(manuscript, user, branchId, payload == null ? Map.of() : payload);
    }

    @Operation(summary = "v2 API endpoint")
    @DeleteMapping("/manuscripts/{manuscriptId}/branches/{branchId}")
    public ResponseEntity<Void> abandonBranch(@AuthenticationPrincipal UserDetails principal,
                                              @PathVariable UUID manuscriptId,
                                              @PathVariable UUID branchId) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedManuscript(manuscriptId, user);
        versionService.abandonBranch(manuscriptId, branchId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "v2 API endpoint")
    @GetMapping("/users/me/auto-save-config")
    public Map<String, Object> getAutoSave(@AuthenticationPrincipal UserDetails principal) {
        User user = accessGuard.currentUser(principal);
        return versionService.getAutoSave(user);
    }

    @Operation(summary = "v2 API endpoint")
    @PutMapping("/users/me/auto-save-config")
    public Map<String, Object> updateAutoSave(@AuthenticationPrincipal UserDetails principal,
                                              @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        return versionService.updateAutoSave(user, payload);
    }
}
