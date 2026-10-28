package com.ainovel.app.writingagent;
import com.ainovel.app.common.CurrentUserResolver;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.*;
import static com.ainovel.app.writingagent.WritingAgentDtos.*;
@RestController
@RequestMapping("/v1/manuscripts/{id}/scenes/{sceneId}/agent-runs")
public class WritingAgentController {
    private final WritingAgentService service;private final CurrentUserResolver users;
    public WritingAgentController(WritingAgentService service,CurrentUserResolver users){this.service=service;this.users=users;}
    @PostMapping("/preview") public Preview preview(@AuthenticationPrincipal UserDetails user,@PathVariable UUID id,@PathVariable UUID sceneId,@RequestBody Request request){return service.preview(users.require(user),id,sceneId,request);}
    @PostMapping public ResponseEntity<Accepted> submit(@AuthenticationPrincipal UserDetails user,@PathVariable UUID id,@PathVariable UUID sceneId,@RequestBody Request request,@RequestHeader("Idempotency-Key")String key){return ResponseEntity.accepted().body(service.submit(users.require(user),id,sceneId,request,key));}
    @GetMapping public List<View> list(@AuthenticationPrincipal UserDetails user,@PathVariable UUID id,@PathVariable UUID sceneId,@RequestParam UUID branchId){return service.list(users.require(user),id,sceneId,branchId);}
    @GetMapping("/{runId}") public View detail(@AuthenticationPrincipal UserDetails user,@PathVariable UUID id,@PathVariable UUID sceneId,@PathVariable UUID runId){return service.get(users.require(user),id,sceneId,runId);}
    public record StepPage(List<StepView> steps,Integer nextAfter) {}
    @GetMapping("/{runId}/steps") public StepPage steps(@AuthenticationPrincipal UserDetails user,@PathVariable UUID id,@PathVariable UUID sceneId,@PathVariable UUID runId,@RequestParam(defaultValue="0") int after,@RequestParam(defaultValue="20") int limit){
        if(after<0||limit<1||limit>50)throw error("WRITING_AGENT_REQUEST_INVALID");
        var remaining=service.get(users.require(user),id,sceneId,runId).steps().stream().filter(step->step.sequence()>after).toList();
        var page=remaining.stream().limit(limit).toList();return new StepPage(page,remaining.size()>limit?page.getLast().sequence():null);
    }
    @PostMapping("/{runId}/{action:pause|resume|cancel}") public View control(@AuthenticationPrincipal UserDetails user,@PathVariable UUID id,@PathVariable UUID sceneId,@PathVariable UUID runId,@PathVariable String action,@RequestBody Revision request,@RequestHeader("Idempotency-Key")String key){return service.control(users.require(user),id,sceneId,runId,action,request,key);}
    @PostMapping("/{runId}/accept") public com.ainovel.app.manuscript.dto.ManuscriptDto accept(@AuthenticationPrincipal UserDetails user,@PathVariable UUID id,@PathVariable UUID sceneId,@PathVariable UUID runId,@RequestBody Decision request,@RequestHeader("Idempotency-Key")String key){return service.accept(users.require(user),id,sceneId,runId,request,key);}
}
