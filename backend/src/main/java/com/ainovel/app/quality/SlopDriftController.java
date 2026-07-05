package com.ainovel.app.quality;

import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.quality.dto.SlopDriftRunDto;
import com.ainovel.app.quality.repo.SlopDriftRunRepository;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "V2", description = "AINovel v2 and quality APIs")
@RestController
@RequestMapping("/v2")
public class SlopDriftController {
    private final ResourceAccessGuard accessGuard;
    private final SlopDriftRunRepository runRepository;
    private final SlopDriftService driftService;

    public SlopDriftController(ResourceAccessGuard accessGuard,
                               SlopDriftRunRepository runRepository,
                               SlopDriftService driftService) {
        this.accessGuard = accessGuard;
        this.runRepository = runRepository;
        this.driftService = driftService;
    }

    @Operation(summary = "v2 API endpoint")
    @GetMapping("/manuscripts/{manuscriptId}/slop-drift-runs")
    public List<SlopDriftRunDto> list(@AuthenticationPrincipal UserDetails principal,
                                      @PathVariable UUID manuscriptId) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedManuscript(manuscriptId, user);
        return runRepository.findTop20ByManuscriptIdOrderByCreatedAtDesc(manuscriptId)
                .stream()
                .map(SlopDriftMapper::toDto)
                .toList();
    }

    @Operation(summary = "v2 API endpoint")
    @PostMapping("/manuscripts/{manuscriptId}/slop-drift-runs")
    public SlopDriftRunDto analyze(@AuthenticationPrincipal UserDetails principal,
                                   @PathVariable UUID manuscriptId) {
        User user = accessGuard.currentUser(principal);
        Manuscript manuscript = accessGuard.requireOwnedManuscript(manuscriptId, user);
        return SlopDriftMapper.toDto(driftService.analyze(user, manuscript));
    }
}
