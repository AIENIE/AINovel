package com.ainovel.app.worldassistant;

import com.ainovel.app.common.CurrentUserResolver;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static com.ainovel.app.worldassistant.WorldAssistantDtos.*;

@RestController
@RequestMapping("/v1/worlds/{worldId}/assistant")
public class WorldAssistantController {
    private final WorldAssistantService service;
    private final CurrentUserResolver users;
    public WorldAssistantController(WorldAssistantService service,CurrentUserResolver users){this.service=service;this.users=users;}
    @GetMapping("/sessions") public List<SessionSummary> sessions(@AuthenticationPrincipal UserDetails p,@PathVariable UUID worldId){return service.sessions(users.require(p),worldId);}
    @PostMapping("/sessions") public SessionSummary create(@AuthenticationPrincipal UserDetails p,@PathVariable UUID worldId,@RequestHeader("Idempotency-Key")String key,@Valid @RequestBody SessionRequest r){return service.createSession(users.require(p),worldId,r,key);}
    @PutMapping("/sessions/{sessionId}") public SessionSummary rename(@AuthenticationPrincipal UserDetails p,@PathVariable UUID worldId,@PathVariable UUID sessionId,@RequestHeader("Idempotency-Key")String key,@Valid @RequestBody SessionRequest r){return service.renameSession(users.require(p),worldId,sessionId,r,key);}
    @GetMapping public SessionDto session(@AuthenticationPrincipal UserDetails p,@PathVariable UUID worldId,@RequestParam(required=false)UUID sessionId,@RequestParam(required=false)String before,@RequestParam(defaultValue="20")int limit){return service.session(users.require(p),worldId,sessionId,before,limit);}
    @PostMapping("/turns") public ResponseEntity<Accepted> submit(@AuthenticationPrincipal UserDetails p,@PathVariable UUID worldId,@RequestHeader("Idempotency-Key")String key,@Valid @RequestBody TurnRequest r){return ResponseEntity.accepted().body(service.submit(users.require(p),worldId,r,key));}
    @GetMapping("/turns/{turnId}") public TurnDto turn(@AuthenticationPrincipal UserDetails p,@PathVariable UUID worldId,@PathVariable UUID turnId){return service.turn(users.require(p),worldId,turnId);}
    @GetMapping("/proposals") public ProposalPage proposals(@AuthenticationPrincipal UserDetails p,@PathVariable UUID worldId,@RequestParam(required=false)String status,@RequestParam(required=false)String type,@RequestParam(required=false)UUID sessionId,@RequestParam(required=false)String before){return service.proposals(users.require(p),worldId,status,type,sessionId,before);}
    @GetMapping("/proposals/{proposalId}") public ProposalDto proposal(@AuthenticationPrincipal UserDetails p,@PathVariable UUID worldId,@PathVariable UUID proposalId){return service.proposal(users.require(p),worldId,proposalId);}
    @PutMapping("/proposals/{proposalId}") public ProposalDto edit(@AuthenticationPrincipal UserDetails p,@PathVariable UUID worldId,@PathVariable UUID proposalId,@RequestHeader("Idempotency-Key")String key,@Valid @RequestBody EditRequest r){return service.edit(users.require(p),worldId,proposalId,r,key);}
    @PostMapping("/proposals/{proposalId}/{action:accept|reject}") public DecisionResult decide(@AuthenticationPrincipal UserDetails p,@PathVariable UUID worldId,@PathVariable UUID proposalId,@PathVariable String action,@RequestHeader("Idempotency-Key")String key,@Valid @RequestBody DecisionRequest r){return service.decide(users.require(p),worldId,proposalId,action,r,key);}
}
