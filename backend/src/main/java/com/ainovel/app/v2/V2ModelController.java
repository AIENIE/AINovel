package com.ainovel.app.v2;

import com.ainovel.app.admin.ops.OpsRecordFileSink;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Tag(name = "V2", description = "AINovel v2 and quality APIs")
@RestController
@RequestMapping("/v2")
public class V2ModelController {
    @org.springframework.beans.factory.annotation.Autowired
    private com.ainovel.app.modelconfig.AiModelConfigurationService modelConfiguration;
    private final ResourceAccessGuard accessGuard;
    private final V2ModelPersistenceService persistenceService;
    private final OpsRecordFileSink recordFileSink;

    @Autowired
    public V2ModelController(ResourceAccessGuard accessGuard,
                             V2ModelPersistenceService persistenceService,
                             OpsRecordFileSink recordFileSink) {
        this.accessGuard = accessGuard;
        this.persistenceService = persistenceService;
        this.recordFileSink = recordFileSink;
    }

    @Operation(summary = "v2 API endpoint")

    @GetMapping("/models")
    public List<Map<String, Object>> listModels(@AuthenticationPrincipal UserDetails principal) {
        accessGuard.currentUser(principal);
        return persistenceService.listModels();
    }

    @Operation(summary = "v2 API endpoint")

    @GetMapping("/models/{modelKey}")
    public Map<String, Object> getModel(@AuthenticationPrincipal UserDetails principal,
                                        @PathVariable String modelKey) {
        accessGuard.currentUser(principal);
        return persistenceService.findModel(modelKey);
    }

    @Operation(summary = "v2 API endpoint")

    @GetMapping("/admin/model-routing")
    @PreAuthorize("hasAuthority('AUTH_LOCAL_ADMIN')")
    @SecurityRequirement(name = "adminSessionCookie")
    public List<Map<String, Object>> listRouting() {
        return modelConfiguration.features(null).stream().map(modelConfiguration::asMap).toList();
    }

    @Operation(summary = "v2 API endpoint")

    @PutMapping("/admin/model-routing/{taskType}")
    @PreAuthorize("hasAuthority('AUTH_LOCAL_ADMIN')")
    @SecurityRequirement(name = "adminSessionCookie")
    public Map<String, Object> updateRouting(Authentication authentication,
                                             @PathVariable String taskType,
                                             @RequestBody V2RequestPayload payload) {
        if(!payload.value().has("options"))throw new com.ainovel.app.common.ApiStatusException(org.springframework.http.HttpStatus.CONFLICT,"AI_ROUTING_PAYLOAD_UPGRADE_REQUIRED");
        Map<String,Object> updated = modelConfiguration.asMap(modelConfiguration.update(taskType, modelConfiguration.parseUpdate(payload.value())));
        recordFileSink.appendAudit(Map.of(
                "category", "admin",
                "action", "model-routing.update",
                "actor", authentication.getName(),
                "targetType", "model-routing",
                "targetId", taskType,
                "result", "SUCCESS",
                "severity", "INFO"
        ));
        return updated;
    }

    @Operation(summary = "v2 API endpoint")

    @GetMapping("/users/me/model-preferences")
    public List<Map<String, Object>> listPreferences(@AuthenticationPrincipal UserDetails principal) {
        User user = accessGuard.currentUser(principal);
        return persistenceService.listPreferences(user);
    }

    @Operation(summary = "v2 API endpoint")

    @PutMapping("/users/me/model-preferences/{taskType}")
    public Map<String, Object> putPreference(@AuthenticationPrincipal UserDetails principal,
                                             @PathVariable String taskType,
                                             @RequestBody V2RequestPayload payload) {
        User user = accessGuard.currentUser(principal);
        if(payload.value().has("preferredOptionId")) {
            modelConfiguration.preference(user,taskType,(String)payload.get("preferredOptionId"));
            return modelConfiguration.asMap(modelConfiguration.feature(taskType,user));
        }
        return persistenceService.savePreference(user, taskType, uuid(payload.get("preferredModelId")));
    }

    @Operation(summary = "v2 API endpoint")

    @DeleteMapping("/users/me/model-preferences/{taskType}")
    public ResponseEntity<Void> resetPreference(@AuthenticationPrincipal UserDetails principal,
                                                @PathVariable String taskType) {
        User user = accessGuard.currentUser(principal);
        persistenceService.resetPreference(user, taskType);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "v2 API endpoint")

    @GetMapping("/users/me/model-usage")
    public Map<String, Object> usageSummary(@AuthenticationPrincipal UserDetails principal) {
        User user = accessGuard.currentUser(principal);
        return persistenceService.usageSummary(user);
    }

    @Operation(summary = "v2 API endpoint")

    @GetMapping("/users/me/model-usage/details")
    public List<Map<String, Object>> usageDetails(@AuthenticationPrincipal UserDetails principal,
                                                  @RequestParam(required = false, defaultValue = "100") int limit) {
        User user = accessGuard.currentUser(principal);
        return persistenceService.usageDetails(user, limit);
    }

    private UUID uuid(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID id) {
            return id;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

}
