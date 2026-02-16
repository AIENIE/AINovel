package com.ainovel.app.settings;

import com.ainovel.app.settings.dto.*;
import com.ainovel.app.user.User;
import com.ainovel.app.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1")
@Tag(name = "Settings", description = "系统设置与提示词模板接口")
@SecurityRequirement(name = "bearerAuth")
public class SettingsController {
    @Autowired
    private SettingsService settingsService;
    @Autowired
    private UserRepository userRepository;

    private User currentUser(UserDetails userDetails) {
        return userRepository.findByUsername(userDetails.getUsername()).orElseThrow();
    }

    @GetMapping("/prompt-templates")
    @Operation(summary = "获取工作区提示词模板", description = "读取当前用户工作区提示词模板。")
    public ResponseEntity<PromptTemplatesResponse> getPrompts(@AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(settingsService.getPromptTemplates(currentUser(principal)));
    }

    @PutMapping("/prompt-templates")
    @Operation(summary = "更新工作区提示词模板", description = "更新当前用户工作区提示词模板。")
    public ResponseEntity<PromptTemplatesResponse> updatePrompts(@AuthenticationPrincipal UserDetails principal,
                                                                 @RequestBody PromptTemplatesUpdateRequest request) {
        return ResponseEntity.ok(settingsService.updatePromptTemplates(currentUser(principal), request));
    }

    @PostMapping("/prompt-templates/reset")
    @Operation(summary = "重置工作区提示词模板", description = "将工作区提示词模板恢复为默认值。")
    public ResponseEntity<PromptTemplatesResponse> resetPrompts(@AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(settingsService.resetPromptTemplates(currentUser(principal)));
    }

    @GetMapping("/prompt-templates/metadata")
    @Operation(summary = "获取工作区提示词元数据", description = "返回模板语法、变量和函数说明。")
    public ResponseEntity<PromptMetadataResponse> metadata() {
        return ResponseEntity.ok(settingsService.getPromptMetadata());
    }

    @GetMapping("/world-prompts")
    @Operation(summary = "获取世界观提示词模板", description = "读取当前用户世界观提示词模板。")
    public ResponseEntity<WorldPromptTemplatesResponse> worldPrompts(@AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(settingsService.getWorldPromptTemplates(currentUser(principal)));
    }

    @PutMapping("/world-prompts")
    @Operation(summary = "更新世界观提示词模板", description = "更新模块模板、终稿模板与字段润色模板。")
    public ResponseEntity<WorldPromptTemplatesResponse> updateWorldPrompts(@AuthenticationPrincipal UserDetails principal,
                                                                           @RequestBody WorldPromptTemplatesUpdateRequest request) {
        return ResponseEntity.ok(settingsService.updateWorldPrompts(currentUser(principal), request));
    }

    @PostMapping("/world-prompts/reset")
    @Operation(summary = "重置世界观提示词模板", description = "将世界观提示词模板恢复为默认值。")
    public ResponseEntity<WorldPromptTemplatesResponse> resetWorldPrompts(@AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(settingsService.resetWorldPrompts(currentUser(principal)));
    }

    @GetMapping("/world-prompts/metadata")
    @Operation(summary = "获取世界观提示词元数据", description = "返回世界观提示词变量、函数与模块约束说明。")
    public ResponseEntity<WorldPromptMetadataResponse> worldPromptMetadata() {
        return ResponseEntity.ok(settingsService.getWorldPromptMetadata());
    }
}
