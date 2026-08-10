package com.ainovel.app.g2evaluation;

import com.ainovel.app.admin.ops.OpsRecordFileSink;
import com.ainovel.app.g2evaluation.dto.G2EvaluationDtos;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/admin/g2-evaluations")
@PreAuthorize("hasAuthority('AUTH_LOCAL_ADMIN')")
@Tag(name = "G2 blind evaluation admin")
@SecurityRequirement(name = "adminSessionCookie")
public class AdminG2EvaluationController {
    private final G2EvaluationService evaluationService;
    private final OpsRecordFileSink recordFileSink;

    public AdminG2EvaluationController(G2EvaluationService evaluationService,
                                       OpsRecordFileSink recordFileSink) {
        this.evaluationService = evaluationService;
        this.recordFileSink = recordFileSink;
    }

    @GetMapping
    public List<G2EvaluationDtos.ExperimentResponse> list() {
        return evaluationService.listAll();
    }

    @PostMapping
    public G2EvaluationDtos.ExperimentResponse create(Authentication authentication,
                                                       @Valid @RequestBody G2EvaluationDtos.CreateExperimentRequest request) {
        G2EvaluationDtos.ExperimentResponse response = evaluationService.create(authentication.getName(), request);
        audit(authentication, "g2-evaluation.create", response.id(), "DRAFT");
        return response;
    }

    @PostMapping("/{id}/status")
    public G2EvaluationDtos.ExperimentResponse transition(Authentication authentication,
                                                           @PathVariable UUID id,
                                                           @Valid @RequestBody G2EvaluationDtos.TransitionRequest request) {
        G2EvaluationDtos.ExperimentResponse response = evaluationService.transition(id, request.status());
        audit(authentication, "g2-evaluation.status", id, request.status().name());
        return response;
    }

    private void audit(Authentication authentication, String action, UUID targetId, String outcome) {
        recordFileSink.appendAudit(Map.of(
                "category", "admin",
                "action", action,
                "actor", authentication.getName(),
                "targetType", "g2-evaluation",
                "targetId", String.valueOf(targetId),
                "outcome", outcome,
                "result", "SUCCESS",
                "severity", "INFO"
        ));
    }
}
