package com.ainovel.app.g2evaluation;

import com.ainovel.app.common.CurrentUserResolver;
import com.ainovel.app.g2evaluation.dto.G2EvaluationDtos;
import com.ainovel.app.user.User;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/admin/g2-evaluations")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "G2 blind evaluation admin")
@SecurityRequirement(name = "bearerAuth")
public class AdminG2EvaluationController {
    private final G2EvaluationService evaluationService;
    private final CurrentUserResolver currentUserResolver;

    public AdminG2EvaluationController(G2EvaluationService evaluationService,
                                       CurrentUserResolver currentUserResolver) {
        this.evaluationService = evaluationService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping
    public List<G2EvaluationDtos.ExperimentResponse> list() {
        return evaluationService.listAll();
    }

    @PostMapping
    public G2EvaluationDtos.ExperimentResponse create(@AuthenticationPrincipal UserDetails principal,
                                                       @Valid @RequestBody G2EvaluationDtos.CreateExperimentRequest request) {
        User admin = currentUserResolver.require(principal);
        return evaluationService.create(admin, request);
    }

    @PostMapping("/{id}/status")
    public G2EvaluationDtos.ExperimentResponse transition(@PathVariable UUID id,
                                                           @Valid @RequestBody G2EvaluationDtos.TransitionRequest request) {
        return evaluationService.transition(id, request.status());
    }
}
