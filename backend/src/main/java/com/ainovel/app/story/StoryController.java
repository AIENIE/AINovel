package com.ainovel.app.story;

import com.ainovel.app.common.RefineRequest;
import com.ainovel.app.story.dto.*;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.story.repo.StoryRepository;
import com.ainovel.app.user.User;
import com.ainovel.app.user.UserRepository;
import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.dto.AiRefineRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1")
@Tag(name = "Story", description = "故事卡、角色卡、大纲与章节场景接口")
@SecurityRequirement(name = "bearerAuth")
public class StoryController {
    @Autowired
    private StoryService storyService;
    @Autowired
    private OutlineService outlineService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StoryRepository storyRepository;
    @Autowired
    private AiService aiService;

    private User currentUser(UserDetails details) {
        return userRepository.findByUsername(details.getUsername()).orElseThrow();
    }

    @GetMapping("/story-cards")
    @Operation(summary = "获取故事列表", description = "返回当前用户全部故事卡片。")
    public List<StoryDto> listStories(@AuthenticationPrincipal UserDetails principal) {
        return storyService.listStories(currentUser(principal));
    }

    @GetMapping("/story-cards/{id}")
    @Operation(summary = "获取故事详情", description = "按故事 ID 获取单个故事卡。")
    public StoryDto getStory(@PathVariable UUID id) { return storyService.getStory(id); }

    @PostMapping("/stories")
    @Operation(summary = "创建故事", description = "创建新的故事卡并进入草稿状态。")
    public StoryDto createStory(@AuthenticationPrincipal UserDetails principal, @Valid @RequestBody StoryCreateRequest request) {
        return storyService.createStory(currentUser(principal), request);
    }

    @PutMapping("/story-cards/{id}")
    @Operation(summary = "更新故事", description = "更新故事标题、梗概、类型、语气与状态。")
    public StoryDto updateStory(@PathVariable UUID id, @RequestBody StoryUpdateRequest request) { return storyService.updateStory(id, request); }

    @DeleteMapping("/stories/{id}")
    @Operation(summary = "删除故事", description = "删除故事及关联角色卡。")
    public ResponseEntity<Void> deleteStory(@PathVariable UUID id) { storyService.deleteStory(id); return ResponseEntity.noContent().build(); }

    @GetMapping("/story-cards/{id}/character-cards")
    @Operation(summary = "获取角色列表", description = "按故事 ID 查询角色卡。")
    public List<CharacterDto> listCharacters(@PathVariable UUID id) { return storyService.listCharacters(id); }

    @PostMapping("/story-cards/{id}/characters")
    @Operation(summary = "新增角色", description = "在指定故事下新增角色卡。")
    public CharacterDto addCharacter(@PathVariable UUID id, @Valid @RequestBody CharacterRequest request) { return storyService.addCharacter(id, request); }

    @PutMapping("/character-cards/{id}")
    @Operation(summary = "更新角色", description = "更新角色卡基础信息。")
    public CharacterDto updateCharacter(@PathVariable UUID id, @RequestBody CharacterRequest request) { return storyService.updateCharacter(id, request); }

    @DeleteMapping("/character-cards/{id}")
    @Operation(summary = "删除角色", description = "删除指定角色卡。")
    public ResponseEntity<Void> deleteCharacter(@PathVariable UUID id) { storyService.deleteCharacter(id); return ResponseEntity.noContent().build(); }

    @PostMapping("/story-cards/{id}/refine")
    @Operation(summary = "润色故事文本", description = "调用 AiService 对故事文本执行润色。")
    public ResponseEntity<String> refineStory(@AuthenticationPrincipal UserDetails principal, @PathVariable UUID id, @RequestBody RefineRequest request) {
        return ResponseEntity.ok(storyService.refineStory(currentUser(principal), id, request));
    }

    @PostMapping("/character-cards/{id}/refine")
    @Operation(summary = "润色角色文本", description = "调用 AiService 对角色文本执行润色。")
    public ResponseEntity<String> refineCharacter(@AuthenticationPrincipal UserDetails principal, @PathVariable UUID id, @RequestBody RefineRequest request) {
        return ResponseEntity.ok(storyService.refineCharacter(currentUser(principal), id, request));
    }

    @PostMapping("/conception")
    @Operation(summary = "一键构思", description = "创建故事并生成初始角色草稿。")
    public ResponseEntity<Map<String, Object>> conception(@AuthenticationPrincipal UserDetails principal, @RequestBody StoryCreateRequest request) {
        return ResponseEntity.ok(storyService.conception(currentUser(principal), request));
    }

    @GetMapping("/story-cards/{storyId}/outlines")
    @Operation(summary = "获取大纲列表", description = "按故事 ID 查询所有大纲。")
    public List<OutlineDto> listOutlines(@PathVariable UUID storyId) {
        Story entity = storyRepository.getReferenceById(storyId);
        return outlineService.listByStory(entity);
    }

    @PostMapping("/story-cards/{storyId}/outlines")
    @Operation(summary = "创建大纲", description = "在故事下创建新的大纲草稿。")
    public OutlineDto createOutline(@PathVariable UUID storyId, @RequestBody OutlineCreateRequest request) {
        Story story = storyRepository.getReferenceById(storyId);
        return outlineService.createOutline(story, request);
    }

    @GetMapping("/outlines/{id}")
    @Operation(summary = "获取大纲详情", description = "按大纲 ID 获取章节与场景结构。")
    public OutlineDto getOutline(@PathVariable UUID id) { return outlineService.get(id); }

    @PutMapping("/outlines/{id}")
    @Operation(summary = "保存大纲", description = "覆盖式保存大纲章节与场景内容。")
    public OutlineDto saveOutline(@PathVariable UUID id, @RequestBody OutlineSaveRequest request) { return outlineService.saveOutline(id, request); }

    @DeleteMapping("/outlines/{id}")
    @Operation(summary = "删除大纲", description = "删除指定大纲。")
    public ResponseEntity<Void> deleteOutline(@PathVariable UUID id) { outlineService.deleteOutline(id); return ResponseEntity.noContent().build(); }

    @PostMapping("/outlines/{outlineId}/chapters")
    @Operation(summary = "生成章节", description = "在大纲下新增 AI 生成章节。")
    public OutlineDto generateChapter(@PathVariable UUID outlineId, @RequestBody OutlineChapterGenerateRequest request) {
        return outlineService.addGeneratedChapter(outlineId, request);
    }

    @PutMapping("/chapters/{id}")
    @Operation(summary = "更新章节", description = "更新章节标题、摘要和顺序。")
    public ResponseEntity<OutlineDto> updateChapter(@PathVariable UUID id, @RequestBody ChapterUpdateRequest request) {
        return ResponseEntity.ok(outlineService.updateChapter(id, request));
    }

    @PutMapping("/scenes/{id}")
    @Operation(summary = "更新场景", description = "更新场景标题、摘要、内容和顺序。")
    public ResponseEntity<OutlineDto> updateScene(@PathVariable UUID id, @RequestBody SceneUpdateRequest request) {
        return ResponseEntity.ok(outlineService.updateScene(id, request));
    }

    @PostMapping("/outlines/scenes/{id}/refine")
    @Operation(summary = "润色场景文本", description = "调用 AiService 对场景文本执行润色。")
    public ResponseEntity<String> refineScene(@AuthenticationPrincipal UserDetails principal, @PathVariable UUID id, @RequestBody RefineRequest request) {
        String instruction = request.instruction() == null ? "" : request.instruction();
        return ResponseEntity.ok(aiService.refine(currentUser(principal), new AiRefineRequest(request.text(), instruction, null)).result());
    }
}
