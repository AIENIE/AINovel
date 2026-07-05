package com.ainovel.app.integration;

import io.grpc.ManagedChannel;

final class GrpcEndpointManager<T extends GrpcEndpointManager.ManagedClient> {
    private final ExternalServiceProperties.ServiceTarget target;
    private final String targetName;
    private final GrpcChannelFactory channelFactory;

    private volatile T client;

    GrpcEndpointManager(ExternalServiceProperties.ServiceTarget target,
                        String targetName,
                        GrpcChannelFactory channelFactory) {
        this.target = target;
        this.targetName = targetName;
        this.channelFactory = channelFactory;
    }

    synchronized T getOrCreate(ClientFactory<T> clientFactory) {
        ConsulServiceResolver.Endpoint endpoint = ConsulServiceResolver.parseAddress(target.getAddress())
                .orElseThrow(() -> new IllegalStateException("No endpoint for " + targetName));
        T existing = client;
        if (existing != null && existing.sameEndpoint(endpoint.host(), endpoint.port())) {
            return existing;
        }

        ManagedChannel channel = channelFactory.create(endpoint.host(), endpoint.port());
        try {
            T next = clientFactory.create(endpoint, channel);
            if (existing != null) {
                existing.close();
            }
            client = next;
            return next;
        } catch (RuntimeException | Error ex) {
            channel.shutdownNow();
            throw ex;
        }
    }

    synchronized void shutdown() {
        if (client != null) {
            client.close();
            client = null;
        }
    }

    interface ManagedClient {
        String host();

        int port();

        void close();

        default boolean sameEndpoint(String targetHost, int targetPort) {
            return host().equals(targetHost) && port() == targetPort;
        }
    }

    @FunctionalInterface
    interface ClientFactory<T extends ManagedClient> {
        T create(ConsulServiceResolver.Endpoint endpoint, ManagedChannel channel);
    }
}
