package com.ainovel.app.manuscript;

import com.ainovel.app.common.RefineRequest;
import com.ainovel.app.manuscript.dto.*;
import io.swagger.v3.oas.annotations.Operation;
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

    @PostMapping("/outlines/{outlineId}/manuscripts")
    @Operation(summary = "创建稿件", description = "在指定大纲下创建稿件。")
    public ManuscriptDto create(@PathVariable UUID outlineId, @Valid @RequestBody ManuscriptCreateRequest request) {
        return manuscriptService.create(outlineId, request);
    }

    @DeleteMapping("/manuscripts/{id}")
    @Operation(summary = "删除稿件", description = "删除指定稿件。")
    public ResponseEntity<Void> delete(@PathVariable UUID id) { manuscriptService.delete(id); return ResponseEntity.noContent().build(); }

    @GetMapping("/manuscripts/{id}")
    @Operation(summary = "获取稿件详情", description = "按稿件 ID 获取章节正文映射。")
    public ManuscriptDto get(@PathVariable UUID id) { return manuscriptService.get(id); }

    @PostMapping("/manuscripts/{id}/scenes/{sceneId}/generate")
    @Operation(summary = "生成场景正文（指定稿件）", description = "为稿件中的指定场景生成正文。")
    public ManuscriptDto generateSceneForManuscript(@PathVariable UUID id, @PathVariable UUID sceneId) {
        return manuscriptService.generateForScene(id, sceneId);
    }

    @PutMapping("/manuscripts/{id}/sections/{sceneId}")
    @Operation(summary = "保存场景正文（指定稿件）", description = "更新指定稿件中场景对应的正文内容。")
    public ManuscriptDto saveSectionForManuscript(@PathVariable UUID id, @PathVariable UUID sceneId, @RequestBody SectionUpdateRequest request) {
        return manuscriptService.updateSection(id, sceneId, request);
    }

    @PostMapping("/manuscript/scenes/{sceneId}/generate")
    @Operation(summary = "生成场景正文（兼容路径）", description = "兼容旧路径，自动选择当前稿件生成场景正文。")
    public ManuscriptDto generateScene(@PathVariable UUID sceneId) { return manuscriptService.generateForScene(sceneId); }

    @PutMapping("/manuscript/sections/{sectionId}")
    @Operation(summary = "保存场景正文（兼容路径）", description = "兼容旧路径，自动选择当前稿件更新正文。")
    public ManuscriptDto saveSection(@PathVariable UUID sectionId, @RequestBody SectionUpdateRequest request) { return manuscriptService.updateSection(sectionId, request); }

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
