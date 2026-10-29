package com.ainovel.app.worldcontext;

import com.ainovel.app.common.CurrentUserResolver;
import com.ainovel.app.story.OutlineAiService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import static com.ainovel.app.worldcontext.WorldContextDtos.*;

@RestController
@RequestMapping("/v1")
public class WorldContextController {
    private final CurrentUserResolver users;
    private final WorldContextPreferences preferences;
    private final WorldContextPreviewService previews;
    public WorldContextController(CurrentUserResolver users, WorldContextPreferences preferences, WorldContextPreviewService previews) {
        this.users=users; this.preferences=preferences; this.previews=previews;
    }
    @GetMapping("/outlines/{id}/world-context") public Preferences outline(@AuthenticationPrincipal UserDetails principal,@PathVariable UUID id){return preferences.get(users.require(principal),id,null,null);}
    @PutMapping("/outlines/{id}/world-context") public Preferences saveOutline(@AuthenticationPrincipal UserDetails principal,@PathVariable UUID id,@RequestBody Save body){return preferences.save(users.require(principal),id,null,null,body);}
    @GetMapping("/manuscripts/{id}/scenes/{sceneId}/world-context") public Preferences scene(@AuthenticationPrincipal UserDetails principal,@PathVariable UUID id,@PathVariable UUID sceneId){return preferences.get(users.require(principal),null,id,sceneId);}
    @PutMapping("/manuscripts/{id}/scenes/{sceneId}/world-context") public Preferences saveScene(@AuthenticationPrincipal UserDetails principal,@PathVariable UUID id,@PathVariable UUID sceneId,@RequestBody Save body){return preferences.save(users.require(principal),null,id,sceneId,body);}
    @PostMapping("/outlines/{id}/world-context/preview")
    public WorldContextPreviewService.Preview outlinePreview(@AuthenticationPrincipal UserDetails principal, @PathVariable UUID id, @RequestBody OutlineAiService.Request body) {
        return previews.outlinePreview(users.require(principal),id,body);
    }
    @PostMapping("/manuscripts/{id}/scenes/{sceneId}/world-context/preview")
    public WorldContextPreviewService.Preview scenePreview(@AuthenticationPrincipal UserDetails principal, @PathVariable UUID id, @PathVariable UUID sceneId, @RequestBody(required=false) JsonNode body) {
        return previews.scenePreview(users.require(principal),id,sceneId,body);
    }
}
