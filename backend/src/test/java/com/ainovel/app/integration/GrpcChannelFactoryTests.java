package com.ainovel.app.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GrpcChannelFactoryTests {

    @Test
    void shouldCreateTlsChannelByDefault() {
        ExternalServiceProperties properties = new ExternalServiceProperties();
        GrpcChannelFactory factory = new GrpcChannelFactory(properties);
        var channel = factory.create("127.0.0.1", 65535);
        assertNotNull(channel);
        channel.shutdownNow();
    }

    @Test
    void shouldFailWhenNoTransportEnabled() {
        ExternalServiceProperties properties = new ExternalServiceProperties();
        properties.getGrpc().setTlsEnabled(false);
        properties.getGrpc().setPlaintextEnabled(false);
        GrpcChannelFactory factory = new GrpcChannelFactory(properties);
        assertThrows(IllegalStateException.class, () -> factory.create("127.0.0.1", 65535));
    }
}
