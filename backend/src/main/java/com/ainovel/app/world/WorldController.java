package com.ainovel.app.world;

import com.ainovel.app.common.RefineRequest;
import com.ainovel.app.user.User;
import com.ainovel.app.user.UserRepository;
import com.ainovel.app.world.dto.*;
import com.ainovel.app.ai.dto.AiRefineResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/worlds")
@Tag(name = "World", description = "世界观构建与发布接口")
@SecurityRequirement(name = "bearerAuth")
public class WorldController {
    @Autowired
    private WorldService worldService;
    @Autowired
    private UserRepository userRepository;

    private User currentUser(UserDetails details) { return userRepository.findByUsername(details.getUsername()).orElseThrow(); }

    @GetMapping
    @Operation(summary = "获取世界观列表", description = "返回当前用户全部世界观卡片。")
    public List<WorldDto> list(@AuthenticationPrincipal UserDetails principal) { return worldService.list(currentUser(principal)); }

    @PostMapping
    @Operation(summary = "创建世界观", description = "创建新的世界观草稿。")
    public WorldDetailDto create(@AuthenticationPrincipal UserDetails principal, @Valid @RequestBody WorldCreateRequest request) {
        return worldService.create(currentUser(principal), request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取世界观详情", description = "返回世界观详情及模块字段数据。")
    public WorldDetailDto detail(@PathVariable UUID id) { return worldService.get(id); }

    @PutMapping("/{id}")
    @Operation(summary = "更新世界观基础信息", description = "更新名称、标语、主题、创作意图与备注。")
    public WorldDetailDto update(@PathVariable UUID id, @RequestBody WorldUpdateRequest request) { return worldService.update(id, request); }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除世界观", description = "仅草稿状态可删除。")
    public ResponseEntity<Void> delete(@PathVariable UUID id) { worldService.delete(id); return ResponseEntity.noContent().build(); }

    @PutMapping("/{id}/modules")
    @Operation(summary = "批量更新模块", description = "批量写入多个模块字段。")
    public WorldDetailDto updateModules(@PathVariable UUID id, @RequestBody WorldModulesUpdateRequest request) { return worldService.updateModules(id, request); }

    @PutMapping("/{id}/modules/{moduleKey}")
    @Operation(summary = "更新单模块", description = "按模块键写入字段集合。")
    public WorldDetailDto updateModule(@PathVariable UUID id, @PathVariable String moduleKey, @RequestBody WorldModuleUpdateRequest request) { return worldService.updateModule(id, moduleKey, request); }

    @PostMapping("/{id}/modules/{moduleKey}/fields/{fieldKey}/refine")
    @Operation(summary = "润色模块字段", description = "调用 AiService 润色世界观模块字段文本。")
    public ResponseEntity<AiRefineResponse> refineField(@AuthenticationPrincipal UserDetails principal, @PathVariable UUID id, @PathVariable String moduleKey, @PathVariable String fieldKey, @RequestBody RefineRequest request) {
        return ResponseEntity.ok(worldService.refineField(currentUser(principal), id, moduleKey, fieldKey, request.text(), request.instruction()));
    }

    @GetMapping("/{id}/publish/preview")
    @Operation(summary = "发布预检查", description = "检查缺失模块和待自动生成模块。")
    public WorldPublishPreviewResponse preview(@PathVariable UUID id) { return worldService.preview(id); }

    @PostMapping("/{id}/publish")
    @Operation(summary = "发布世界观", description = "推进发布状态并更新版本号。")
    public WorldDetailDto publish(@PathVariable UUID id) { return worldService.publish(id); }

    @GetMapping("/{id}/generation")
    @Operation(summary = "查询生成状态", description = "查看各模块生成进度与状态。")
    public WorldGenerationStatus generation(@PathVariable UUID id) { return worldService.generationStatus(id); }

    @PostMapping("/{id}/generation/{moduleKey}")
    @Operation(summary = "生成模块内容", description = "触发单模块自动生成。")
    public WorldDetailDto generate(@PathVariable UUID id, @PathVariable String moduleKey) { return worldService.generateModule(id, moduleKey); }

    @PostMapping("/{id}/generation/{moduleKey}/retry")
    @Operation(summary = "重试模块生成", description = "重试生成失败或待生成模块。")
    public WorldDetailDto retry(@PathVariable UUID id, @PathVariable String moduleKey) { return worldService.retryModule(id, moduleKey); }
}
