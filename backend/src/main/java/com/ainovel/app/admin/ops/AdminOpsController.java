package com.ainovel.app.admin.ops;

import com.ainovel.app.metrics.ApiRequestMetrics;
import com.ainovel.app.settings.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/admin/ops")
@PreAuthorize("hasAuthority('AUTH_LOCAL_ADMIN')")
@Tag(name = "Admin Ops", description = "AINovel 只读运维观测接口")
@SecurityRequirement(name = "adminSessionCookie")
public class AdminOpsController {
    private final ApiRequestMetrics requestMetrics;
    private final DependencyHealthService dependencyHealthService;
    private final String environment;
    private final SettingsService settingsService;

    @Value("${app.records.dir:${APP_RECORD_DIR:./var/records}}")
    private String recordDir;

    public AdminOpsController(
            ApiRequestMetrics requestMetrics,
            DependencyHealthService dependencyHealthService,
            SettingsService settingsService,
            @Value("${ENV}") String environment
    ) {
        this.requestMetrics = requestMetrics;
        this.dependencyHealthService = dependencyHealthService;
        this.environment = environment;
        this.settingsService = settingsService;
    }

    @GetMapping("/summary")
    @Operation(summary = "运维观测总览")
    public Map<String, Object> summary() {
        ApiRequestMetrics.Snapshot snapshot = requestMetrics.snapshot();
        List<Map<String, Object>> dependencies = dependencyHealthService.checkAll();
        long down = dependencies.stream().filter(item -> !"UP".equals(item.get("status"))).count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requests", snapshot);
        result.put("dependencyTotal", dependencies.size());
        result.put("dependencyIssues", down);
        result.put("maintenanceMode", settingsService.getGlobalSettings().isMaintenanceMode());
        result.put("logsUrl", consoleUrl());
        result.put("alerts", deriveAlerts(snapshot, dependencies));
        result.put("generatedAt", Instant.now());
        return result;
    }

    @GetMapping("/dependencies")
    @Operation(summary = "外部依赖健康状态")
    public List<Map<String, Object>> dependencies() {
        return dependencyHealthService.checkAll();
    }

    @GetMapping("/alerts")
    @Operation(summary = "只读派生告警")
    public List<Map<String, Object>> alerts() {
        return deriveAlerts(requestMetrics.snapshot(), dependencyHealthService.checkAll());
    }

    @GetMapping("/diagnostics")
    @Operation(summary = "脱敏运行诊断")
    public Map<String, Object> diagnostics() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("service", "ainovel");
        result.put("javaVersion", System.getProperty("java.version"));
        result.put("uptimeMs", ManagementFactory.getRuntimeMXBean().getUptime());
        result.put("recordDir", recordDir);
        result.put("logsUrl", consoleUrl());
        result.put("maintenanceMode", settingsService.getGlobalSettings().isMaintenanceMode());
        result.put("generatedAt", Instant.now());
        return result;
    }

    private String consoleUrl() {
        String target = switch (environment) {
            case "local" -> "develop";
            case "test" -> "staging";
            case "production" -> "production";
            default -> throw new IllegalStateException("Unknown runtime environment");
        };
        return "https://control.testhut.top/config-center/logs?component=ai-novel&environment=" + target;
    }

    private List<Map<String, Object>> deriveAlerts(ApiRequestMetrics.Snapshot snapshot, List<Map<String, Object>> dependencies) {
        List<Map<String, Object>> alerts = new ArrayList<>();
        if (snapshot.errorRate() >= 0.2) {
            alerts.add(alert("http-error-rate-critical", "CRITICAL", "HTTP 5xx error rate is high", snapshot.errorRate()));
        } else if (snapshot.errorRate() >= 0.05) {
            alerts.add(alert("http-error-rate-warning", "WARN", "HTTP 5xx error rate needs attention", snapshot.errorRate()));
        }
        dependencies.stream()
                .filter(item -> !"UP".equals(item.get("status")))
                .forEach(item -> alerts.add(alert(
                        "dependency-" + item.get("key"),
                        "DOWN".equals(item.get("status")) ? "WARN" : "INFO",
                        item.get("name") + " is " + item.get("status"),
                        item.get("message")
                )));
        if (settingsService.getGlobalSettings().isMaintenanceMode()) {
            alerts.add(alert("maintenance-mode", "INFO", "AINovel maintenance mode is enabled", true));
        }
        alerts.sort(Comparator.comparing(item -> String.valueOf(item.get("severity"))));
        return alerts;
    }

    private Map<String, Object> alert(String key, String severity, String title, Object detail) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("severity", severity);
        item.put("title", title);
        item.put("detail", detail);
        item.put("generatedAt", Instant.now());
        return item;
    }
}
