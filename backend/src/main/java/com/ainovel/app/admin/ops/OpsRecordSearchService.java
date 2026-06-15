package com.ainovel.app.admin.ops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpsRecordSearchService {
    private static final Logger log = LoggerFactory.getLogger(OpsRecordSearchService.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    @Value("${app.records.elasticsearch-query-enabled:${APP_RECORD_ES_QUERY_ENABLED:true}}")
    private boolean enabled;

    @Value("${elasticsearch.hosts:${ELASTICSEARCH_HOSTS:https://es.localhut.com:9200}}")
    private String elasticsearchHosts;

    @Value("${elasticsearch.username:${ELASTICSEARCH_USERNAME:elastic}}")
    private String username;

    @Value("${elasticsearch.password:${ELASTICSEARCH_PASSWORD:}}")
    private String password;

    @Value("${filebeat.index-prefix:${FILEBEAT_INDEX_PREFIX:aienie-local-ainovel}}")
    private String indexPrefix;

    public OpsRecordSearchService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SearchResult search(String recordType, String severity, String category, String action,
                               String actor, String targetType, Instant from, Instant to, int page, int size) {
        return search(List.of(recordType), severity, category, action, actor, targetType, from, to, page, size);
    }

    public SearchResult search(List<String> recordTypes, String severity, String category, String action,
                               String actor, String targetType, Instant from, Instant to, int page, int size) {
        if (!isReady()) {
            return SearchResult.unavailable("Elasticsearch query is disabled or credentials are missing");
        }
        try {
            JsonNode root = search(query(recordTypes, severity, category, action, actor, targetType, from, to, page, size));
            List<Map<String, Object>> rows = new ArrayList<>();
            JsonNode hits = root.path("hits").path("hits");
            if (hits.isArray()) {
                for (JsonNode hit : hits) {
                    rows.add(objectMapper.convertValue(hit.path("_source"), Map.class));
                }
            }
            long total = root.path("hits").path("total").path("value").asLong(rows.size());
            return new SearchResult(true, null, rows, total, page, size);
        } catch (Exception ex) {
            log.warn("AINovel ops Elasticsearch query failed: {}", ex.getMessage());
            return SearchResult.unavailable("Elasticsearch query failed: " + ex.getMessage());
        }
    }

    public Map<String, Object> status() {
        return Map.of(
                "enabled", enabled,
                "ready", isReady(),
                "indexPattern", indexPrefix + "-*",
                "hostConfigured", elasticsearchHosts != null && !elasticsearchHosts.isBlank(),
                "passwordConfigured", password != null && !password.isBlank()
        );
    }

    private JsonNode search(Map<String, Object> body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/" + indexPrefix + "-*/_search"))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .header("Authorization", basicAuth())
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("status=" + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }

    private Map<String, Object> query(List<String> recordTypes, String severity, String category, String action,
                                      String actor, String targetType, Instant from, Instant to, int page, int size) {
        List<Map<String, Object>> filters = new ArrayList<>();
        filters.add(recordTypeFilter(recordTypes));
        addTerm(filters, "severity.keyword", severity);
        addTerm(filters, "category.keyword", category);
        addTerm(filters, "action.keyword", action);
        addTerm(filters, "actor.keyword", actor);
        addTerm(filters, "targetType.keyword", targetType);
        if (from != null || to != null) {
            Map<String, Object> range = new LinkedHashMap<>();
            if (from != null) {
                range.put("gte", from.toString());
            }
            if (to != null) {
                range.put("lt", to.toString());
            }
            filters.add(Map.of("range", Map.of("createdAt", range)));
        }
        return Map.of(
                "from", Math.max(0, page) * Math.min(100, Math.max(1, size)),
                "size", Math.min(100, Math.max(1, size)),
                "query", Map.of("bool", Map.of("filter", filters)),
                "sort", List.of(Map.of("createdAt", Map.of("order", "desc")))
        );
    }

    private void addTerm(List<Map<String, Object>> filters, String field, String value) {
        if (value != null && !value.isBlank()) {
            filters.add(term(field, value.trim()));
        }
    }

    private Map<String, Object> term(String field, String value) {
        return Map.of("term", Map.of(field, value));
    }

    private Map<String, Object> recordTypeFilter(List<String> recordTypes) {
        List<String> normalized = recordTypes == null
                ? List.of()
                : recordTypes.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (normalized.size() <= 1) {
            return term("recordType.keyword", normalized.isEmpty() ? "" : normalized.get(0));
        }
        return Map.of("terms", Map.of("recordType.keyword", normalized));
    }

    private boolean isReady() {
        return enabled && elasticsearchHosts != null && !elasticsearchHosts.isBlank()
                && password != null && !password.isBlank();
    }

    private String baseUrl() {
        return elasticsearchHosts.split(",")[0].trim().replaceAll("/+$", "");
    }

    private String basicAuth() {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    public record SearchResult(
            boolean available,
            String message,
            List<Map<String, Object>> items,
            long total,
            int page,
            int size
    ) {
        static SearchResult unavailable(String message) {
            return new SearchResult(false, message, List.of(), 0L, 0, 0);
        }
    }
}
