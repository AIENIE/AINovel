package com.ainovel.app.assistant;

import com.ainovel.app.common.CurrentUserResolver;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import static com.ainovel.app.assistant.AssistantDtos.*;

@RestController
@RequestMapping("/v1/manuscripts/{id}/scenes/{sceneId}/assistant")
public class AssistantController {
    private final WritingAssistantService service;private final CurrentUserResolver users;
    public AssistantController(WritingAssistantService service,CurrentUserResolver users){this.service=service;this.users=users;}
    @GetMapping public SessionDto session(@AuthenticationPrincipal UserDetails principal,@PathVariable UUID id,@PathVariable UUID sceneId,
            @RequestParam UUID branchId,@RequestParam(required=false)String before,@RequestParam(defaultValue="20")int limit){return service.session(users.require(principal),id,sceneId,branchId,before,limit);}
    @PostMapping("/turns") public ResponseEntity<Accepted> submit(@AuthenticationPrincipal UserDetails principal,@PathVariable UUID id,@PathVariable UUID sceneId,
            @RequestHeader("Idempotency-Key")String key,@Valid @RequestBody TurnRequest request){return ResponseEntity.accepted().body(service.submit(users.require(principal),id,sceneId,request,key));}
    @GetMapping("/turns/{turnId}") public TurnDto turn(@AuthenticationPrincipal UserDetails principal,@PathVariable UUID id,@PathVariable UUID sceneId,@PathVariable UUID turnId){return service.turn(users.require(principal),id,sceneId,turnId);}
    @GetMapping("/proposals/{proposalId}") public ProposalDto proposal(@AuthenticationPrincipal UserDetails principal,@PathVariable UUID id,@PathVariable UUID sceneId,@PathVariable UUID proposalId){return service.proposal(users.require(principal),id,sceneId,proposalId);}
    @PostMapping("/proposals/{proposalId}/{action:accept|reject|undo}") public DecisionResult decide(@AuthenticationPrincipal UserDetails principal,@PathVariable UUID id,@PathVariable UUID sceneId,
            @PathVariable UUID proposalId,@PathVariable String action,@RequestHeader("Idempotency-Key")String key,@Valid @RequestBody DecisionRequest request){return service.decide(users.require(principal),id,sceneId,proposalId,action,request,key);}
}
