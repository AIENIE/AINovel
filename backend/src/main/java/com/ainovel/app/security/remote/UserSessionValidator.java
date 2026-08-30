package com.ainovel.app.security.remote;

import com.ainovel.app.common.SafeLogThrowable;
import fireflychat.user.v1.UserAuthServiceGrpc;
import fireflychat.user.v1.ValidateSessionRequest;
import com.ainovel.app.integration.ExternalServiceProperties;
import com.ainovel.app.integration.GrpcChannelFactory;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
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
    private final ExternalServiceProperties externalServiceProperties;
    private final GrpcChannelFactory channelFactory;
    private final UserServiceJwtProvider userServiceJwtProvider;

    private final ConcurrentMap<String, EndpointClient> endpointClients = new ConcurrentHashMap<>();
    private final java.util.Map<SessionKey, Instant> positiveCache = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<>(128, 0.75f, true) {
                @Override protected boolean removeEldestEntry(java.util.Map.Entry<SessionKey, Instant> eldest) {
                    return size() > 10_000;
                }
            });

    public UserSessionValidator(
            ConsulUserGrpcEndpointResolver consulResolver,
            UserSessionValidationProperties properties,
            ExternalServiceProperties externalServiceProperties,
            GrpcChannelFactory channelFactory,
            UserServiceJwtProvider userServiceJwtProvider
    ) {
        this.consulResolver = consulResolver;
        this.properties = properties;
        this.externalServiceProperties = externalServiceProperties;
        this.channelFactory = channelFactory;
        this.userServiceJwtProvider = userServiceJwtProvider;
    }

    public boolean validate(long userId, String sessionId) {
        if (userId <= 0 || sessionId == null || sessionId.isBlank()) {
            return false;
        }
        SessionKey cacheKey = new SessionKey(userId, sessionId);
        Instant cachedUntil = positiveCache.get(cacheKey);
        if (cachedUntil != null && cachedUntil.isAfter(Instant.now())) return true;
        if (cachedUntil != null) positiveCache.remove(cacheKey);
        String serviceToken;
        try {
            serviceToken = userServiceJwtProvider.currentToken();
        } catch (RuntimeException ex) {
            log.warn("Userservice caller JWT issuance failed errorType={}", ex.getClass().getSimpleName());
            return false;
        }

        Exception terminalFailure = null;
        String failureStage = null;
        boolean rpcCompleted = false;
        for (ConsulUserGrpcEndpointResolver.Endpoint endpoint : resolveCandidates()) {
            EndpointClient client;
            try {
                client = getOrCreateClient(endpoint);
            } catch (Exception e) {
                terminalFailure = e;
                failureStage = "CLIENT_CREATE";
                continue;
            }

            try {
                Metadata metadata = new Metadata();
                metadata.put(
                        Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER),
                        "Bearer " + serviceToken
                );
                boolean valid = client.stub()
                        .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
                        .withDeadlineAfter(Math.max(500L, externalServiceProperties.getSessionTimeoutMs()), TimeUnit.MILLISECONDS)
                        .validateSession(ValidateSessionRequest.newBuilder()
                                .setUserId(userId)
                                .setSessionId(sessionId)
                        .build())
                        .getValid();
                rpcCompleted = true;
                if (valid) {
                    positiveCache.put(cacheKey, Instant.now().plusSeconds(10));
                    return true;
                }
            } catch (Exception e) {
                terminalFailure = e;
                failureStage = "RPC";
            }
        }
        if (!rpcCompleted && terminalFailure != null) {
            log.warn("Userservice session validation failed stage={} errorType={}",
                    failureStage, terminalFailure.getClass().getSimpleName(), SafeLogThrowable.stackOnly(terminalFailure));
        }
        return false;
    }

    public boolean dependencyAvailable() {
        return !resolveCandidates().isEmpty();
    }

    private EndpointClient getOrCreateClient(ConsulUserGrpcEndpointResolver.Endpoint endpoint) {
        String key = endpoint.host() + ":" + endpoint.port();
        return endpointClients.computeIfAbsent(key, ignored -> {
            ManagedChannel channel = channelFactory.create(endpoint.host(), endpoint.port());
            return new EndpointClient(
                    endpoint.host(),
                    endpoint.port(),
                    channel,
                    UserAuthServiceGrpc.newBlockingStub(channel)
            );
        });
    }

    private List<ConsulUserGrpcEndpointResolver.Endpoint> resolveCandidates() {
        LinkedHashMap<String, ConsulUserGrpcEndpointResolver.Endpoint> ordered = new LinkedHashMap<>();

        consulResolver.resolve().ifPresent(endpoint -> {
            String key = endpoint.host() + ":" + endpoint.port();
            log.info("Userservice session validation endpoint candidate resolved");
            ordered.put(key, endpoint);
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

    private record SessionKey(long userId, String sessionId) { }
}
