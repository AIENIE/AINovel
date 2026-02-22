package com.ainovel.app.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConsulServiceResolver {
    private static final Logger log = LoggerFactory.getLogger(ConsulServiceResolver.class);

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private final ExternalServiceProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public ConsulServiceResolver(ExternalServiceProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Optional<Endpoint> resolve(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            return Optional.empty();
        }
        ExternalServiceProperties.Discovery discovery = properties.getDiscovery();
        if (!discovery.isEnabled()) {
            return Optional.empty();
        }

        CacheEntry cached = cache.get(serviceName);
        long now = System.currentTimeMillis();
        if (cached != null && now < cached.expireAtMs()) {
            return Optional.of(cached.endpoint());
        }

        Endpoint endpoint = fetchFromConsul(serviceName, discovery);
        long ttlMs = Math.max(1, discovery.getCacheSeconds()) * 1000L;
        cache.put(serviceName, new CacheEntry(endpoint, now + ttlMs));
        return Optional.of(endpoint);
    }

    public Optional<Endpoint> resolveOrFallback(String serviceName, String fallback) {
        Optional<Endpoint> fromConsul = Optional.empty();
        try {
            fromConsul = resolve(serviceName);
        } catch (Exception ignored) {
        }

        int timeoutMs = (int) Math.max(300L, properties.getTimeoutMs());
        if (fromConsul.isPresent()) {
            Endpoint endpoint = fromConsul.get();
            if (isTcpReachable(endpoint.host(), endpoint.port(), timeoutMs)) {
                return fromConsul;
            }
            log.warn("Consul endpoint unreachable for {}: {}:{}", serviceName, endpoint.host(), endpoint.port());
        }

        Optional<Endpoint> fallbackEndpoint = parseAddress(fallback);
        if (fallbackEndpoint.isPresent()) {
            Endpoint endpoint = fallbackEndpoint.get();
            if (isTcpReachable(endpoint.host(), endpoint.port(), timeoutMs)) {
                if (fromConsul.isPresent()) {
                    log.info("Switching {} to fallback endpoint {}:{} after Consul endpoint check failed",
                            serviceName, endpoint.host(), endpoint.port());
                }
                return fallbackEndpoint;
            }
            log.warn("Fallback endpoint unreachable for {}: {}:{}", serviceName, endpoint.host(), endpoint.port());
        }

        return fromConsul.isPresent() ? fromConsul : fallbackEndpoint;
    }

    private Endpoint fetchFromConsul(String serviceName, ExternalServiceProperties.Discovery discovery) {
        String encoded = URLEncoder.encode(serviceName, StandardCharsets.UTF_8);
        StringBuilder url = new StringBuilder()
                .append(discovery.getScheme())
                .append("://")
                .append(discovery.getHost())
                .append(":")
                .append(discovery.getPort())
                .append("/v1/health/service/")
                .append(encoded)
                .append("?passing=true");
        if (discovery.getDatacenter() != null && !discovery.getDatacenter().isBlank()) {
            url.append("&dc=").append(URLEncoder.encode(discovery.getDatacenter(), StandardCharsets.UTF_8));
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url.toString()))
                    .GET()
                    .timeout(Duration.ofMillis(Math.max(800L, properties.getTimeoutMs())))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Consul status=" + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (!root.isArray()) {
                throw new IllegalStateException("Consul response is not array");
            }
            for (JsonNode item : root) {
                JsonNode service = item.path("Service");
                int port = service.path("Port").asInt(0);
                if (port <= 0) {
                    continue;
                }
                String address = service.path("Address").asText("");
                if (address == null || address.isBlank()) {
                    address = item.path("Node").path("Address").asText("");
                }
                if (address != null && !address.isBlank()) {
                    return new Endpoint(address, port);
                }
            }
            throw new IllegalStateException("No healthy instance found");
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to resolve service from Consul: " + serviceName, ex);
        }
    }

    public static Optional<Endpoint> parseAddress(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String value = raw.trim();
        if (value.startsWith("http://") || value.startsWith("https://")) {
            try {
                URI uri = URI.create(value);
                if (uri.getHost() != null && uri.getPort() > 0) {
                    return Optional.of(new Endpoint(uri.getHost(), uri.getPort()));
                }
            } catch (Exception ignored) {
                return Optional.empty();
            }
        }
        if (value.startsWith("static://")) {
            value = value.substring("static://".length());
        }
        if (value.contains("://")) {
            try {
                URI uri = URI.create(value);
                if (uri.getHost() != null && uri.getPort() > 0) {
                    return Optional.of(new Endpoint(uri.getHost(), uri.getPort()));
                }
            } catch (Exception ignored) {
                return Optional.empty();
            }
        }

        int idx = value.lastIndexOf(':');
        if (idx <= 0 || idx >= value.length() - 1) {
            return Optional.empty();
        }
        String host = value.substring(0, idx).trim();
        String portStr = value.substring(idx + 1).trim();
        if (host.isBlank()) {
            return Optional.empty();
        }
        try {
            int port = Integer.parseInt(portStr);
            if (port <= 0) {
                return Optional.empty();
            }
            return Optional.of(new Endpoint(host, port));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public record Endpoint(String host, int port) {
        public String toHttpBase() {
            return "http://" + host + ":" + port;
        }

        public String toAuthority() {
            return host + ":" + port;
        }
    }

    private record CacheEntry(Endpoint endpoint, long expireAtMs) {
    }

    private boolean isTcpReachable(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
