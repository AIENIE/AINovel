package com.ainovel.app.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/api-management")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class ApiManagementController {

    private final ApiManagementService apiManagementService;

    public ApiManagementController(ApiManagementService apiManagementService) {
        this.apiManagementService = apiManagementService;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        return apiManagementService.summary();
    }

    @GetMapping("/openapi")
    public ResponseEntity<String> openapi() {
        return apiManagementService.openapiJson();
    }

    @GetMapping("/bundle")
    public ResponseEntity<Map<String, Object>> bundle(
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch
    ) {
        return apiManagementService.bundle(ifNoneMatch);
    }
}

