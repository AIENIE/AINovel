package com.ainovel.app.g2evaluation;

import com.ainovel.app.common.CurrentUserResolver;
import com.ainovel.app.g2evaluation.dto.G2EvaluationDtos;
import com.ainovel.app.user.User;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/g2-evaluations")
@Tag(name = "G2 blind evaluation")
@SecurityRequirement(name = "bearerAuth")
public class G2EvaluationController {
    private final G2EvaluationService evaluationService;
    private final CurrentUserResolver currentUserResolver;

    public G2EvaluationController(G2EvaluationService evaluationService,
                                  CurrentUserResolver currentUserResolver) {
        this.evaluationService = evaluationService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping("/open")
    public List<G2EvaluationDtos.ExperimentResponse> openForAuthors() {
        return evaluationService.listOpenForAuthors();
    }

    @PostMapping("/{id}/samples")
    public G2EvaluationDtos.SampleSubmissionResponse submit(@AuthenticationPrincipal UserDetails principal,
                                                             @PathVariable UUID id,
                                                             @Valid @RequestBody G2EvaluationDtos.SubmitSampleRequest request) {
        return evaluationService.submitSample(currentUserResolver.require(principal), id, request);
    }

    @GetMapping("/{id}/review/next")
    public G2EvaluationDtos.ReviewSampleResponse next(@AuthenticationPrincipal UserDetails principal,
                                                       @PathVariable UUID id) {
        return evaluationService.nextReviewSample(currentUserResolver.require(principal), id);
    }

    @PostMapping("/{id}/review/votes")
    public G2EvaluationDtos.ExperimentResponse vote(@AuthenticationPrincipal UserDetails principal,
                                                    @PathVariable UUID id,
                                                    @Valid @RequestBody G2EvaluationDtos.VoteRequest request) {
        return evaluationService.submitVote(currentUserResolver.require(principal), id, request);
    }
}
