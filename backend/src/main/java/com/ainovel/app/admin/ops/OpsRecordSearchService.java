package com.ainovel.app.admin.ops;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Keeps the historical record API response while directing runtime logs to the central console. */
@Component
public class OpsRecordSearchService {
    @Value("${ENV:local}")
    private String environment = "local";

    public String consoleUrl() {
        String mapped = switch (environment) {
            case "local" -> "develop";
            case "test" -> "staging";
            case "production" -> "production";
            default -> "";
        };
        return "https://control.testhut.top/config-center/logs?component=ai-novel"
                + (mapped.isEmpty() ? "" : "&environment=" + mapped);
    }

    public SearchResult search(String recordType, String severity, String category, String action,
                               String actor, String targetType, Instant from, Instant to, int page, int size) {
        return search(List.of(recordType), severity, category, action, actor, targetType, from, to, page, size);
    }

    public SearchResult search(List<String> recordTypes, String severity, String category, String action,
                               String actor, String targetType, Instant from, Instant to, int page, int size) {
        return new SearchResult(false,
                "ES record queries have been retired. Runtime logs are available in the central console; independent record files retain their existing policy.",
                List.of(), 0L, page, size);
    }

    public Map<String, Object> status() {
        return Map.of("enabled", false, "ready", false, "retired", true,
                "backend", "loki", "consoleUrl", consoleUrl());
    }

    public record SearchResult(boolean available, String message, List<Map<String, Object>> items,
                               long total, int page, int size) {}
}
