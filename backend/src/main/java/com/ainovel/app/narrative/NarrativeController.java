package com.ainovel.app.narrative;
import com.ainovel.app.security.ResourceAccessGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import static com.ainovel.app.narrative.NarrativeDtos.*;

@RestController
@RequestMapping("/v2/manuscripts/{manuscriptId}/branches/{branchId}/narrative")
@Tag(name="Narrative", description="有证据的正文确认与叙事状态")
@SecurityRequirement(name="bearerAuth")
public class NarrativeController {
    private final ResourceAccessGuard access;
    private final NarrativeService narrative;
    private final NarrativeApprovalCoordinator approvals;
    public NarrativeController(ResourceAccessGuard access, NarrativeService narrative, NarrativeApprovalCoordinator approvals) {
        this.access = access; this.narrative = narrative; this.approvals = approvals;
    }
    @Operation(summary="确认场景正文并异步提取候选")
    @PostMapping("/scene-approvals")
    public Approved approve(@AuthenticationPrincipal UserDetails principal, @PathVariable UUID manuscriptId,
                            @PathVariable UUID branchId, @RequestHeader("Idempotency-Key") String key,
                            @Valid @RequestBody ApprovalRequest request) {
        return approvals.approve(access.currentUser(principal), manuscriptId, branchId, request, key);
    }
    @Operation(summary="读取抽取与审阅结果")
    @GetMapping("/extractions/{id}")
    public ExtractionView extraction(@AuthenticationPrincipal UserDetails principal, @PathVariable UUID manuscriptId,
                                     @PathVariable UUID branchId, @PathVariable UUID id) {
        return narrative.extraction(access.currentUser(principal), manuscriptId, branchId, id);
    }
    @Operation(summary="原子提交作者对全部候选的审阅决定")
    @PostMapping("/extractions/{id}/review")
    public ReviewResult review(@AuthenticationPrincipal UserDetails principal, @PathVariable UUID manuscriptId,
                               @PathVariable UUID branchId, @PathVariable UUID id,
                               @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody ReviewRequest request) {
        return narrative.review(access.currentUser(principal), manuscriptId, branchId, id, request, key);
    }
    @Operation(summary="查询当前或历史账本；当前来源变化会标记待复核")
    @GetMapping("/state")
    public StateView state(@AuthenticationPrincipal UserDetails principal, @PathVariable UUID manuscriptId,
                           @PathVariable UUID branchId, @RequestParam(required=false) UUID sceneId,
                           @RequestParam(required=false) UUID characterId, @RequestParam(required=false) Kind kind,
                           @RequestParam(required=false) String status, @RequestParam(required=false) Long canonRevision) {
        return narrative.state(access.currentUser(principal), manuscriptId, branchId, sceneId, characterId, kind, status, canonRevision);
    }
    @Operation(summary="读取不可变正文证据及 Unicode 定位信息")
    @GetMapping("/scene-approvals/{id}/evidence")
    public EvidenceView evidence(@AuthenticationPrincipal UserDetails principal, @PathVariable UUID manuscriptId,
                                 @PathVariable UUID branchId, @PathVariable UUID id) {
        return narrative.evidence(access.currentUser(principal), manuscriptId, branchId, id);
    }
}
