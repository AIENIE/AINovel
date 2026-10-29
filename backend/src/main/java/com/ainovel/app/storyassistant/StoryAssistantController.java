package com.ainovel.app.storyassistant;

import com.ainovel.app.common.CurrentUserResolver;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static com.ainovel.app.storyassistant.StoryAssistantDtos.*;

@RestController
@RequestMapping("/v1/story-cards/{storyId}/assistant")
public class StoryAssistantController {
    private final StoryAssistantService service;
    private final CurrentUserResolver users;
    public StoryAssistantController(StoryAssistantService service,CurrentUserResolver users){this.service=service;this.users=users;}
    @GetMapping("/sessions") public List<SessionSummary> sessions(@AuthenticationPrincipal UserDetails p,@PathVariable UUID storyId){return service.sessions(users.require(p),storyId);}
    @PostMapping("/sessions") public SessionSummary create(@AuthenticationPrincipal UserDetails p,@PathVariable UUID storyId,@RequestHeader("Idempotency-Key")String key,@Valid @RequestBody SessionRequest r){return service.createSession(users.require(p),storyId,r,key);}
    @PutMapping("/sessions/{sessionId}") public SessionSummary rename(@AuthenticationPrincipal UserDetails p,@PathVariable UUID storyId,@PathVariable UUID sessionId,@RequestHeader("Idempotency-Key")String key,@Valid @RequestBody SessionRequest r){return service.renameSession(users.require(p),storyId,sessionId,r,key);}
    @GetMapping public SessionDto session(@AuthenticationPrincipal UserDetails p,@PathVariable UUID storyId,@RequestParam(required=false)UUID sessionId,@RequestParam(required=false)String before,@RequestParam(defaultValue="20")int limit){return service.session(users.require(p),storyId,sessionId,before,limit);}
    @PostMapping("/turns") public ResponseEntity<Accepted> submit(@AuthenticationPrincipal UserDetails p,@PathVariable UUID storyId,@RequestHeader("Idempotency-Key")String key,@Valid @RequestBody TurnRequest r){return ResponseEntity.accepted().body(service.submit(users.require(p),storyId,r,key));}
    @GetMapping("/turns/{turnId}") public TurnDto turn(@AuthenticationPrincipal UserDetails p,@PathVariable UUID storyId,@PathVariable UUID turnId){return service.turn(users.require(p),storyId,turnId);}
    @GetMapping("/proposals/{proposalId}") public ProposalDto proposal(@AuthenticationPrincipal UserDetails p,@PathVariable UUID storyId,@PathVariable UUID proposalId){return service.proposal(users.require(p),storyId,proposalId);}
    @PutMapping("/proposals/{proposalId}") public ProposalDto edit(@AuthenticationPrincipal UserDetails p,@PathVariable UUID storyId,@PathVariable UUID proposalId,@RequestHeader("Idempotency-Key")String key,@Valid @RequestBody EditRequest r){return service.edit(users.require(p),storyId,proposalId,r,key);}
    @PostMapping("/proposals/{proposalId}/{action:accept|reject|undo}") public DecisionResult decide(@AuthenticationPrincipal UserDetails p,@PathVariable UUID storyId,@PathVariable UUID proposalId,@PathVariable String action,@RequestHeader("Idempotency-Key")String key,@Valid @RequestBody DecisionRequest r){return service.decide(users.require(p),storyId,proposalId,action,r,key);}
}
