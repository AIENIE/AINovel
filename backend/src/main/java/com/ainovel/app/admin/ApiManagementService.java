package com.ainovel.app.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ApiManagementService {

    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public ApiManagementService(Environment environment, ObjectMapper objectMapper) {
        this.environment = environment;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    public Map<String, Object> summary() {
        requireEnabled();
        Instant now = Instant.now();

        String springAppName = environment.getProperty("spring.application.name", "ainovel");
        String version = resolveVersion();
        String env = resolveEnvironment();
        String apiPrefix = normalizeContextPath(environment.getProperty("spring.mvc.servlet.path", ""));

        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("swaggerUi", "/admin/api-management");
        endpoints.put("openapiJson", apiPrefix + "/admin/api-management/openapi");
        endpoints.put("bundle", apiPrefix + "/admin/api-management/bundle");

        Map<String, Object> svc = new LinkedHashMap<>();
        svc.put("name", "AINovel");
        svc.put("springApplicationName", springAppName);
        svc.put("version", version);
        svc.put("environment", env);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("service", svc);
        out.put("endpoints", endpoints);
        out.put("generatedAt", now.toString());
        return out;
    }

    public ResponseEntity<String> openapiJson() {
        requireEnabled();
        String json;
        try {
            Integer port = environment.getProperty("local.server.port", Integer.class);
            if (port == null || port <= 0) {
                port = environment.getProperty("server.port", Integer.class, 0);
            }
            if (port == null || port <= 0) throw new IllegalStateException("server port not resolved");

            String apiPrefix = normalizeContextPath(environment.getProperty("spring.mvc.servlet.path", ""));
            String url = "http://127.0.0.1:" + port + apiPrefix + "/v3/api-docs";
            json = restTemplate.getForObject(url, String.class);
            if (json == null || json.isBlank()) throw new IllegalStateException("empty /v3/api-docs response");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "生成 OpenAPI 失败", e);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(json);
    }

    public ResponseEntity<Map<String, Object>> bundle(String ifNoneMatch) {
        requireEnabled();
        Map<String, Object> summary = summary();
        String openapiJson = openapiJson().getBody();
        JsonNode openapi = parseJson(openapiJson);

        String etag = "\"" + sha256Hex(openapiJson == null ? "" : openapiJson) + "\"";
        if (ifNoneMatch != null && !ifNoneMatch.isBlank() && ifNoneMatch.trim().equals(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .header(HttpHeaders.ETAG, etag)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .build();
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", summary);
        out.put("openapi", openapi);
        out.put("openapiSha256", etag.replace("\"", ""));
        out.put("generatedAt", Instant.now().toString());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.ETAG, etag)
                .body(out);
    }

    private void requireEnabled() {
        Boolean enabled = environment.getProperty("api-management.enabled", Boolean.class, true);
        if (enabled == null || !enabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "API management disabled");
        }
    }

    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) return objectMapper.nullNode();
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "OpenAPI JSON 解析失败", e);
        }
    }

    private String resolveEnvironment() {
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0) return environment.getProperty("spring.profiles.active", "");
        return String.join(",", profiles);
    }

    private String resolveVersion() {
        String v = environment.getProperty("build.version");
        if (v != null && !v.isBlank()) return v;
        v = environment.getProperty("info.app.version");
        if (v != null && !v.isBlank()) return v;
        Package p = ApiManagementService.class.getPackage();
        if (p != null && p.getImplementationVersion() != null) return p.getImplementationVersion();
        return "";
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] out = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (Exception e) {
            return "";
        }
    }

    private static String normalizeContextPath(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty() || "/".equals(s)) return "";
        if (!s.startsWith("/")) s = "/" + s;
        if (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }
}
