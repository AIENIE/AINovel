package com.ainovel.app.quality;

import com.ainovel.app.quality.dto.SlopQualityRunDto;
import com.ainovel.app.quality.repo.SlopQualityRunRepository;
import com.ainovel.app.user.User;
import com.ainovel.app.v2.V2AccessGuard;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v2")
public class SlopQualityController {
    private final V2AccessGuard accessGuard;
    private final SlopQualityRunRepository runRepository;

    public SlopQualityController(V2AccessGuard accessGuard, SlopQualityRunRepository runRepository) {
        this.accessGuard = accessGuard;
        this.runRepository = runRepository;
    }

    @GetMapping("/manuscripts/{manuscriptId}/quality-runs")
    public List<SlopQualityRunDto> listRuns(@AuthenticationPrincipal UserDetails principal,
                                            @PathVariable UUID manuscriptId,
                                            @RequestParam(required = false) UUID sceneId) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedManuscript(manuscriptId, user);
        if (sceneId != null) {
            return runRepository.findTop20ByManuscriptIdAndSceneIdOrderByCreatedAtDesc(manuscriptId, sceneId)
                    .stream()
                    .map(SlopQualityMapper::toDto)
                    .toList();
        }
        return runRepository.findTop20ByManuscriptIdOrderByCreatedAtDesc(manuscriptId)
                .stream()
                .map(SlopQualityMapper::toDto)
                .toList();
    }
}
