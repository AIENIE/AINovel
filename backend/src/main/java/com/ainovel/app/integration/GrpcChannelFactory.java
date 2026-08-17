package com.ainovel.app.integration;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class GrpcChannelFactory {

    private final ExternalServiceProperties properties;

    public GrpcChannelFactory(ExternalServiceProperties properties) {
        this.properties = properties;
    }

    public ManagedChannel create(String host, int port) {
        NettyChannelBuilder builder = NettyChannelBuilder.forAddress(host, port);
        ExternalServiceProperties.Grpc grpc = properties.getGrpc();
        if (grpc.isTlsEnabled()) {
            String configuredTrust = grpc.getTrustCertCollection();
            if (configuredTrust == null || configuredTrust.isBlank()) {
                builder.useTransportSecurity();
            } else {
                Path trustPath = Path.of(configuredTrust).toAbsolutePath().normalize();
                if (!Files.isRegularFile(trustPath, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(trustPath)) {
                    throw new IllegalStateException("Configured gRPC trust certificate must be a regular non-link file");
                }
                try {
                    builder.sslContext(GrpcSslContexts.forClient().trustManager(trustPath.toFile()).build());
                } catch (IOException exception) {
                    throw new IllegalStateException("Unable to load the configured gRPC trust certificate", exception);
                }
            }
        } else if (grpc.isPlaintextEnabled()) {
            builder.usePlaintext();
        } else {
            throw new IllegalStateException("gRPC transport misconfigured: both TLS and plaintext are disabled");
        }
        return builder.build();
    }
}
