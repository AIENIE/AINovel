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
import org.springframework.web.bind.annotation.RequestParam;
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
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Admin Ops", description = "AINovel 只读运维观测接口")
@SecurityRequirement(name = "bearerAuth")
public class AdminOpsController {
    private final ApiRequestMetrics requestMetrics;
    private final DependencyHealthService dependencyHealthService;
    private final OpsRecordSearchService recordSearchService;
    private final SettingsService settingsService;

    @Value("${app.records.dir:${APP_RECORD_DIR:/app/records}}")
    private String recordDir;

    @Value("${filebeat.index-prefix:${FILEBEAT_INDEX_PREFIX:aienie-local-ainovel}}")
    private String indexPrefix;

    public AdminOpsController(
            ApiRequestMetrics requestMetrics,
            DependencyHealthService dependencyHealthService,
            OpsRecordSearchService recordSearchService,
            SettingsService settingsService
    ) {
        this.requestMetrics = requestMetrics;
        this.dependencyHealthService = dependencyHealthService;
        this.recordSearchService = recordSearchService;
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
        result.put("es", recordSearchService.status());
        result.put("alerts", deriveAlerts(snapshot, dependencies));
        result.put("generatedAt", Instant.now());
        return result;
    }

    @GetMapping("/dependencies")
    @Operation(summary = "外部依赖健康状态")
    public List<Map<String, Object>> dependencies() {
        return dependencyHealthService.checkAll();
    }

    @GetMapping("/events")
    @Operation(summary = "查询 AINovel 运维事件记录")
    public OpsRecordSearchService.SearchResult events(
            @RequestParam(value = "severity", required = false) String severity,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "from", required = false) Instant from,
            @RequestParam(value = "to", required = false) Instant to,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        return recordSearchService.search(
                List.of(OpsRecordFileSink.OPS_EVENT_RECORD, OpsRecordFileSink.DEPENDENCY_PROBE_RECORD),
                severity,
                category,
                null,
                null,
                null,
                from,
                to,
                page,
                size
        );
    }

    @GetMapping("/audit")
    @Operation(summary = "查询 AINovel 管理操作审计记录")
    public OpsRecordSearchService.SearchResult audit(
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "actor", required = false) String actor,
            @RequestParam(value = "targetType", required = false) String targetType,
            @RequestParam(value = "from", required = false) Instant from,
            @RequestParam(value = "to", required = false) Instant to,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        return recordSearchService.search(OpsRecordFileSink.AUDIT_RECORD, null, "admin", action, actor, targetType, from, to, page, size);
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
        result.put("filebeatIndexPrefix", indexPrefix);
        result.put("elasticsearch", recordSearchService.status());
        result.put("maintenanceMode", settingsService.getGlobalSettings().isMaintenanceMode());
        result.put("generatedAt", Instant.now());
        return result;
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
        Map<String, Object> es = recordSearchService.status();
        if (!Boolean.TRUE.equals(es.get("ready"))) {
            alerts.add(alert("elasticsearch-query-unavailable", "WARN", "Elasticsearch record query is unavailable", es));
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
