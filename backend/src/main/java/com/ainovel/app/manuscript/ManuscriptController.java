package com.ainovel.app.manuscript;

import com.ainovel.app.common.RefineRequest;
import com.ainovel.app.manuscript.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1")
@Tag(name = "Manuscript", description = "稿件编写、场景生成与角色变化分析接口")
@SecurityRequirement(name = "bearerAuth")
public class ManuscriptController {
    @Autowired
    private ManuscriptService manuscriptService;

    @GetMapping("/outlines/{outlineId}/manuscripts")
    @Operation(summary = "获取稿件列表", description = "按大纲 ID 查询全部稿件。")
    public List<ManuscriptDto> list(@PathVariable UUID outlineId) { return manuscriptService.listByOutline(outlineId); }

    @GetMapping("/outlines/{outlineId}/manuscript-summaries")
    @Operation(summary = "稿件摘要列表", description = "工作台列表只返回身份和版本，不读取正文 LOB。")
    public List<ManuscriptSummaryDto> summaries(@PathVariable UUID outlineId) { return manuscriptService.summaries(outlineId); }

    @GetMapping("/manuscripts/{id}/scenes/{sceneId}/content")
    @Operation(summary = "读取单场景正文", description = "返回稿件、分支、场景、正文与乐观版本。不存在的场景正文为空字符串。")
    public SceneContentDto sceneContent(@PathVariable UUID id, @PathVariable UUID sceneId) { return manuscriptService.getScene(id, sceneId); }

