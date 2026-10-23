package com.ainovel.app.world;

import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/v1")
public class WorldBindingController {
    private final WorldBindingService bindings;
    public WorldBindingController(WorldBindingService bindings){this.bindings=bindings;}
    private String type(String collection){return switch(collection){case "story-cards"->"STORY";case "outlines"->"OUTLINE";case "manuscripts"->"MANUSCRIPT";default->throw WorldEntryStore.error("WORLD_BINDING_SCOPE_INVALID");};}
    @GetMapping("/{collection:story-cards|outlines|manuscripts}/{id}/world-binding")
    public WorldBindingService.View view(@PathVariable String collection,@PathVariable UUID id){return bindings.view(type(collection),id);}
    @PutMapping("/{collection:story-cards|outlines|manuscripts}/{id}/world-binding")
    public WorldBindingService.View change(@PathVariable String collection,@PathVariable UUID id,@RequestBody WorldBindingService.Change request){return bindings.change(type(collection),id,request);}
    @GetMapping("/{collection:story-cards|outlines|manuscripts}/{id}/world-binding/diff")
    public WorldDraftDtos.Diff diff(@PathVariable String collection,@PathVariable UUID id,@RequestParam UUID toVersionId){return bindings.preview(type(collection),id,toVersionId);}
}
