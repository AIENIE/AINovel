package com.ainovel.app.integration;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

import java.util.Objects;

/** Resolves the current short-lived JWT when each real gRPC call starts. */
final class PayServiceJwtClientInterceptor implements ClientInterceptor {
    static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    static final Metadata.Key<String> LEGACY_INTERNAL_TOKEN =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);
    private final PayServiceJwtProvider tokenProvider;

    PayServiceJwtClientInterceptor(PayServiceJwtProvider tokenProvider) {
        this.tokenProvider = Objects.requireNonNull(tokenProvider);
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                headers.discardAll(AUTHORIZATION);
                headers.discardAll(LEGACY_INTERNAL_TOKEN);
                headers.put(AUTHORIZATION, "Bearer " + tokenProvider.currentToken());
                super.start(responseListener, headers);
            }
        };
    }
}