    @PutMapping("/manuscripts/{id}/scenes/{sceneId}/content")
    @Operation(summary = "紧凑保存单场景正文", description = "仅更新目标场景和稿件版本。expectedVersion 必填；expectedBranchId 可选。正文或请求无效返回 400，版本或分支变化返回 409，失败不改变已有正文。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "返回稿件、分支、场景身份、正文和新版本"),
            @ApiResponse(responseCode = "400", description = "正文或请求字段无效"),
            @ApiResponse(responseCode = "409", description = "乐观版本或分支已变化")})
    public SceneContentDto saveSceneContent(@PathVariable UUID id, @PathVariable UUID sceneId, @Valid @RequestBody SectionUpdateRequest request) {
        return manuscriptService.updateScene(id, sceneId, request);
    }

    @PostMapping("/outlines/{outlineId}/manuscripts")
    @Operation(summary = "创建或恢复稿件", description = "保存并刷新后返回非空 updatedAt 和 version。可选 UUID Idempotency-Key：同用户、大纲、键及内容重放原响应；同键不同内容返回 409，原稿删除返回 410。省略请求键保持旧行为。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "已创建稿件或原创建回执"),
            @ApiResponse(responseCode = "409", description = "创建请求键内容冲突"),
            @ApiResponse(responseCode = "410", description = "原创建稿件已删除")})
    public ManuscriptDto create(@PathVariable UUID outlineId, @Valid @RequestBody ManuscriptCreateRequest request,
                                @RequestHeader(value = "Idempotency-Key", required = false) String requestKey) {
        return manuscriptService.create(outlineId, request, requestKey);
    }

    @DeleteMapping("/manuscripts/{id}")
    @Operation(summary = "删除稿件", description = "删除指定稿件。")
    public ResponseEntity<Void> delete(@PathVariable UUID id) { manuscriptService.delete(id); return ResponseEntity.noContent().build(); }

    @GetMapping("/manuscripts/{id}")
    @Operation(summary = "获取稿件详情", description = "按稿件 ID 获取章节正文映射。")
    public ManuscriptDto get(@PathVariable UUID id) { return manuscriptService.get(id); }

    @PostMapping("/manuscripts/{id}/scenes/{sceneId}/generate")
    @Operation(summary = "生成场景正文（指定稿件）", description = "为稿件中的指定场景生成正文。mode=crafted 启用精雕模式（注入反 slop 约束），默认 fast。")
    public ManuscriptDto generateSceneForManuscript(@PathVariable UUID id, @PathVariable UUID sceneId,
            @RequestParam(defaultValue = "fast") String mode) {
        GenerationMode generationMode = "crafted".equalsIgnoreCase(mode) ? GenerationMode.CRAFTED : GenerationMode.FAST;
        return manuscriptService.generateForScene(id, sceneId, generationMode);
    }

    @PutMapping("/manuscripts/{id}/sections/{sceneId}")
    @Operation(summary = "保存场景正文（指定稿件）", description = "更新指定稿件中场景对应的正文内容。")
    public ManuscriptDto saveSectionForManuscript(@PathVariable UUID id, @PathVariable UUID sceneId, @Valid @RequestBody SectionUpdateRequest request) {
        return manuscriptService.updateSection(id, sceneId, request);
    }

    @GetMapping("/manuscripts/{id}/scenes/{sceneId}/generation-runs")
    @Operation(summary = "查询场景生成历史", description = "按时间倒序返回生成记录；待重算记录会在查询中再次尝试精确归因。")
    public List<SceneGenerationRunDto> listSceneGenerationRuns(
            @PathVariable UUID id,
            @PathVariable UUID sceneId,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return manuscriptService.listSceneGenerationRuns(id, sceneId, limit);
    }

    @PatchMapping("/manuscripts/{id}/scenes/{sceneId}/generation-runs/{runId}/feedback")
    @Operation(summary = "更新生成反馈", description = "局部更新最多 8 个标签、500 字备注和偏好确认状态。")
    public SceneGenerationRunDto patchSceneGenerationFeedback(
            @PathVariable UUID id,
            @PathVariable UUID sceneId,
            @PathVariable UUID runId,
            @Valid @RequestBody SceneGenerationFeedbackPatchRequest request
    ) {
        return manuscriptService.patchSceneGenerationFeedback(id, sceneId, runId, request);
    }

    @PostMapping("/manuscript/scenes/{sceneId}/generate")
    @Operation(summary = "生成场景正文（兼容路径）", description = "兼容旧路径，自动选择当前稿件生成场景正文。")
    public ManuscriptDto generateScene(@PathVariable UUID sceneId) { return manuscriptService.generateForScene(sceneId); }

    @PutMapping("/manuscript/sections/{sectionId}")
    @Operation(summary = "保存场景正文（兼容路径）", description = "兼容旧路径，自动选择当前稿件更新正文。")
    public ManuscriptDto saveSection(@PathVariable UUID sectionId, @Valid @RequestBody SectionUpdateRequest request) { return manuscriptService.updateSection(sectionId, request); }

    @PostMapping("/manuscripts/{id}/sections/analyze-character-changes")
    @Operation(summary = "分析角色变化", description = "基于当前段落内容生成角色变化日志。")
    public List<CharacterChangeLogDto> analyze(@PathVariable UUID id, @RequestBody AnalyzeCharacterChangeRequest request) { return manuscriptService.analyzeCharacterChanges(id, request); }

    @GetMapping("/manuscripts/{id}/character-change-logs")
    @Operation(summary = "查询角色变化日志", description = "查询稿件全部角色变化记录。")
    public List<CharacterChangeLogDto> logs(@PathVariable UUID id) { return manuscriptService.listCharacterLogs(id); }

    @GetMapping("/manuscripts/{id}/character-change-logs/{characterId}")
    @Operation(summary = "按角色查询变化日志", description = "按角色 ID 过滤稿件变化日志。")
    public List<CharacterChangeLogDto> logsByCharacter(@PathVariable UUID id, @PathVariable UUID characterId) {
        return manuscriptService.listCharacterLogs(id, characterId);
    }

    @PostMapping("/ai/generate-dialogue")
    @Operation(summary = "生成记忆对话", description = "根据输入文本生成角色对话内容。")
    public ResponseEntity<String> dialogue(@RequestBody RefineRequest request) { return ResponseEntity.ok(manuscriptService.generateDialogue(request)); }
}
