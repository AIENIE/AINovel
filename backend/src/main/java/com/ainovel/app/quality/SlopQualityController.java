package com.ainovel.app.quality;

import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.quality.dto.SlopQualityRunDto;
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
public class SlopQualityController {
    private final ResourceAccessGuard accessGuard;
    private final SlopDiagnosticService diagnosticService;

    public SlopQualityController(ResourceAccessGuard accessGuard,
                                 SlopDiagnosticService diagnosticService) {
        this.accessGuard = accessGuard;
        this.diagnosticService = diagnosticService;
    }

    @Operation(summary = "v2 API endpoint")
    @GetMapping("/manuscripts/{manuscriptId}/quality-runs")
    public List<SlopQualityRunDto> listRuns(@AuthenticationPrincipal UserDetails principal,
                                            @PathVariable UUID manuscriptId,
                                            @RequestParam(required = false) UUID sceneId) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedManuscript(manuscriptId, user);
        return diagnosticService.listRuns(manuscriptId, sceneId)
                .stream()
                .map(SlopQualityMapper::toDto)
                .toList();
    }

    @Operation(summary = "v2 API endpoint")
    @PostMapping("/manuscripts/{manuscriptId}/scenes/{sceneId}/quality-runs")
    public SlopQualityRunDto analyzeScene(@AuthenticationPrincipal UserDetails principal,
                                          @PathVariable UUID manuscriptId,
                                          @PathVariable UUID sceneId) {
        User user = accessGuard.currentUser(principal);
        Manuscript manuscript = accessGuard.requireOwnedManuscript(manuscriptId, user);
        return SlopQualityMapper.toDto(diagnosticService.analyzeScene(user, manuscript, sceneId));
    }
}
