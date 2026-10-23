package com.ainovel.app.world;

import org.springframework.web.bind.annotation.*;
import java.util.*;
import static com.ainovel.app.world.WorldDraftDtos.*;

@RestController
@RequestMapping("/v1/worlds")
public class WorldDraftController {
    private final WorldVersionService versions;
    public WorldDraftController(WorldVersionService versions){this.versions=versions;}
    @GetMapping("/entry-types") public Map<String,String> types(){return WorldEntryStore.TYPES;}
    @GetMapping("/{id}/draft") public Draft draft(@PathVariable UUID id){return versions.draft(id);}
    @PutMapping("/{id}/draft") public Draft save(@PathVariable UUID id,@RequestBody Save request){return versions.save(id,request);}
    @GetMapping("/{id}/versions") public List<Version> versions(@PathVariable UUID id){return versions.list(id);}
    @GetMapping("/{id}/versions/{versionId}") public Version version(@PathVariable UUID id,@PathVariable UUID versionId){return versions.get(id,versionId);}
    @GetMapping("/{id}/diff") public Diff diff(@PathVariable UUID id,@RequestParam(required=false) UUID fromVersionId,
        @RequestParam(required=false) UUID toVersionId,@RequestParam(required=false) Long draftRevision){return versions.diff(id,fromVersionId,toVersionId,draftRevision);}
    @PostMapping("/{id}/publish") public Version publish(@PathVariable UUID id,@RequestBody Publish request){return versions.publish(id,request);}
}
