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
    private final ExternalServiceProperties externalServiceProperties;
    private final GrpcChannelFactory channelFactory;

    private final ConcurrentMap<String, EndpointClient> endpointClients = new ConcurrentHashMap<>();

    public UserSessionValidator(
            ConsulUserGrpcEndpointResolver consulResolver,
            UserSessionValidationProperties properties,
            ExternalServiceProperties externalServiceProperties,
            GrpcChannelFactory channelFactory
    ) {
        this.consulResolver = consulResolver;
        this.properties = properties;
        this.externalServiceProperties = externalServiceProperties;
        this.channelFactory = channelFactory;
    }

    public boolean validate(long userId, String sessionId) {
        if (userId <= 0 || sessionId == null || sessionId.isBlank()) {
            return false;
        }
        String internalToken = externalServiceProperties.getSecurity().getUser().getInternalGrpcToken();
        if (internalToken == null || internalToken.isBlank()) {
            log.warn("Userservice session validation token is empty");
            return false;
        }

        int connectTimeout = (int) Math.max(300L, properties.getTimeoutMs());
        Exception terminalFailure = null;
        String failureStage = null;
        boolean endpointUnreachable = false;
        boolean rpcCompleted = false;
        for (ConsulUserGrpcEndpointResolver.Endpoint endpoint : resolveCandidates()) {
            if (!isTcpReachable(endpoint.host(), endpoint.port(), connectTimeout)) {
                endpointUnreachable = true;
                continue;
            }

            EndpointClient client;
            try {
                client = getOrCreateClient(endpoint);
            } catch (Exception e) {
                terminalFailure = e;
                failureStage = "CLIENT_CREATE";
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
                rpcCompleted = true;
                if (valid) {
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
        } else if (!rpcCompleted && endpointUnreachable) {
            log.warn("Userservice session validation failed reason=NO_REACHABLE_ENDPOINT");
        }
        return false;
    }

    private EndpointClient getOrCreateClient(ConsulUserGrpcEndpointResolver.Endpoint endpoint) {
        String key = endpoint.host() + ":" + endpoint.port();
        return endpointClients.computeIfAbsent(key, ignored -> {
            ManagedChannel channel = channelFactory.create(endpoint.host(), endpoint.port());
            Metadata metadata = new Metadata();
            metadata.put(
                    Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER),
                    externalServiceProperties.getSecurity().getUser().getInternalGrpcToken().trim()
            );
            return new EndpointClient(
                    endpoint.host(),
                    endpoint.port(),
                    channel,
                    UserAuthServiceGrpc.newBlockingStub(channel)
                            .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
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

    private boolean isTcpReachable(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
