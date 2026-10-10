package com.ainovel.app.material.evidence;

import com.ainovel.app.common.CurrentUserResolver;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/material-evidence/revisions")
public class MaterialSemanticController {
    private final MaterialSemanticService service;
    private final CurrentUserResolver users;

    public MaterialSemanticController(MaterialSemanticService service, CurrentUserResolver users) {
        this.service = service;
        this.users = users;
    }

    @PostMapping("/{revision}/semantic/{profile}/resume")
    public EvidenceDtos.SemanticResume resume(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable String revision,
            @PathVariable String profile,
            @Valid @RequestBody EvidenceDtos.SemanticResumeWrite request) {
        return service.resume(users.require(user), revision, profile, request);
    }
}
