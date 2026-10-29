package com.ainovel.app.world;

import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/v1/stories/{id}/world-eras")
public class StoryWorldEraController {
    private final StoryWorldEraService service;
    public StoryWorldEraController(StoryWorldEraService service){this.service=service;}
    @GetMapping public StoryWorldEraService.View view(@PathVariable UUID id){return service.view(id);}
    @PutMapping public StoryWorldEraService.View save(@PathVariable UUID id,@RequestBody StoryWorldEraService.Save request){return service.save(id,request);}
}
