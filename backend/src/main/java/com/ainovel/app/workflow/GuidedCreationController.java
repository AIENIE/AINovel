package com.ainovel.app.workflow;

import com.ainovel.app.common.BusinessException;
import com.ainovel.app.common.CurrentUserResolver;
import com.ainovel.app.user.User;
import com.ainovel.app.workflow.dto.CreationWorkflowDtos;
import com.ainovel.app.workflow.model.GuidedCreationStep;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/v1/creation-workflows")
@Tag(name = "G1 guided creation")
@SecurityRequirement(name = "bearerAuth")
public class GuidedCreationController {
    private final GuidedCreationWorkflowService workflowService;
    private final CurrentUserResolver currentUserResolver;

    public GuidedCreationController(GuidedCreationWorkflowService workflowService,
                                    CurrentUserResolver currentUserResolver) {
        this.workflowService = workflowService;
        this.currentUserResolver = currentUserResolver;
    }

    @PostMapping
    public ResponseEntity<CreationWorkflowDtos.WorkflowResponse> create(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody CreationWorkflowDtos.CreateRunRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(workflowService.create(currentUserResolver.require(principal), request));
    }

    @GetMapping
    public List<CreationWorkflowDtos.WorkflowResponse> list(@AuthenticationPrincipal UserDetails principal) {
        return workflowService.list(currentUserResolver.require(principal));
    }

    @GetMapping("/{id}")
    public CreationWorkflowDtos.WorkflowResponse get(@AuthenticationPrincipal UserDetails principal,
                                                     @PathVariable UUID id) {
        return workflowService.get(currentUserResolver.require(principal), id);
    }

    @PostMapping("/{id}/steps/{step}/generate")
    public ResponseEntity<CreationWorkflowDtos.AcceptedResponse> generate(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID id,
            @PathVariable String step,
            @Valid @RequestBody(required = false) CreationWorkflowDtos.GenerateStepRequest request) {
        User user = currentUserResolver.require(principal);
        CreationWorkflowDtos.GenerateStepRequest body = request == null
                ? new CreationWorkflowDtos.GenerateStepRequest(null) : request;
        return ResponseEntity.accepted().body(workflowService.generate(user, id, parseStep(step), body));
    }

    @PostMapping("/{id}/steps/{step}/confirm")
    public CreationWorkflowDtos.WorkflowResponse confirm(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID id,
            @PathVariable String step,
            @Valid @RequestBody CreationWorkflowDtos.ConfirmStepRequest request) {
        return workflowService.confirm(currentUserResolver.require(principal), id, parseStep(step), request);
    }

    @PostMapping("/{id}/steps/world/skip")
    public CreationWorkflowDtos.WorkflowResponse skipWorld(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID id,
            @RequestParam(required = false) Long version) {
        return workflowService.skipWorld(currentUserResolver.require(principal), id, version);
    }

    @PostMapping("/{id}/auto-run")
    public ResponseEntity<CreationWorkflowDtos.WorkflowResponse> startAuto(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) CreationWorkflowDtos.AutoRunRequest request) {
        return ResponseEntity.accepted().body(workflowService.startAuto(
                currentUserResolver.require(principal), id,
                request == null ? new CreationWorkflowDtos.AutoRunRequest(null) : request));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<CreationWorkflowDtos.WorkflowResponse> retry(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID id) {
        return ResponseEntity.accepted().body(
                workflowService.retry(currentUserResolver.require(principal), id));
    }

    private GuidedCreationStep parseStep(String raw) {
        try {
            GuidedCreationStep step = GuidedCreationStep.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            if (step == GuidedCreationStep.COMPLETED) {
                throw new IllegalArgumentException();
            }
            return step;
        } catch (RuntimeException ex) {
            throw new BusinessException("未知的向导步骤：" + raw);
        }
    }
}
