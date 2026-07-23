package com.ainovel.app.aioperation;

import com.ainovel.app.common.CurrentUserResolver;
import com.ainovel.app.manuscript.GenerationMode;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.story.OutlineService;
import com.ainovel.app.story.dto.OutlineChapterGenerateRequest;
import com.ainovel.app.story.dto.StoryCreateRequest;
import com.ainovel.app.user.User;
import com.ainovel.app.world.WorldService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
public class AiFeatureOperationController {
    private final AiOperationService operations;
    private final CurrentUserResolver users;
    private final ResourceAccessGuard accessGuard;
    private final OutlineService outlines;
    private final WorldService worlds;
    private final ObjectMapper objectMapper;

    public AiFeatureOperationController(AiOperationService operations, CurrentUserResolver users,
                                        ResourceAccessGuard accessGuard, OutlineService outlines,
                                        WorldService worlds, ObjectMapper objectMapper) {
        this.operations = operations;
        this.users = users;
        this.accessGuard = accessGuard;
        this.outlines = outlines;
        this.worlds = worlds;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/v1/conception/operations")
    public ResponseEntity<AiOperationDtos.Accepted> conception(@AuthenticationPrincipal UserDetails principal,
                                                               @Valid @RequestBody StoryCreateRequest request) {
        User user = users.require(principal);
        return accepted(user, "CONCEPTION", "CONCEPTION", null, null, null, request, 3, "准备故事构思");
    }

    @PostMapping("/v1/outlines/{outlineId}/chapters/operations")
    public ResponseEntity<AiOperationDtos.Accepted> chapter(@AuthenticationPrincipal UserDetails principal,
                                                            @PathVariable UUID outlineId,
                                                            @RequestBody OutlineChapterGenerateRequest request) {
        User user = users.require(principal);
        outlines.get(outlineId);
        return accepted(user, "OUTLINE_CHAPTER", "OUTLINE", outlineId, null, null, request, 3, "准备章节上下文");
    }

    @PostMapping("/v1/worlds/{worldId}/publish/operations")
    public ResponseEntity<AiOperationDtos.Accepted> publishWorld(@AuthenticationPrincipal UserDetails principal,
                                                                 @PathVariable UUID worldId) {
        User user = users.require(principal);
        worlds.get(worldId);
        return accepted(user, "WORLD_PUBLISH", "WORLD", worldId, null, null, null, 6, "检查世界模块");
    }

    @PostMapping("/v1/worlds/{worldId}/generation/{moduleKey}/operations")
    public ResponseEntity<AiOperationDtos.Accepted> generateWorldModule(@AuthenticationPrincipal UserDetails principal,
                                                                        @PathVariable UUID worldId,
                                                                        @PathVariable String moduleKey) {
        User user = users.require(principal);
        worlds.get(worldId);
        return accepted(user, "WORLD_MODULE", "WORLD", worldId, null, moduleKey, null, 2, "准备世界模块");
    }

    @PostMapping("/v1/manuscripts/{manuscriptId}/scenes/{sceneId}/generate/operations")
    public ResponseEntity<AiOperationDtos.Accepted> generateScene(@AuthenticationPrincipal UserDetails principal,
                                                                  @PathVariable UUID manuscriptId,
                                                                  @PathVariable UUID sceneId,
                                                                  @RequestParam(defaultValue = "fast") String mode) {
        User user = users.require(principal);
        accessGuard.requireOwnedManuscript(manuscriptId, user);
        GenerationMode parsed = "crafted".equalsIgnoreCase(mode) ? GenerationMode.CRAFTED : GenerationMode.FAST;
        return accepted(user, "SCENE_GENERATION", "MANUSCRIPT", manuscriptId, sceneId, parsed.name(), null, 5, "准备场景上下文");
    }

    @PostMapping("/v2/manuscripts/{manuscriptId}/scenes/{sceneId}/quality-runs/operations")
    public ResponseEntity<AiOperationDtos.Accepted> slop(@AuthenticationPrincipal UserDetails principal,
                                                         @PathVariable UUID manuscriptId, @PathVariable UUID sceneId) {
        User user = users.require(principal);
        accessGuard.requireOwnedManuscript(manuscriptId, user);
        return accepted(user, "SLOP_DIAGNOSIS", "MANUSCRIPT", manuscriptId, sceneId, null, null, 2, "准备文本诊断");
    }

    @PostMapping("/v2/manuscripts/{manuscriptId}/slop-drift-runs/operations")
    public ResponseEntity<AiOperationDtos.Accepted> drift(@AuthenticationPrincipal UserDetails principal,
                                                          @PathVariable UUID manuscriptId) {
        User user = users.require(principal);
        accessGuard.requireOwnedManuscript(manuscriptId, user);
        return accepted(user, "SLOP_DRIFT", "MANUSCRIPT", manuscriptId, null, null, null, 2, "准备长文窗口");
    }

    @PostMapping("/v2/manuscripts/{manuscriptId}/scenes/{sceneId}/plot-quality-runs/operations")
    public ResponseEntity<AiOperationDtos.Accepted> plot(@AuthenticationPrincipal UserDetails principal,
                                                         @PathVariable UUID manuscriptId, @PathVariable UUID sceneId) {
        User user = users.require(principal);
        accessGuard.requireOwnedManuscript(manuscriptId, user);
        return accepted(user, "PLOT_DIAGNOSIS", "MANUSCRIPT", manuscriptId, sceneId, null, null, 2, "准备剧情诊断");
    }

    @PostMapping("/v2/manuscripts/{manuscriptId}/plot-quality-runs/{runId}/revision-candidate/operations")
    public ResponseEntity<AiOperationDtos.Accepted> plotRevision(@AuthenticationPrincipal UserDetails principal,
                                                                 @PathVariable UUID manuscriptId, @PathVariable UUID runId) {
        User user = users.require(principal);
        accessGuard.requireOwnedManuscript(manuscriptId, user);
        return accepted(user, "PLOT_REVISION", "MANUSCRIPT", manuscriptId, runId, null, null, 2, "准备修订上下文");
    }

    private ResponseEntity<AiOperationDtos.Accepted> accepted(User user, String action, String scopeType,
                                                              UUID primaryId, UUID secondaryId, String mode,
                                                              Object request, int total, String step) {
        var payload = new CoreAiOperationHandler.CorePayload(action, primaryId, secondaryId, mode,
                request == null ? null : objectMapper.valueToTree(request));
        return ResponseEntity.accepted().body(operations.submit(user, CoreAiOperationHandler.TYPE,
                scopeType, primaryId, payload, total, step));
    }
}
