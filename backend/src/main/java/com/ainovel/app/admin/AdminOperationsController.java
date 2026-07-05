package com.ainovel.app.admin;

import com.ainovel.app.admin.ops.OpsRecordFileSink;
import com.ainovel.app.material.MaterialService;
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
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Admin Operations", description = "AINovel 业务运营后台接口")
@SecurityRequirement(name = "bearerAuth")
public class AdminOperationsController {
    private final MaterialService materialService;
    private final AdminOperationsQueryService adminOperationsQueryService;
    private final OpsRecordFileSink recordFileSink;

    public AdminOperationsController(
            MaterialService materialService,
            AdminOperationsQueryService adminOperationsQueryService,
            OpsRecordFileSink recordFileSink
    ) {
        this.materialService = materialService;
        this.adminOperationsQueryService = adminOperationsQueryService;
        this.recordFileSink = recordFileSink;
    }

    @GetMapping("/materials/pending")
    @Operation(summary = "待审素材列表")
    public List<MaterialDto> pendingMaterials() {
        return materialService.pending();
    }

    @PostMapping("/materials/{id}/approve")
    @Operation(summary = "通过素材审核")
    public MaterialDto approveMaterial(@PathVariable UUID id, @RequestBody MaterialReviewRequest request) {
        MaterialDto result = materialService.review(id, "approve", request);
        audit("material.approve", "material", String.valueOf(id));
        return result;
    }

    @PostMapping("/materials/{id}/reject")
    @Operation(summary = "驳回素材审核")
    public MaterialDto rejectMaterial(@PathVariable UUID id, @RequestBody MaterialReviewRequest request) {
        MaterialDto result = materialService.review(id, "reject", request);
        audit("material.reject", "material", String.valueOf(id));
        return result;
    }

    @PostMapping("/materials/duplicates")
    @Operation(summary = "素材重复检测")
    public List<Map<String, Object>> duplicateMaterials() {
        return materialService.findDuplicates();
    }

    @PostMapping("/materials/merge")
    @Operation(summary = "合并素材")
    public MaterialDto mergeMaterial(@RequestBody MaterialMergeRequest request) {
        MaterialDto result = materialService.merge(request);
        audit("material.merge", "material", result.id() == null ? "merged" : String.valueOf(result.id()));
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
    public List<Map<String, Object>> stories() {
        return adminOperationsQueryService.stories();
    }

    @GetMapping("/assets/worlds")
    @Operation(summary = "世界观资产只读列表")
    public List<Map<String, Object>> worlds() {
        return adminOperationsQueryService.worlds();
    }

    @GetMapping("/assets/manuscripts")
    @Operation(summary = "稿件资产只读列表")
    public List<Map<String, Object>> manuscripts() {
        return adminOperationsQueryService.manuscripts();
    }

    @GetMapping("/quality/runs")
    @Operation(summary = "质量巡检运行记录")
    public List<Map<String, Object>> qualityRuns() {
        return adminOperationsQueryService.qualityRuns();
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
