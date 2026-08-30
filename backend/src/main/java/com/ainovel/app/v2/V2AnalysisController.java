package com.ainovel.app.v2;

import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "V2", description = "AINovel v2 and quality APIs")
@RestController
@RequestMapping("/v2")
public class V2AnalysisController {
    private final ResourceAccessGuard accessGuard;
    private final V2AnalysisPersistenceService persistenceService;

    public V2AnalysisController(ResourceAccessGuard accessGuard, V2AnalysisPersistenceService persistenceService) {
        this.accessGuard = accessGuard;
        this.persistenceService = persistenceService;
    }

    @Operation(summary = "v2 API endpoint")

    @PostMapping("/stories/{storyId}/analysis/beta-reader")
    public ResponseEntity<V2AnalysisDtos.AnalysisUnavailableResponse> triggerBetaReader(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID storyId,
            @RequestBody(required = false) V2RequestPayload payload) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(V2AnalysisDtos.AnalysisUnavailableResponse.notImplemented());
    }

    @Operation(summary = "v2 API endpoint")

    @PostMapping("/stories/{storyId}/analysis/continuity-check")
    public ResponseEntity<V2AnalysisDtos.AnalysisUnavailableResponse> triggerContinuityCheck(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID storyId,
            @RequestBody(required = false) V2RequestPayload payload) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(V2AnalysisDtos.AnalysisUnavailableResponse.notImplemented());
    }

    @Operation(summary = "v2 API endpoint")

    @GetMapping("/stories/{storyId}/analysis/jobs")
    public List<V2AnalysisDtos.AnalysisJobResponse> listJobs(@AuthenticationPrincipal UserDetails principal,
                                                             @PathVariable UUID storyId) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);
        return persistenceService.listJobs(storyId);
    }

    @Operation(summary = "v2 API endpoint")

    @GetMapping("/stories/{storyId}/analysis/jobs/{jobId}")
    public V2AnalysisDtos.AnalysisJobResponse getJob(@AuthenticationPrincipal UserDetails principal,
                                                     @PathVariable UUID storyId,
                                                     @PathVariable UUID jobId) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);
        return persistenceService.getJob(storyId, jobId);
    }

    @Operation(summary = "v2 API endpoint")

    @GetMapping("/stories/{storyId}/analysis/reports")
    public List<V2AnalysisDtos.AnalysisReportResponse> listReports(@AuthenticationPrincipal UserDetails principal,
                                                                   @PathVariable UUID storyId) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);
        return persistenceService.listReports(storyId);
    }

    @Operation(summary = "v2 API endpoint")

    @GetMapping("/stories/{storyId}/analysis/reports/{reportId}")
    public V2AnalysisDtos.AnalysisReportResponse getReport(@AuthenticationPrincipal UserDetails principal,
                                                           @PathVariable UUID storyId,
                                                           @PathVariable UUID reportId) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);
        return persistenceService.getReport(storyId, reportId);
    }

    @Operation(summary = "v2 API endpoint")

    @GetMapping("/stories/{storyId}/analysis/continuity-issues")
    public List<V2AnalysisDtos.ContinuityIssueResponse> listContinuityIssues(@AuthenticationPrincipal UserDetails principal,
                                                                             @PathVariable UUID storyId) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);
        return persistenceService.listContinuityIssues(storyId);
    }

    @Operation(summary = "v2 API endpoint")

    @PutMapping("/stories/{storyId}/analysis/continuity-issues/{issueId}")
    public V2AnalysisDtos.ContinuityIssueResponse updateContinuityIssue(@AuthenticationPrincipal UserDetails principal,
                                                                        @PathVariable UUID storyId,
                                                                        @PathVariable UUID issueId,
                                                                        @RequestBody V2RequestPayload payload) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);
        return persistenceService.updateContinuityIssue(storyId, issueId, payload.asMap());
    }

}
