package com.ainovel.app.security.remote;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ConsulUserGrpcEndpointResolver {

    private final UserSessionValidationProperties properties;

    public ConsulUserGrpcEndpointResolver(UserSessionValidationProperties properties) {
        this.properties = properties;
    }

    public Optional<Endpoint> resolve() {
        return UserSessionValidator.parseGrpcAddress(properties.getGrpcAddress());
    }

    public record Endpoint(String host, int port) {}
}
