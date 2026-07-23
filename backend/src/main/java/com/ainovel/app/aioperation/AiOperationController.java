package com.ainovel.app.aioperation;

import com.ainovel.app.common.CurrentUserResolver;
import com.ainovel.app.user.User;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/v1/ai-operations")
public class AiOperationController {
    private final AiOperationService service;
    private final CurrentUserResolver users;

    public AiOperationController(AiOperationService service, CurrentUserResolver users) {
        this.service = service;
        this.users = users;
    }

    @GetMapping("/{id}")
    public AiOperationDtos.Progress get(@AuthenticationPrincipal UserDetails principal, @PathVariable UUID id) {
        return service.get(users.require(principal), id);
    }

    @GetMapping("/active")
    public ResponseEntity<AiOperationDtos.Progress> active(@AuthenticationPrincipal UserDetails principal,
                                                           @RequestParam String scopeType,
                                                           @RequestParam UUID scopeId) {
        User user = users.require(principal);
        return ResponseEntity.ofNullable(service.active(user, scopeType, scopeId));
    }

    @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@AuthenticationPrincipal UserDetails principal, @PathVariable UUID id) {
        return service.events(users.require(principal), id);
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<AiOperationDtos.Accepted> retry(@AuthenticationPrincipal UserDetails principal,
                                                          @PathVariable UUID id) {
        return ResponseEntity.accepted().body(service.retry(users.require(principal), id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal UserDetails principal, @PathVariable UUID id) {
        service.cancel(users.require(principal), id);
        return ResponseEntity.noContent().build();
    }
}
