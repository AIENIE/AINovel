package com.ainovel.app.worldassistant;

import com.ainovel.app.common.CurrentUserResolver;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import static com.ainovel.app.worldassistant.WorldAssistantDtos.*;

@RestController
@RequestMapping("/v1/worlds/{worldId}")
public class WorldProposalOperationController {
    private final WorldAssistantService service;
    private final CurrentUserResolver users;
    public WorldProposalOperationController(WorldAssistantService service,CurrentUserResolver users){this.service=service;this.users=users;}
    @PostMapping("/entries/{entryId}/refine/operations")
    public ResponseEntity<Accepted> refine(@AuthenticationPrincipal UserDetails principal,@PathVariable UUID worldId,@PathVariable UUID entryId,
            @RequestHeader("Idempotency-Key")String key,@Valid @RequestBody SingleRequest request) {
        return ResponseEntity.accepted().body(service.single(users.require(principal),worldId,entryId,request,key));
    }
    @PostMapping("/overview/integrate/operations")
    public ResponseEntity<Accepted> overview(@AuthenticationPrincipal UserDetails principal,@PathVariable UUID worldId,
            @RequestHeader("Idempotency-Key")String key,@Valid @RequestBody SingleRequest request) {
        return ResponseEntity.accepted().body(service.single(users.require(principal),worldId,null,request,key));
    }
}
