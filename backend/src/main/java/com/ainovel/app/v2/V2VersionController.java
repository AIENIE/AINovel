package com.ainovel.app.v2;

import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
    @org.springframework.beans.factory.annotation.Autowired
    private com.ainovel.app.manuscript.OwnedManuscriptTransactions manuscriptTransactions;
    private final ResourceAccessGuard accessGuard;
    private final V2VersionPersistenceService versionService;

    @Autowired
    public V2VersionController(ResourceAccessGuard accessGuard,
                               V2VersionPersistenceService versionService) {
        this.accessGuard = accessGuard;
        this.versionService = versionService;
    }

    @Operation(summary = "列出稿件历史版本")
    @GetMapping("/manuscripts/{manuscriptId}/versions")
    public List<Map<String, Object>> listVersions(@AuthenticationPrincipal UserDetails principal,
                                                  @PathVariable UUID manuscriptId) {
        User user = accessGuard.currentUser(principal);
        return manuscriptTransactions.write(manuscriptId, user, manuscript -> versionService.listVersions(manuscript, user));
    }

    @Operation(summary = "保存当前稿件版本", description = "从当前工作副本创建不可变快照；正文 JSON 无效时失败并回滚。")
    @PostMapping("/manuscripts/{manuscriptId}/versions")
    public Map<String, Object> createVersion(@AuthenticationPrincipal UserDetails principal,
                                             @PathVariable UUID manuscriptId,
                                             @RequestBody(required = false) V2RequestPayload payload) {
        User user = accessGuard.currentUser(principal);
        return manuscriptTransactions.write(manuscriptId, user, manuscript -> versionService.createVersion(manuscript, user, payload == null ? Map.of() : payload.asMap()));
    }

    @Operation(summary = "读取稿件历史版本")
    @GetMapping("/manuscripts/{manuscriptId}/versions/{versionId}")
    public Map<String, Object> getVersion(@AuthenticationPrincipal UserDetails principal,
                                          @PathVariable UUID manuscriptId,
                                          @PathVariable UUID versionId) {
        User user = accessGuard.currentUser(principal);
        return manuscriptTransactions.write(manuscriptId, user, manuscript -> versionService.getVersion(manuscript, user, versionId));
    }

    @Operation(summary = "回滚稿件到历史版本", description = "先保存当前工作副本快照，再在同一事务中替换正文；历史快照保持不可变。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "返回回滚后的工作副本"),
            @ApiResponse(responseCode = "400", description = "历史版本正文无效"),
            @ApiResponse(responseCode = "409", description = "分支或版本冲突")})
    @PostMapping("/manuscripts/{manuscriptId}/versions/{versionId}/rollback")
    public Map<String, Object> rollback(@AuthenticationPrincipal UserDetails principal,
                                        @PathVariable UUID manuscriptId,
                                        @PathVariable UUID versionId) {
        User user = accessGuard.currentUser(principal);
        return manuscriptTransactions.write(manuscriptId, user, manuscript -> versionService.rollback(manuscript, user, versionId));
    }

    @Operation(summary = "比较两个稿件版本")
    @GetMapping("/manuscripts/{manuscriptId}/versions/diff")
    public Map<String, Object> diff(@AuthenticationPrincipal UserDetails principal,
                                    @PathVariable UUID manuscriptId,
                                    @RequestParam UUID fromVersionId,
                                    @RequestParam UUID toVersionId) {
        User user = accessGuard.currentUser(principal);
        return manuscriptTransactions.write(manuscriptId, user, manuscript -> versionService.diff(manuscript, user, fromVersionId, toVersionId));
    }

    @Operation(summary = "列出稿件分支")
    @GetMapping("/manuscripts/{manuscriptId}/branches")
    public List<Map<String, Object>> listBranches(@AuthenticationPrincipal UserDetails principal,
                                                  @PathVariable UUID manuscriptId) {
        User user = accessGuard.currentUser(principal);
        return manuscriptTransactions.write(manuscriptId, user, manuscript -> versionService.listBranches(manuscript, user));
    }

    @Operation(summary = "切换当前工作分支", description = "切换前保存当前工作副本快照；在同一事务中替换场景集合。")
    @PostMapping("/manuscripts/{manuscriptId}/branches/{branchId}/checkout")
    public Map<String, Object> checkoutBranch(@AuthenticationPrincipal UserDetails principal,
                                              @PathVariable UUID manuscriptId,
                                              @PathVariable UUID branchId) {
        User user = accessGuard.currentUser(principal);
        return manuscriptTransactions.write(manuscriptId, user, manuscript -> versionService.checkoutBranch(manuscript, user, branchId));
    }

    @Operation(summary = "创建稿件分支", description = "使用明确的分支创建请求；非法字段或来源版本返回 4xx。")
    @PostMapping("/manuscripts/{manuscriptId}/branches")
    public Map<String, Object> createBranch(@AuthenticationPrincipal UserDetails principal,
                                            @PathVariable UUID manuscriptId,
                                            @RequestBody V2BranchRequests.CreateBranch payload) {
        User user = accessGuard.currentUser(principal);
        return manuscriptTransactions.write(manuscriptId, user, manuscript -> versionService.createBranch(manuscript, user, payload));
    }

    @Operation(summary = "更新稿件分支", description = "状态仅允许 active、abandoned、merged；非法状态返回 400。")
    @PutMapping("/manuscripts/{manuscriptId}/branches/{branchId}")
    public Map<String, Object> updateBranch(@AuthenticationPrincipal UserDetails principal,
                                            @PathVariable UUID manuscriptId,
                                            @PathVariable UUID branchId,
                                            @RequestBody V2BranchRequests.UpdateBranch payload) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedManuscript(manuscriptId, user);
        return versionService.updateBranch(manuscriptId, branchId, payload);
    }

    @Operation(summary = "合并稿件分支", description = "缺省策略为 REPLACE_ALL；显式策略仅允许 REPLACE_ALL、SCENE_SELECT，场景决议仅接受 source/target。合并前保存目标工作副本快照。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "合并结果或需人工解决的冲突"),
            @ApiResponse(responseCode = "400", description = "策略、状态或场景决议非法"),
            @ApiResponse(responseCode = "409", description = "稿件版本或分支状态冲突")})
    @PostMapping("/manuscripts/{manuscriptId}/branches/{branchId}/merge")
    public Map<String, Object> mergeBranch(@AuthenticationPrincipal UserDetails principal,
                                           @PathVariable UUID manuscriptId,
                                           @PathVariable UUID branchId,
                                           @RequestBody V2BranchRequests.MergeBranch payload) {
        User user = accessGuard.currentUser(principal);
        return manuscriptTransactions.write(manuscriptId, user, manuscript -> versionService.mergeBranch(manuscript, user, branchId, payload));
    }

    @Operation(summary = "放弃稿件分支")
    @DeleteMapping("/manuscripts/{manuscriptId}/branches/{branchId}")
    public ResponseEntity<Void> abandonBranch(@AuthenticationPrincipal UserDetails principal,
                                              @PathVariable UUID manuscriptId,
                                              @PathVariable UUID branchId) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedManuscript(manuscriptId, user);
        versionService.abandonBranch(manuscriptId, branchId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "读取自动保存配置")
    @GetMapping("/users/me/auto-save-config")
    public Map<String, Object> getAutoSave(@AuthenticationPrincipal UserDetails principal) {
        User user = accessGuard.currentUser(principal);
        return versionService.getAutoSave(user);
    }

    @Operation(summary = "更新自动保存配置")
    @PutMapping("/users/me/auto-save-config")
    public Map<String, Object> updateAutoSave(@AuthenticationPrincipal UserDetails principal,
                                              @RequestBody V2RequestPayload payload) {
        User user = accessGuard.currentUser(principal);
        return versionService.updateAutoSave(user, payload.asMap());
    }
}
