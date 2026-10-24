package com.ainovel.app.story;
import com.ainovel.app.common.CurrentUserResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/v1/outlines/{id}")
public class OutlineAiController {
    private final CurrentUserResolver users;private final OutlineAiService service;
    public OutlineAiController(CurrentUserResolver users,OutlineAiService service){this.users=users;this.service=service;}
    @PostMapping("/ai-operations") public ResponseEntity<OutlineAiService.Accepted> submit(@AuthenticationPrincipal UserDetails p,@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key,@RequestBody OutlineAiService.Request request){return ResponseEntity.accepted().body(service.submit(users.require(p),id,request,key));}
    @GetMapping("/ai-results") public List<OutlineAiService.Result> results(@AuthenticationPrincipal UserDetails p,@PathVariable UUID id){return service.list(users.require(p),id);}
    @GetMapping("/ai-results/{resultId}") public OutlineAiService.Result result(@AuthenticationPrincipal UserDetails p,@PathVariable UUID id,@PathVariable UUID resultId){return service.get(users.require(p),id,resultId);}
    @PostMapping("/ai-results/{resultId}/accept") public OutlineAiService.Result accept(@AuthenticationPrincipal UserDetails p,@PathVariable UUID id,@PathVariable UUID resultId,@RequestBody OutlineAiService.Decision request){return service.accept(users.require(p),id,resultId,request);}
}
