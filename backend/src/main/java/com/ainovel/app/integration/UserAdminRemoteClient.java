package com.ainovel.app.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class UserAdminRemoteClient {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private final ExternalServiceProperties properties;
    private final ConsulServiceResolver resolver;
    private final ObjectMapper objectMapper;

    public UserAdminRemoteClient(
            ExternalServiceProperties properties,
            ConsulServiceResolver resolver,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.resolver = resolver;
        this.objectMapper = objectMapper;
    }

    public List<RemoteAdminUser> listUsers(String authorizationHeader, String search) {
        String base = resolveBaseUrl();
        StringBuilder url = new StringBuilder(base).append("/api/admin/users");
        if (search != null && !search.isBlank()) {
            url.append("?search=").append(URLEncoder.encode(search, StandardCharsets.UTF_8));
        }
        JsonNode body = sendJson("GET", URI.create(url.toString()), authorizationHeader, null);
        List<RemoteAdminUser> users = new ArrayList<>();
        if (body.isArray()) {
            for (JsonNode n : body) {
                users.add(new RemoteAdminUser(
                        n.path("id").asLong(0),
                        n.path("username").asText(""),
                        n.path("email").asText(""),
                        n.path("role").asText("USER"),
                        n.path("banned").asBoolean(false),
                        parseInstant(n.path("bannedUntil").asText(null)),
                        parseInstant(n.path("createdAt").asText(null))
                ));
            }
        }
        return users;
    }

    public RemoteAdminUser banUser(String authorizationHeader, long userId, int days, String reason) {
        String url = resolveBaseUrl() + "/api/admin/users/" + userId + "/ban";
        String body = "{\"days\":" + Math.max(1, days) + ",\"reason\":\"" + escapeJson(reason == null ? "manual_ban" : reason) + "\"}";
        JsonNode node = sendJson("POST", URI.create(url), authorizationHeader, body);
        return new RemoteAdminUser(
                node.path("id").asLong(userId),
                node.path("username").asText(""),
                node.path("email").asText(""),
                node.path("role").asText("USER"),
                node.path("banned").asBoolean(true),
                parseInstant(node.path("bannedUntil").asText(null)),
                parseInstant(node.path("createdAt").asText(null))
        );
    }

    public RemoteAdminUser unbanUser(String authorizationHeader, long userId) {
        String url = resolveBaseUrl() + "/api/admin/users/" + userId + "/unban";
        JsonNode node = sendJson("POST", URI.create(url), authorizationHeader, "{}");
        return new RemoteAdminUser(
                node.path("id").asLong(userId),
                node.path("username").asText(""),
                node.path("email").asText(""),
                node.path("role").asText("USER"),
                node.path("banned").asBoolean(false),
                parseInstant(node.path("bannedUntil").asText(null)),
                parseInstant(node.path("createdAt").asText(null))
        );
    }

    private JsonNode sendJson(String method, URI uri, String authorizationHeader, String requestBody) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofMillis(Math.max(800L, properties.getTimeoutMs())))
                    .header("Content-Type", "application/json");
            if (authorizationHeader != null && !authorizationHeader.isBlank()) {
                builder.header("Authorization", authorizationHeader);
            }

            HttpRequest request = switch (method) {
                case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(requestBody == null ? "{}" : requestBody)).build();
                case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofString(requestBody == null ? "{}" : requestBody)).build();
                case "DELETE" -> builder.DELETE().build();
                default -> builder.GET().build();
            };
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("UserService HTTP " + response.statusCode());
            }
            String raw = response.body() == null ? "{}" : response.body();
            if (raw.isBlank()) {
                raw = "{}";
            }
            return objectMapper.readTree(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("UserService request failed: " + ex.getMessage(), ex);
        }
    }

    private String resolveBaseUrl() {
        ExternalServiceProperties.ServiceTarget target = properties.getUserserviceHttp();
        String preferred = normalizeHttpUrl(target.getFallback());
        if (preferred != null) {
            return preferred;
        }
        Optional<ConsulServiceResolver.Endpoint> endpoint = resolver.resolveOrFallback(target.getServiceName(), target.getFallback());
        return endpoint.map(ConsulServiceResolver.Endpoint::toHttpBase)
                .orElseGet(() -> normalizeBaseUrl(target.getFallback()));
    }

    private String normalizeHttpUrl(String fallback) {
        if (fallback == null || fallback.isBlank()) {
            return null;
        }
        String value = fallback.trim();
        if (value.startsWith("http://") || value.startsWith("https://")) {
            if (value.endsWith("/")) {
                return value.substring(0, value.length() - 1);
            }
            return value;
        }
        return null;
    }

    private String normalizeBaseUrl(String fallback) {
        if (fallback == null || fallback.isBlank()) {
            throw new IllegalStateException("userservice-http fallback is empty");
        }
        String value = fallback.trim();
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            Optional<ConsulServiceResolver.Endpoint> endpoint = ConsulServiceResolver.parseAddress(value);
            if (endpoint.isEmpty()) {
                throw new IllegalStateException("Invalid userservice-http fallback: " + fallback);
            }
            return endpoint.get().toHttpBase();
        }
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record RemoteAdminUser(
            long id,
            String username,
            String email,
            String role,
            boolean banned,
            Instant bannedUntil,
            Instant createdAt
    ) {
    }
}
