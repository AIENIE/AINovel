package com.ainovel.app.material;

import com.ainovel.app.material.dto.*;
import com.ainovel.app.user.User;
import com.ainovel.app.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/materials")
@Tag(name = "Material", description = "素材库管理、导入、审核与检索接口")
@SecurityRequirement(name = "bearerAuth")
public class MaterialController {
    @Autowired
    private MaterialService materialService;
    @Autowired
    private UserRepository userRepository;

    private User currentUser(UserDetails details) {
        return userRepository.findByUsername(details.getUsername()).orElseThrow();
    }

    @PostMapping
    @Operation(summary = "创建素材", description = "创建单条文本素材。")
    public MaterialDto create(@AuthenticationPrincipal UserDetails principal, @Valid @RequestBody MaterialCreateRequest request) {
        return materialService.create(currentUser(principal), request);
    }

    @GetMapping
    @Operation(summary = "获取素材列表", description = "查询当前用户素材。")
    public List<MaterialDto> list(@AuthenticationPrincipal UserDetails principal) { return materialService.list(currentUser(principal)); }

    @GetMapping("/{id}")
    @Operation(summary = "获取素材详情", description = "按素材 ID 查询详情。")
    public MaterialDto get(@PathVariable UUID id) { return materialService.get(id); }

    @PutMapping("/{id}")
    @Operation(summary = "更新素材", description = "更新素材内容、标签和状态。")
    public MaterialDto update(@PathVariable UUID id, @RequestBody MaterialUpdateRequest request) { return materialService.update(id, request); }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除素材", description = "删除指定素材。")
    public ResponseEntity<Void> delete(@PathVariable UUID id) { materialService.delete(id); return ResponseEntity.noContent().build(); }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传素材文件", description = "上传文本文件并创建异步导入任务。")
    public FileImportJobDto upload(@AuthenticationPrincipal UserDetails principal, @RequestPart("file") MultipartFile file) throws IOException {
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        return materialService.createUploadJob(currentUser(principal), file.getOriginalFilename(), content);
    }

    @GetMapping("/upload/{jobId}")
    @Operation(summary = "查询导入任务状态", description = "按任务 ID 查询文件导入进度。")
    public FileImportJobDto uploadStatus(@PathVariable UUID jobId) { return materialService.getUploadStatus(jobId); }

    @PostMapping("/search")
    @Operation(summary = "搜索素材", description = "按关键词和规则进行素材检索。")
    public List<MaterialSearchResultDto> search(@RequestBody MaterialSearchRequest request) { return materialService.search(request); }

    @PostMapping("/editor/auto-hints")
    @Operation(summary = "编辑器自动提示", description = "根据上下文返回素材建议。")
    public List<MaterialSearchResultDto> hints(@RequestBody AutoHintRequest request) { return materialService.autoHints(request); }

    @PostMapping("/review/pending")
    @Operation(summary = "获取待审核素材", description = "返回待审核素材队列。")
    public List<MaterialDto> pending() { return materialService.pending(); }

    @PostMapping("/{id}/review/approve")
    @Operation(summary = "通过素材审核", description = "将素材状态标记为通过。")
    public MaterialDto approve(@PathVariable UUID id, @RequestBody MaterialReviewRequest request) { return materialService.review(id, "approve", request); }

    @PostMapping("/{id}/review/reject")
    @Operation(summary = "驳回素材审核", description = "将素材状态标记为驳回。")
    public MaterialDto reject(@PathVariable UUID id, @RequestBody MaterialReviewRequest request) { return materialService.review(id, "reject", request); }

    @PostMapping("/find-duplicates")
    @Operation(summary = "查重分析", description = "检测潜在重复素材组合。")
    public List<Map<String, Object>> duplicates() { return materialService.findDuplicates(); }

    @PostMapping("/merge")
    @Operation(summary = "合并素材", description = "将重复素材合并为一条记录。")
    public MaterialDto merge(@RequestBody MaterialMergeRequest request) { return materialService.merge(request); }

    @GetMapping("/{id}/citations")
    @Operation(summary = "查询引用关系", description = "查询素材被引用的场景和位置。")
    public List<Map<String, Object>> citations(@PathVariable UUID id) { return materialService.citations(id); }
}
