package com.ainovel.app.quality;

import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.quality.dto.PlotQualityRunDto;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "V2", description = "AINovel v2 and quality APIs")
@RestController
@RequestMapping("/v2")
public class PlotQualityController {
    private final ResourceAccessGuard accessGuard;
    private final PlotQualityService plotQualityService;

    public PlotQualityController(ResourceAccessGuard accessGuard,
                                 PlotQualityService plotQualityService) {
        this.accessGuard = accessGuard;
        this.plotQualityService = plotQualityService;
    }

    @Operation(summary = "v2 API endpoint")
    @GetMapping("/manuscripts/{manuscriptId}/plot-quality-runs")
    public List<PlotQualityRunDto> listRuns(@AuthenticationPrincipal UserDetails principal,
                                            @PathVariable UUID manuscriptId,
                                            @RequestParam(required = false) UUID sceneId) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedManuscript(manuscriptId, user);
        return plotQualityService.listRuns(manuscriptId, sceneId)
                .stream()
                .map(PlotQualityMapper::toDto)
                .toList();
    }

    @Operation(summary = "v2 API endpoint")
    @PostMapping("/manuscripts/{manuscriptId}/scenes/{sceneId}/plot-quality-runs")
    public PlotQualityRunDto analyzeScene(@AuthenticationPrincipal UserDetails principal,
                                          @PathVariable UUID manuscriptId,
                                          @PathVariable UUID sceneId) {
        User user = accessGuard.currentUser(principal);
        Manuscript manuscript = accessGuard.requireOwnedManuscript(manuscriptId, user);
        return PlotQualityMapper.toDto(plotQualityService.analyzeScene(user, manuscript, sceneId));
    }

    @Operation(summary = "v2 API endpoint")
    @GetMapping("/manuscripts/{manuscriptId}/plot-quality-trends")
    public PlotQualityTrend trend(@AuthenticationPrincipal UserDetails principal,
                                  @PathVariable UUID manuscriptId) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedManuscript(manuscriptId, user);
        return plotQualityService.buildTrend(manuscriptId);
    }

    @Operation(summary = "v2 API endpoint")
    @PostMapping("/manuscripts/{manuscriptId}/plot-quality-runs/{runId}/revision-candidate")
    public PlotQualityRunDto generateRevisionCandidate(@AuthenticationPrincipal UserDetails principal,
                                                       @PathVariable UUID manuscriptId,
                                                       @PathVariable UUID runId) {
        User user = accessGuard.currentUser(principal);
        Manuscript manuscript = accessGuard.requireOwnedManuscript(manuscriptId, user);
        return PlotQualityMapper.toDto(plotQualityService.generateRevisionCandidate(user, manuscript, runId));
    }

    @Operation(summary = "v2 API endpoint")
    @PostMapping("/manuscripts/{manuscriptId}/plot-quality-runs/{runId}/apply-revision")
    public PlotQualityRunDto applyRevision(@AuthenticationPrincipal UserDetails principal,
                                           @PathVariable UUID manuscriptId,
                                           @PathVariable UUID runId) {
        User user = accessGuard.currentUser(principal);
        Manuscript manuscript = accessGuard.requireOwnedManuscript(manuscriptId, user);
        return PlotQualityMapper.toDto(plotQualityService.applyRevision(user, manuscript, runId));
    }
}
