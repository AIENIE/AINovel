package com.ainovel.app.security.remote;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserSessionValidatorInfrastructureTests {

    @Test
    void shouldResolveGrpcEndpointFromConfiguredAddress() {
        UserSessionValidationProperties props = new UserSessionValidationProperties();
        props.setGrpcAddress("static://userservice.localhut.com:10001");

        ConsulUserGrpcEndpointResolver resolver = new ConsulUserGrpcEndpointResolver(props);
        Optional<ConsulUserGrpcEndpointResolver.Endpoint> endpoint = resolver.resolve();

        assertTrue(endpoint.isPresent());
        assertEquals("userservice.localhut.com", endpoint.get().host());
        assertEquals(10001, endpoint.get().port());
    }

    @Test
    void shouldParseStaticGrpcAddress() {
        Optional<ConsulUserGrpcEndpointResolver.Endpoint> endpoint = UserSessionValidator.parseGrpcAddress("static://127.0.0.1:13001");
        assertTrue(endpoint.isPresent());
        assertEquals("127.0.0.1", endpoint.get().host());
        assertEquals(13001, endpoint.get().port());
    }

    @Test
    void shouldParseDnsGrpcAddress() {
        Optional<ConsulUserGrpcEndpointResolver.Endpoint> endpoint = UserSessionValidator.parseGrpcAddress("dns:///userservice.localhut.com:10001");
        assertTrue(endpoint.isPresent());
        assertEquals("userservice.localhut.com", endpoint.get().host());
        assertEquals(10001, endpoint.get().port());
    }

    @Test
    void shouldRejectInvalidGrpcAddress() {
        Optional<ConsulUserGrpcEndpointResolver.Endpoint> endpoint = UserSessionValidator.parseGrpcAddress("invalid-address");
        assertTrue(endpoint.isEmpty());
    }
}
