package com.ainovel.app.admin;

import com.ainovel.app.admin.ops.OpsRecordFileSink;
import com.ainovel.app.material.MaterialAdminService;
import com.ainovel.app.material.MaterialAdminReadRepository;
import com.ainovel.app.material.MaterialDuplicateService;
import com.ainovel.app.material.dto.MaterialDto;
import com.ainovel.app.material.dto.MaterialMergeRequest;
import com.ainovel.app.material.dto.MaterialReviewRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/admin")
@PreAuthorize("hasAuthority('AUTH_LOCAL_ADMIN')")
@Tag(name = "Admin Operations", description = "AINovel 业务运营后台接口")
@SecurityRequirement(name = "adminSessionCookie")
public class AdminOperationsController {
    private final MaterialAdminService materialService;
    private final AdminOperationsQueryService adminOperationsQueryService;
    private final OpsRecordFileSink recordFileSink;

    public AdminOperationsController(
            MaterialAdminService materialService,
            AdminOperationsQueryService adminOperationsQueryService,
            OpsRecordFileSink recordFileSink
    ) {
        this.materialService = materialService;
        this.adminOperationsQueryService = adminOperationsQueryService;
        this.recordFileSink = recordFileSink;
    }

    @GetMapping("/materials/pending")
    @Operation(summary = "待审素材列表")
    public MaterialAdminReadRepository.Page pendingMaterials(@RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        return materialService.pending(page, size);
    }

    @PostMapping("/materials/{id}/approve")
    @Operation(summary = "通过素材审核")
    public MaterialDto approveMaterial(@PathVariable UUID id, @RequestBody MaterialReviewRequest request) {
        MaterialDto result = materialService.review(id, "approve", request);
        return result;
    }

    @PostMapping("/materials/{id}/reject")
    @Operation(summary = "驳回素材审核")
    public MaterialDto rejectMaterial(@PathVariable UUID id, @RequestBody MaterialReviewRequest request) {
        MaterialDto result = materialService.review(id, "reject", request);
        return result;
    }

    @PostMapping("/materials/duplicates")
    @Operation(summary = "素材重复检测")
    public MaterialDuplicateService.Job duplicateMaterials() {
        return materialService.findDuplicates();
    }

    @GetMapping("/materials/duplicates/{id}")
    public MaterialDuplicateService.Job duplicateStatus(@PathVariable UUID id) { return materialService.duplicateStatus(id); }

    @GetMapping("/materials/duplicates/{id}/results")
    public MaterialDuplicateService.ResultPage duplicateResults(@PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return materialService.duplicateResults(id, page, size);
    }

    @PostMapping("/materials/duplicates/{id}/cancel")
    public MaterialDuplicateService.Job cancelDuplicates(@PathVariable UUID id) { return materialService.cancelDuplicates(id); }

    @PostMapping("/materials/merge")
    @Operation(summary = "合并素材")
    public MaterialDto mergeMaterial(@RequestBody MaterialMergeRequest request) {
        MaterialDto result = materialService.merge(request);
        return result;
    }

    @GetMapping("/materials/{id}/citations")
    @Operation(summary = "素材引用查询")
    public List<Map<String, Object>> materialCitations(@PathVariable UUID id) {
        return materialService.citations(id);
    }

    @GetMapping("/assets/summary")
    @Operation(summary = "创作资产汇总")
    public Map<String, Object> assetSummary() {
        return adminOperationsQueryService.assetSummary();
    }

    @GetMapping("/assets/stories")
    @Operation(summary = "故事资产只读列表")
    public AdminOperationsQueryService.PageResult<AdminOperationsReadRepository.AssetRow> stories(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String search) {
        return adminOperationsQueryService.assets("stories", page, size, search);
    }

    @GetMapping("/assets/worlds")
    @Operation(summary = "世界观资产只读列表")
    public AdminOperationsQueryService.PageResult<AdminOperationsReadRepository.AssetRow> worlds(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String search) {
        return adminOperationsQueryService.assets("worlds", page, size, search);
    }

    @GetMapping("/assets/manuscripts")
    @Operation(summary = "稿件资产只读列表")
    public AdminOperationsQueryService.PageResult<AdminOperationsReadRepository.AssetRow> manuscripts(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String search) {
        return adminOperationsQueryService.assets("manuscripts", page, size, search);
    }

    @GetMapping("/quality/runs")
    @Operation(summary = "质量巡检运行记录")
    public AdminOperationsQueryService.PageResult<AdminOperationsReadRepository.QualityRow> qualityRuns(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "all") String filter) {
        return adminOperationsQueryService.qualityRuns(page, size, search, filter);
    }

    private void audit(String action, String targetType, String targetId) {
        recordFileSink.appendAudit(Map.of(
                "category", "admin",
                "action", action,
                "actor", "admin",
                "targetType", targetType,
                "targetId", targetId,
                "result", "SUCCESS",
                "severity", "INFO"
        ));
    }
}
