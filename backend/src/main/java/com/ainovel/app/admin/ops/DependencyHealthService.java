package com.ainovel.app.admin.ops;

import com.ainovel.app.integration.ConsulServiceResolver;
import com.ainovel.app.integration.ExternalServiceProperties;
import com.ainovel.app.security.remote.UserSessionValidationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DependencyHealthService {
    private final JdbcTemplate jdbcTemplate;
    private final RedisConnectionFactory redisConnectionFactory;
    private final ExternalServiceProperties externalServiceProperties;
    private final UserSessionValidationProperties userSessionValidationProperties;
    private final OpsRecordFileSink recordFileSink;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    @Value("${qdrant.host:http://base.seekerhut.com}")
    private String qdrantHost;

    @Value("${qdrant.http-port:26333}")
    private int qdrantPort;

    @Value("${qdrant.enabled:true}")
    private boolean qdrantEnabled;

    public DependencyHealthService(
            JdbcTemplate jdbcTemplate,
            RedisConnectionFactory redisConnectionFactory,
            ExternalServiceProperties externalServiceProperties,
            UserSessionValidationProperties userSessionValidationProperties,
            OpsRecordFileSink recordFileSink
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisConnectionFactory = redisConnectionFactory;
        this.externalServiceProperties = externalServiceProperties;
        this.userSessionValidationProperties = userSessionValidationProperties;
        this.recordFileSink = recordFileSink;
    }

    public List<Map<String, Object>> checkAll() {
        List<Map<String, Object>> result = new ArrayList<>();
        result.add(check("database", "MySQL", "local", "jdbc", "configured", this::checkDatabase));
        result.add(check("cache", "Redis", "local", "redis", "configured", this::checkRedis));
        result.add(check("vector", "Qdrant", "local", "http", qdrantBaseUrl(), () -> checkHttp(qdrantBaseUrl() + "/collections")));
        result.add(check("user-http", "user-service HTTP", "external", "http",
                sanitizeHttp(externalServiceProperties.getUserserviceHttp().getAddress()),
                () -> checkHttp(externalServiceProperties.getUserserviceHttp().getAddress() + "/actuator/health")));
        result.add(checkTcp("user-grpc", "user-service gRPC", userSessionValidationProperties.getGrpcAddress()));
        result.add(checkTcp("ai-grpc", "ai-service gRPC", externalServiceProperties.getAiserviceGrpc().getAddress()));
        result.add(checkTcp("pay-grpc", "pay-service gRPC", externalServiceProperties.getPayserviceGrpc().getAddress()));
        return result;
    }

    private Map<String, Object> checkTcp(String key, String name, String rawAddress) {
        return check(key, name, "external", "grpc", sanitizeAddress(rawAddress), () -> {
            ConsulServiceResolver.Endpoint endpoint = ConsulServiceResolver.parseAddress(rawAddress)
                    .orElseThrow(() -> new IllegalStateException("endpoint not configured"));
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(endpoint.host(), endpoint.port()), 2_000);
            }
        });
    }

    private Map<String, Object> check(String key, String name, String category, String protocol, String endpoint, Probe probe) {
        Instant checkedAt = Instant.now();
        long started = System.nanoTime();
        String status = "UP";
        String message = "OK";
        if ("vector".equals(key) && !qdrantEnabled) {
            status = "DEGRADED";
            message = "Qdrant disabled";
        } else {
            try {
                probe.run();
            } catch (Exception ex) {
                status = "DOWN";
                message = clean(ex.getMessage());
            }
        }
        long latencyMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("name", name);
        item.put("category", category);
        item.put("protocol", protocol);
        item.put("endpoint", endpoint);
        item.put("status", status);
        item.put("latencyMs", latencyMs);
        item.put("message", message);
        item.put("checkedAt", checkedAt);
        recordFileSink.appendDependencyProbe(Map.of(
                "category", "dependency",
                "dependencyKey", key,
                "dependencyName", name,
                "status", status,
                "result", "UP".equals(status) ? "SUCCESS" : "FAILED",
                "severity", "UP".equals(status) ? "INFO" : "WARN",
                "latencyMs", latencyMs,
                "message", message
        ));
        return item;
    }

    private void checkDatabase() {
        jdbcTemplate.queryForObject("select 1", Integer.class);
    }

    private void checkRedis() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            connection.ping();
        }
    }

    private void checkHttp(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 500) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
    }

    private String qdrantBaseUrl() {
        String value = qdrantHost == null || qdrantHost.isBlank() ? "http://base.seekerhut.com" : qdrantHost.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (!value.matches("https?://.*:\\d+$")) {
            value = value + ":" + qdrantPort;
        }
        return value;
    }

    private String sanitizeAddress(String raw) {
        return ConsulServiceResolver.parseAddress(raw)
                .map(ConsulServiceResolver.Endpoint::toAuthority)
                .orElse(raw == null || raw.isBlank() ? "not-configured" : raw.replaceAll("(?i)(token|password|secret)=([^&]+)", "$1=<redacted>"));
    }

    private String sanitizeHttp(String raw) {
        if (raw == null || raw.isBlank()) {
            return "not-configured";
        }
        try {
            URI uri = URI.create(raw);
            return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        } catch (Exception ignored) {
            return raw;
        }
    }

    private String clean(String message) {
        if (message == null || message.isBlank()) {
            return "probe failed";
        }
        return message.replaceAll("(?i)(password|token|secret)=\\S+", "$1=<redacted>");
    }

    private interface Probe {
        void run() throws Exception;
    }
}
