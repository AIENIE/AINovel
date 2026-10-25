package com.ainovel.app.world;

import com.ainovel.app.common.CurrentUserResolver;
import com.ainovel.app.world.dto.WorldDetailDto;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/v1/worlds/{id}")
public class WorldManagementController {
    private final WorldManagementService service;
    private final CurrentUserResolver users;
    public WorldManagementController(WorldManagementService service,CurrentUserResolver users){this.service=service;this.users=users;}
    @PostMapping("/copy") public WorldDetailDto copy(@AuthenticationPrincipal UserDetails principal,@PathVariable UUID id,@RequestBody WorldManagementService.Copy request) {
        return service.copy(users.require(principal),id,request);
    }
    @GetMapping("/export") public ResponseEntity<byte[]> export(@PathVariable UUID id,@RequestParam String source,
            @RequestParam(required=false) Long expectedRevision,@RequestParam(required=false) UUID versionId,@RequestParam String format) {
        var download=service.export(id,source,expectedRevision,versionId,format);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(download.contentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(download.filename(),StandardCharsets.UTF_8).build().toString()).body(download.bytes());
    }
}
