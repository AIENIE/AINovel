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
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
@Profile("!test")
@ConditionalOnProperty(name = "sso.session-validation.enabled", havingValue = "true", matchIfMissing = true)
public class UserSessionValidator {
    private static final Logger log = LoggerFactory.getLogger(UserSessionValidator.class);

    private final ConsulUserGrpcEndpointResolver consulResolver;
    private final UserSessionValidationProperties properties;

    private volatile EndpointClient endpointClient;

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
        EndpointClient client;
        try {
            client = getOrCreateClient();
        } catch (Exception e) {
            return false;
        }
        if (client == null) {
            return false;
        }
        if (!isTcpReachable(client.host(), client.port(), (int) Math.max(300L, properties.getTimeoutMs()))) {
            return false;
        }
        try {
            return client.stub()
                    .withDeadlineAfter(Math.max(500L, properties.getTimeoutMs()), TimeUnit.MILLISECONDS)
                    .validateSession(ValidateSessionRequest.newBuilder()
                            .setUserId(userId)
                            .setSessionId(sessionId)
                            .build())
                    .getValid();
        } catch (Exception e) {
            return false;
        }
    }

    private synchronized EndpointClient getOrCreateClient() {
        ConsulUserGrpcEndpointResolver.Endpoint endpoint = resolveEndpoint().orElse(null);
        if (endpoint == null) {
            return null;
        }

        EndpointClient existing = endpointClient;
        if (existing != null && existing.sameEndpoint(endpoint.host(), endpoint.port())) {
            return existing;
        }

        ManagedChannel channel = ManagedChannelBuilder.forAddress(endpoint.host(), endpoint.port())
                .usePlaintext()
                .build();
        EndpointClient next = new EndpointClient(endpoint.host(), endpoint.port(), channel, UserAuthServiceGrpc.newBlockingStub(channel));

        if (existing != null) {
            existing.close();
        }
        endpointClient = next;
        return next;
    }

    private Optional<ConsulUserGrpcEndpointResolver.Endpoint> resolveEndpoint() {
        try {
            Optional<ConsulUserGrpcEndpointResolver.Endpoint> fromConsul = consulResolver.resolve();
            if (fromConsul.isPresent()) {
                return fromConsul;
            }
        } catch (Exception ex) {
            log.warn("Consul userservice discovery failed, fallback to USER_GRPC_ADDR: {}", ex.getMessage());
        }
        Optional<ConsulUserGrpcEndpointResolver.Endpoint> fallback = parseGrpcAddress(properties.getGrpcFallbackAddress());
        fallback.ifPresent(endpoint -> log.info("Using fallback userservice grpc endpoint: {}:{}", endpoint.host(), endpoint.port()));
        return fallback;
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
    public synchronized void shutdown() {
        if (endpointClient != null) {
            endpointClient.close();
            endpointClient = null;
        }
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
