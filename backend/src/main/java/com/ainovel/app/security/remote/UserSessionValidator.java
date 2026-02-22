package com.ainovel.app.security.remote;

import fireflychat.user.v1.UserAuthServiceGrpc;
import fireflychat.user.v1.ValidateSessionRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Component
@Profile("!test")
@ConditionalOnProperty(name = "sso.session-validation.enabled", havingValue = "true", matchIfMissing = true)
public class UserSessionValidator {
    private static final Logger log = LoggerFactory.getLogger(UserSessionValidator.class);

    private final ConsulUserGrpcEndpointResolver consulResolver;
    private final UserSessionValidationProperties properties;

    private final ConcurrentMap<String, EndpointClient> endpointClients = new ConcurrentHashMap<>();

    public UserSessionValidator(
            ConsulUserGrpcEndpointResolver consulResolver,
            UserSessionValidationProperties properties
    ) {
        this.consulResolver = consulResolver;
        this.properties = properties;
    }

    public boolean validate(long userId, String sessionId) {
        if (userId <= 0 || sessionId == null || sessionId.isBlank()) {
            return false;
        }

        int connectTimeout = (int) Math.max(300L, properties.getTimeoutMs());
        for (ConsulUserGrpcEndpointResolver.Endpoint endpoint : resolveCandidates()) {
            if (!isTcpReachable(endpoint.host(), endpoint.port(), connectTimeout)) {
                log.warn("Userservice session validation endpoint unreachable: {}:{}", endpoint.host(), endpoint.port());
                continue;
            }

            EndpointClient client;
            try {
                client = getOrCreateClient(endpoint);
            } catch (Exception e) {
                log.warn("Create userservice session validation client failed: {}:{} -> {}", endpoint.host(), endpoint.port(), e.getMessage());
                continue;
            }

            try {
                boolean valid = client.stub()
                        .withDeadlineAfter(Math.max(500L, properties.getTimeoutMs()), TimeUnit.MILLISECONDS)
                        .validateSession(ValidateSessionRequest.newBuilder()
                                .setUserId(userId)
                                .setSessionId(sessionId)
                                .build())
                        .getValid();
                if (valid) {
                    return true;
                }
            } catch (Exception e) {
                log.warn("Userservice session validation RPC failed at {}:{} -> {}", endpoint.host(), endpoint.port(), e.getMessage());
            }
        }
        return false;
    }

    private EndpointClient getOrCreateClient(ConsulUserGrpcEndpointResolver.Endpoint endpoint) {
        String key = endpoint.host() + ":" + endpoint.port();
        return endpointClients.computeIfAbsent(key, ignored -> {
            ManagedChannel channel = ManagedChannelBuilder.forAddress(endpoint.host(), endpoint.port())
                    .usePlaintext()
                    .build();
            return new EndpointClient(endpoint.host(), endpoint.port(), channel, UserAuthServiceGrpc.newBlockingStub(channel));
        });
    }

    private List<ConsulUserGrpcEndpointResolver.Endpoint> resolveCandidates() {
        LinkedHashMap<String, ConsulUserGrpcEndpointResolver.Endpoint> ordered = new LinkedHashMap<>();

        try {
            Optional<ConsulUserGrpcEndpointResolver.Endpoint> fromConsul = consulResolver.resolve();
            fromConsul.ifPresent(endpoint -> ordered.put(endpoint.host() + ":" + endpoint.port(), endpoint));
        } catch (Exception ex) {
            log.warn("Consul userservice discovery failed, fallback to USER_GRPC_ADDR: {}", ex.getMessage());
        }

        parseGrpcAddress(properties.getGrpcFallbackAddress())
                .ifPresent(endpoint -> {
                    String key = endpoint.host() + ":" + endpoint.port();
                    if (!ordered.containsKey(key)) {
                        log.info("Using fallback userservice grpc endpoint candidate: {}:{}", endpoint.host(), endpoint.port());
                        ordered.put(key, endpoint);
                    }
                });

        return List.copyOf(ordered.values());
    }

    static Optional<ConsulUserGrpcEndpointResolver.Endpoint> parseGrpcAddress(String rawAddress) {
        if (rawAddress == null || rawAddress.isBlank()) {
            return Optional.empty();
        }
        String value = rawAddress.trim();
        if (value.startsWith("static://")) {
            value = value.substring("static://".length());
        } else if (value.startsWith("dns:///")) {
            value = value.substring("dns:///".length());
        } else if (value.contains("://")) {
            try {
                URI uri = URI.create(value);
                if (uri.getHost() != null && uri.getPort() > 0) {
                    return Optional.of(new ConsulUserGrpcEndpointResolver.Endpoint(uri.getHost(), uri.getPort()));
                }
            } catch (Exception ignored) {
                return Optional.empty();
            }
        }

        int lastColon = value.lastIndexOf(':');
        if (lastColon <= 0 || lastColon >= value.length() - 1) {
            return Optional.empty();
        }
        String host = value.substring(0, lastColon).trim();
        String portStr = value.substring(lastColon + 1).trim();
        if (host.isBlank()) {
            return Optional.empty();
        }
        try {
            int port = Integer.parseInt(portStr);
            if (port <= 0) {
                return Optional.empty();
            }
            return Optional.of(new ConsulUserGrpcEndpointResolver.Endpoint(host, port));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    @PreDestroy
    public void shutdown() {
        endpointClients.values().forEach(EndpointClient::close);
        endpointClients.clear();
    }

    private record EndpointClient(
            String host,
            int port,
            ManagedChannel channel,
            UserAuthServiceGrpc.UserAuthServiceBlockingStub stub
    ) {
        boolean sameEndpoint(String targetHost, int targetPort) {
            return host.equals(targetHost) && port == targetPort;
        }

        void close() {
            channel.shutdownNow();
        }
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
