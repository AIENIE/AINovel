package com.ainovel.app.security.remote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserSessionValidatorInfrastructureTests {

    @Test
    void shouldResolveGrpcEndpointFromConsulAndUseCache() throws Exception {
        AtomicInteger requestCount = new AtomicInteger(0);
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/health/service/userservice-grpc", exchange -> {
            requestCount.incrementAndGet();
            String body = """
                    [
                      {
                        "Service": {"Address": "10.0.0.8", "Port": 13001},
                        "Node": {"Address": "10.0.0.7"}
                      }
                    ]
                    """;
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(payload);
            }
        });
        server.start();
        try {
            UserSessionValidationProperties props = new UserSessionValidationProperties();
            props.getConsul().setEnabled(true);
            props.getConsul().setHost("127.0.0.1");
            props.getConsul().setPort(server.getAddress().getPort());
            props.getConsul().setServiceName("userservice-grpc");
            props.getConsul().setCacheSeconds(30);

            ConsulUserGrpcEndpointResolver resolver = new ConsulUserGrpcEndpointResolver(props, new ObjectMapper());
            Optional<ConsulUserGrpcEndpointResolver.Endpoint> first = resolver.resolve();
            Optional<ConsulUserGrpcEndpointResolver.Endpoint> second = resolver.resolve();

            assertTrue(first.isPresent());
            assertTrue(second.isPresent());
            assertEquals("10.0.0.8", first.get().host());
            assertEquals(13001, first.get().port());
            assertEquals(1, requestCount.get(), "second resolve should hit cache");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldParseStaticGrpcAddress() {
        Optional<ConsulUserGrpcEndpointResolver.Endpoint> endpoint = UserSessionValidator.parseGrpcAddress("static://127.0.0.1:13001");
        assertTrue(endpoint.isPresent());
        assertEquals("127.0.0.1", endpoint.get().host());
        assertEquals(13001, endpoint.get().port());
    }

    @Test
    void shouldRejectInvalidGrpcAddress() {
        Optional<ConsulUserGrpcEndpointResolver.Endpoint> endpoint = UserSessionValidator.parseGrpcAddress("invalid-address");
        assertTrue(endpoint.isEmpty());
    }
}
