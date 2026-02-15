package com.ainovel.app.security.remote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

@Component
public class ConsulUserGrpcEndpointResolver {

    private static final Logger log = LoggerFactory.getLogger(ConsulUserGrpcEndpointResolver.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    private final UserSessionValidationProperties properties;
    private final ObjectMapper objectMapper;

    private volatile Endpoint cachedEndpoint;
    private volatile long cacheExpireAt;

    public ConsulUserGrpcEndpointResolver(UserSessionValidationProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Optional<Endpoint> resolve() {
        UserSessionValidationProperties.Consul consul = properties.getConsul();
        if (!consul.isEnabled()) {
            return Optional.empty();
        }

        Endpoint endpoint = cachedEndpoint;
        long now = System.currentTimeMillis();
        if (endpoint != null && now < cacheExpireAt) {
            return Optional.of(endpoint);
        }

        synchronized (this) {
            endpoint = cachedEndpoint;
            now = System.currentTimeMillis();
            if (endpoint != null && now < cacheExpireAt) {
                return Optional.of(endpoint);
            }
            Endpoint refreshed = fetchFromConsul(consul);
            cachedEndpoint = refreshed;
            long ttl = Math.max(1, consul.getCacheSeconds()) * 1000L;
            cacheExpireAt = now + ttl;
            return Optional.of(refreshed);
        }
    }

    private Endpoint fetchFromConsul(UserSessionValidationProperties.Consul consul) {
        String serviceName = URLEncoder.encode(consul.getServiceName(), StandardCharsets.UTF_8);
        StringBuilder url = new StringBuilder()
                .append(consul.getScheme())
                .append("://")
                .append(consul.getHost())
                .append(":")
                .append(consul.getPort())
                .append("/v1/health/service/")
                .append(serviceName)
                .append("?passing=true");
        if (consul.getTag() != null && !consul.getTag().isBlank()) {
            url.append("&tag=").append(URLEncoder.encode(consul.getTag(), StandardCharsets.UTF_8));
        }
        if (consul.getDatacenter() != null && !consul.getDatacenter().isBlank()) {
            url.append("&dc=").append(URLEncoder.encode(consul.getDatacenter(), StandardCharsets.UTF_8));
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .GET()
                    .timeout(Duration.ofMillis(Math.max(500L, properties.getTimeoutMs())))
                    .uri(URI.create(url.toString()))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Consul returned status " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (!root.isArray()) {
                throw new IllegalStateException("Consul response is not an array");
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
                    Endpoint endpoint = new Endpoint(address, port);
                    log.info("Resolved userservice grpc endpoint from Consul: {}:{}", endpoint.host(), endpoint.port());
                    return endpoint;
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to resolve userservice grpc endpoint from Consul", e);
        }
        throw new IllegalStateException("No healthy userservice grpc instances found in Consul");
    }

    public record Endpoint(String host, int port) {}
}
