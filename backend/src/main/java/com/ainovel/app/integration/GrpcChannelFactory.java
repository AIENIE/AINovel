package com.ainovel.app.integration;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.stereotype.Component;

@Component
public class GrpcChannelFactory {

    private final ExternalServiceProperties properties;

    public GrpcChannelFactory(ExternalServiceProperties properties) {
        this.properties = properties;
    }

    public ManagedChannel create(String host, int port) {
        ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forAddress(host, port);
        ExternalServiceProperties.Grpc grpc = properties.getGrpc();
        if (grpc.isTlsEnabled()) {
            builder.useTransportSecurity();
        } else if (grpc.isPlaintextEnabled()) {
            builder.usePlaintext();
        } else {
            throw new IllegalStateException("gRPC transport misconfigured: both TLS and plaintext are disabled");
        }
        return builder.build();
    }
}
