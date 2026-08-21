package com.ainovel.app.integration;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiHmacAuthInterceptorTests {
    private static final Metadata.Key<String> CALLER =
            Metadata.Key.of("x-aienie-caller", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> NONCE =
            Metadata.Key.of("x-aienie-nonce", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> SIGNATURE =
            Metadata.Key.of("x-aienie-signature", Metadata.ASCII_STRING_MARSHALLER);

    @Test
    void shouldGenerateFreshNonceForEachLogicalRetryAttempt() {
        ExternalServiceProperties properties = new ExternalServiceProperties();
        properties.getSecurity().getAi().setHmacCaller("ai-novel");
        properties.getSecurity().getAi().setHmacSecret(
                "unit-test-ai-hmac-secret-with-at-least-32-bytes");
        var interceptor = new AiGatewayGrpcClient.AiHmacAuthInterceptor(
                properties,
                Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC));
        CapturingChannel channel = new CapturingChannel();

        for (int attempt = 0; attempt < 2; attempt++) {
            ClientCall<String, String> call = interceptor.interceptCall(
                    method(), CallOptions.DEFAULT, channel);
            call.start(new ClientCall.Listener<>() { }, new Metadata());
            call.request(1);
            call.sendMessage("same-logical-request-body");
            call.halfClose();
        }

        assertEquals(2, channel.observedHeaders.size());
        assertEquals(List.of("ai-novel", "ai-novel"), channel.observedHeaders.stream()
                .map(headers -> headers.get(CALLER))
                .toList());
        String firstNonce = channel.observedHeaders.get(0).get(NONCE);
        String secondNonce = channel.observedHeaders.get(1).get(NONCE);
        assertTrue(firstNonce != null && firstNonce.matches("[0-9a-f]{32}"));
        assertTrue(secondNonce != null && secondNonce.matches("[0-9a-f]{32}"));
        assertNotEquals(firstNonce, secondNonce);
        assertNotEquals(channel.observedHeaders.get(0).get(SIGNATURE),
                channel.observedHeaders.get(1).get(SIGNATURE));
    }

    private MethodDescriptor<String, String> method() {
        MethodDescriptor.Marshaller<String> marshaller = new MethodDescriptor.Marshaller<>() {
            @Override
            public InputStream stream(String value) {
                return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public String parse(InputStream stream) {
                return "";
            }
        };
        return MethodDescriptor.<String, String>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("test.Ai/Call")
                .setRequestMarshaller(marshaller)
                .setResponseMarshaller(marshaller)
                .build();
    }

    private static final class CapturingChannel extends Channel {
        private final List<Metadata> observedHeaders = new ArrayList<>();

        @Override
        public <RequestT, ResponseT> ClientCall<RequestT, ResponseT> newCall(
                MethodDescriptor<RequestT, ResponseT> methodDescriptor, CallOptions callOptions) {
            return new ClientCall<>() {
                @Override
                public void start(Listener<ResponseT> listener, Metadata headers) {
                    observedHeaders.add(headers);
                }

                @Override
                public void request(int numMessages) { }

                @Override
                public void cancel(String message, Throwable cause) { }

                @Override
                public void halfClose() { }

                @Override
                public void sendMessage(RequestT message) { }
            };
        }

        @Override
        public String authority() {
            return "test";
        }
    }
}
