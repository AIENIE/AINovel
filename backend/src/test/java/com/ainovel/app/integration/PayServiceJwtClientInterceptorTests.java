package com.ainovel.app.integration;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PayServiceJwtClientInterceptorTests {

    @Test
    void shouldResolveFreshBearerTokenForEachLogicalRetryAttemptAndRemoveLegacyHeader() {
        PayServiceJwtProvider provider = mock(PayServiceJwtProvider.class);
        when(provider.currentToken()).thenReturn("pay.jwt.one", "pay.jwt.two");
        PayServiceJwtClientInterceptor interceptor = new PayServiceJwtClientInterceptor(provider);
        CapturingChannel channel = new CapturingChannel();
        MethodDescriptor<byte[], byte[]> method = method();

        for (int index = 0; index < 2; index++) {
            Metadata headers = new Metadata();
            headers.put(PayServiceJwtClientInterceptor.AUTHORIZATION, "Bearer stale");
            headers.put(PayServiceJwtClientInterceptor.LEGACY_INTERNAL_TOKEN, "legacy-static-token");
            interceptor.interceptCall(method, CallOptions.DEFAULT, channel)
                    .start(new ClientCall.Listener<>() { }, headers);
        }

        assertEquals(List.of("Bearer pay.jwt.one", "Bearer pay.jwt.two"),
                channel.observedHeaders.stream()
                        .map(headers -> headers.get(PayServiceJwtClientInterceptor.AUTHORIZATION))
                        .toList());
        assertFalse(channel.observedHeaders.stream()
                .anyMatch(headers -> headers.containsKey(PayServiceJwtClientInterceptor.LEGACY_INTERNAL_TOKEN)));
    }

    private MethodDescriptor<byte[], byte[]> method() {
        MethodDescriptor.Marshaller<byte[]> marshaller = new MethodDescriptor.Marshaller<>() {
            @Override
            public InputStream stream(byte[] value) {
                return new ByteArrayInputStream(value);
            }

            @Override
            public byte[] parse(InputStream stream) {
                return new byte[0];
            }
        };
        return MethodDescriptor.<byte[], byte[]>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("test.Pay/Call")
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
                public void start(Listener<ResponseT> responseListener, Metadata headers) {
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
