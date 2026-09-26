package com.ainovel.app.narrative;
import com.ainovel.app.security.ResourceAccessGuard;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static com.ainovel.app.narrative.NarrativeContextDtos.*;

@RestController
@RequestMapping("/v2/manuscripts/{manuscriptId}/branches/{branchId}/narrative/context")
public class NarrativeContextController {
    private final NarrativeContextService context;
    private final ResourceAccessGuard access;
    public NarrativeContextController(NarrativeContextService context, ResourceAccessGuard access) { this.context=context; this.access=access; }
    @GetMapping public State state(@AuthenticationPrincipal UserDetails user,@PathVariable UUID manuscriptId,@PathVariable UUID branchId) {
        return context.state(access.currentUser(user),manuscriptId,branchId);
    }
    @PutMapping public State update(@AuthenticationPrincipal UserDetails user,@PathVariable UUID manuscriptId,@PathVariable UUID branchId,
                                   @RequestHeader("Idempotency-Key") String key,@Valid @RequestBody Update request) {
        return context.update(access.currentUser(user),manuscriptId,branchId,request,key);
    }
    @GetMapping("/history") public List<Map<String,Object>> history(@AuthenticationPrincipal UserDetails user,@PathVariable UUID manuscriptId,@PathVariable UUID branchId) {
        return context.history(access.currentUser(user),manuscriptId,branchId);
    }
    @GetMapping("/candidates") public List<NarrativeGenerationCandidate> candidates(@AuthenticationPrincipal UserDetails user,@PathVariable UUID manuscriptId,@PathVariable UUID branchId) {
        return context.candidates(access.currentUser(user),manuscriptId,branchId);
    }
    @GetMapping("/preview") public Preview preview(@AuthenticationPrincipal UserDetails user,@PathVariable UUID manuscriptId,@PathVariable UUID branchId,
            @RequestParam UUID sceneId,@RequestParam View view,@RequestParam(required=false) UUID characterId,@RequestParam(defaultValue="3500") int budget) {
        return context.preview(access.currentUser(user),manuscriptId,branchId,sceneId,view,characterId,budget);
    }
}
