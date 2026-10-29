package com.ainovel.app.aioperation;

import com.ainovel.app.common.CurrentUserResolver;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.story.OutlineService;
import com.ainovel.app.story.dto.OutlineChapterGenerateRequest;
import com.ainovel.app.story.dto.StoryCreateRequest;
import com.ainovel.app.user.User;
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
    private final WorldTaskService worldTasks;
    private final ObjectMapper objectMapper;
    
    @org.springframework.beans.factory.annotation.Autowired
    private com.ainovel.app.quality.SlopDiagnosticService slopDiagnostic;
    @org.springframework.beans.factory.annotation.Autowired private com.ainovel.app.world.WorldBindingService worldBindings;
    @org.springframework.beans.factory.annotation.Autowired private com.ainovel.app.story.OutlineAiService outlineAi;
    @org.springframework.beans.factory.annotation.Autowired private com.ainovel.app.manuscript.SceneGenerationAdmission sceneAdmission;

    public AiFeatureOperationController(AiOperationService operations, CurrentUserResolver users,
                                        ResourceAccessGuard accessGuard, OutlineService outlines,
                                        WorldTaskService worldTasks, ObjectMapper objectMapper) {
        this.operations = operations;
        this.users = users;
        this.accessGuard = accessGuard;
        this.outlines = outlines;
        this.worldTasks = worldTasks;
        this.objectMapper = objectMapper;
        
    }

    @PostMapping("/v1/conception/operations")
    public ResponseEntity<AiOperationDtos.Accepted> conception(@AuthenticationPrincipal UserDetails principal,
                                                               @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                               @Valid @RequestBody StoryCreateRequest request) {
        User user = users.require(principal);
        return accepted(user, "CONCEPTION", "CONCEPTION", null, null, null, request, 3, "准备故事构思", idempotencyKey);
    }

    @PostMapping("/v1/outlines/{outlineId}/chapters/operations")
    public ResponseEntity<AiOperationDtos.Accepted> chapter(@AuthenticationPrincipal UserDetails principal,
                                                            @PathVariable UUID outlineId,
                                                            @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                            @RequestBody OutlineChapterGenerateRequest request) {
        User user = users.require(principal);
        outlines.get(outlineId);
        return ResponseEntity.accepted().body(new AiOperationDtos.Accepted(outlineAi.submit(user,outlineId,com.ainovel.app.story.OutlineAiService.chapterRequest(request),idempotencyKey).operationId()));
    }

    @PostMapping("/v1/worlds/{worldId}/publish/operations")
    public ResponseEntity<AiOperationDtos.Accepted> publishWorld(@AuthenticationPrincipal UserDetails principal,
                                                                 @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                                 @PathVariable UUID worldId) {
        User user = users.require(principal);
        return accepted(user, "WORLD_PUBLISH", "WORLD", worldId, null, null, worldTasks.freeze(worldId, null), 6, "检查世界模块", idempotencyKey);
    }

    @PostMapping("/v1/worlds/{worldId}/generation/{moduleKey}/operations")
    public ResponseEntity<AiOperationDtos.Accepted> generateWorldModule(@AuthenticationPrincipal UserDetails principal,
                                                                        @PathVariable UUID worldId,
                                                                        @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                                        @PathVariable String moduleKey) {
        User user = users.require(principal);
        return accepted(user, "WORLD_MODULE", "WORLD", worldId, null, moduleKey, worldTasks.freeze(worldId, moduleKey), 2, "准备世界模块", idempotencyKey);
    }

    @GetMapping("/v1/worlds/{worldId}/operations/{operationId}/generated-results")
    public WorldTaskService.GeneratedResults worldGeneratedResults(@AuthenticationPrincipal UserDetails principal,
                                                                   @PathVariable UUID worldId, @PathVariable UUID operationId) {
        return worldTasks.generatedResults(users.require(principal), worldId, operationId);
    }

    @PostMapping("/v1/worlds/{worldId}/entries/{entryId}/generate/operations")
    public ResponseEntity<AiOperationDtos.Accepted> generateEntry(@AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID worldId,@PathVariable UUID entryId,@RequestHeader("Idempotency-Key") String key,
            @RequestBody com.ainovel.app.world.WorldDraftDtos.EntryAi request) {
        return accepted(users.require(principal),"WORLD_ENTRY","WORLD",worldId,entryId,null,
            worldTasks.freezeEntry(worldId,entryId,request.expectedRevision()),2,"准备世界条目",key);
    }

    @PostMapping("/v1/manuscripts/{manuscriptId}/scenes/{sceneId}/generate/operations")
    public ResponseEntity<AiOperationDtos.Accepted> generateScene(@AuthenticationPrincipal UserDetails principal,
                                                                  @PathVariable UUID manuscriptId,
                                                                  @PathVariable UUID sceneId,
                                                                  @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                                  @RequestParam(defaultValue = "fast") String mode) {
        return ResponseEntity.accepted().body(sceneAdmission.submit(users.require(principal),manuscriptId,sceneId,mode,idempotencyKey));
    }

    @PostMapping("/v2/manuscripts/{manuscriptId}/scenes/{sceneId}/quality-runs/operations")
    public ResponseEntity<AiOperationDtos.Accepted> slop(@AuthenticationPrincipal UserDetails principal,
                                                         @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                         @PathVariable UUID manuscriptId, @PathVariable UUID sceneId) {
        User user = users.require(principal);
        var manuscript=accessGuard.requireOwnedManuscript(manuscriptId, user);
        var frozen=slopDiagnostic.freeze(user,manuscript,sceneId);
        return accepted(user, "SLOP_DIAGNOSIS", "MANUSCRIPT", manuscriptId, sceneId, null, frozen, 2, "准备文本诊断", idempotencyKey);
    }

    @PostMapping("/v2/manuscripts/{manuscriptId}/slop-drift-runs/operations")
    public ResponseEntity<AiOperationDtos.Accepted> drift(@AuthenticationPrincipal UserDetails principal,
                                                          @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                          @PathVariable UUID manuscriptId) {
        User user = users.require(principal);
        accessGuard.requireOwnedManuscript(manuscriptId, user);
        return accepted(user, "SLOP_DRIFT", "MANUSCRIPT", manuscriptId, null, null, null, 2, "准备长文窗口", idempotencyKey);
    }

    @PostMapping("/v2/manuscripts/{manuscriptId}/scenes/{sceneId}/plot-quality-runs/operations")
    public ResponseEntity<AiOperationDtos.Accepted> plot(@AuthenticationPrincipal UserDetails principal,
                                                         @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                         @PathVariable UUID manuscriptId, @PathVariable UUID sceneId) {
        User user = users.require(principal);
        accessGuard.requireOwnedManuscript(manuscriptId, user);
        return accepted(user, "PLOT_DIAGNOSIS", "MANUSCRIPT", manuscriptId, sceneId, null, null, 2, "准备剧情诊断", idempotencyKey);
    }

    @PostMapping("/v2/manuscripts/{manuscriptId}/plot-quality-runs/{runId}/revision-candidate/operations")
    public ResponseEntity<AiOperationDtos.Accepted> plotRevision(@AuthenticationPrincipal UserDetails principal,
                                                                 @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                                 @PathVariable UUID manuscriptId, @PathVariable UUID runId) {
        User user = users.require(principal);
        accessGuard.requireOwnedManuscript(manuscriptId, user);
        return accepted(user, "PLOT_REVISION", "MANUSCRIPT", manuscriptId, runId, null, null, 2, "准备修订上下文", idempotencyKey);
    }

    private ResponseEntity<AiOperationDtos.Accepted> accepted(User user, String action, String scopeType,
                                                              UUID primaryId, UUID secondaryId, String mode,
                                                              Object request, int total, String step, String idempotencyKey) {
        var payload = new CoreAiOperationHandler.CorePayload(action, primaryId, secondaryId, mode,
                request == null ? null : objectMapper.valueToTree(request), "SCENE_GENERATION".equals(action)?4:null);
        return ResponseEntity.accepted().body(operations.submit(user, CoreAiOperationHandler.TYPE,
                scopeType, primaryId, payload, total, step, idempotencyKey));
    }

}
