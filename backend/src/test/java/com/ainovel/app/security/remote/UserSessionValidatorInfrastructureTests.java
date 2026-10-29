package com.ainovel.app.security.remote;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ainovel.app.integration.GrpcChannelFactory;
import fireflychat.user.v1.UserAuthServiceGrpc;
import fireflychat.user.v1.ValidateSessionRequest;
import fireflychat.user.v1.ValidateSessionResponse;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserSessionValidatorInfrastructureTests {

    @ParameterizedTest
    @ValueSource(strings={"DEADLINE_EXCEEDED", "UNAVAILABLE", "INVALID"})
    void keepsRemoteFailureSemanticsAndRecoversWithoutCachingFailures(String outcome) throws Exception {
        var calls = new AtomicInteger();
        var deadlines = new CopyOnWriteArrayList<Long>();
        Server server = ServerBuilder.forPort(0).addService(new UserAuthServiceGrpc.UserAuthServiceImplBase() {
            @Override public void validateSession(ValidateSessionRequest request, StreamObserver<ValidateSessionResponse> response) {
                if (!request.getSessionId().equals("warmup")) {
                    deadlines.add(Context.current().getDeadline().timeRemaining(TimeUnit.MILLISECONDS));
                    if (calls.incrementAndGet() == 1) {
                        if (outcome.equals("DEADLINE_EXCEEDED")) return;
                        if (outcome.equals("UNAVAILABLE")) {
                            response.onError(Status.UNAVAILABLE.withDescription("endpoint=private.example token=remote-token-secret").asRuntimeException());
                            return;
                        }
                        response.onNext(ValidateSessionResponse.newBuilder().setValid(false).build());
                        response.onCompleted();
                        return;
                    }
                }
                response.onNext(ValidateSessionResponse.newBuilder().setValid(true).build());
                response.onCompleted();
            }
        }).build().start();
        String host = InetAddress.getLoopbackAddress().getHostAddress();
        int port = server.getPort();
        var resolver = mock(ConsulUserGrpcEndpointResolver.class);
        when(resolver.resolve()).thenReturn(Optional.of(new ConsulUserGrpcEndpointResolver.Endpoint(host, port)));
        var channelFactory = mock(GrpcChannelFactory.class);
        when(channelFactory.create(host, port)).thenReturn(ManagedChannelBuilder.forAddress(host, port).usePlaintext().build());
        var tokenProvider = mock(UserServiceJwtProvider.class);
        when(tokenProvider.currentToken()).thenReturn("caller-token-secret");
        var properties = new UserSessionValidationProperties();
        var validator = new UserSessionValidator(resolver, properties, channelFactory, tokenProvider);
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(UserSessionValidator.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            // Avoid including initial channel setup/JIT time in the minimum-deadline check.
            assertEquals(UserSessionValidator.ValidationResult.VALID, validator.validate(42L, "warmup"));
            properties.setTimeoutMs(outcome.equals("DEADLINE_EXCEEDED") ? 0 : 1300);
            long expectedTimeout = outcome.equals("DEADLINE_EXCEEDED") ? 500 : 1300;
            assertEquals(outcome.equals("INVALID") ? UserSessionValidator.ValidationResult.INVALID : UserSessionValidator.ValidationResult.UNAVAILABLE,
                    validator.validate(42L, "session-secret"));
            assertEquals(1, calls.get());
            assertEquals(UserSessionValidator.ValidationResult.VALID, validator.validate(42L, "session-secret"));
            assertEquals(UserSessionValidator.ValidationResult.VALID, validator.validate(42L, "session-secret"));
            assertEquals(2, calls.get(), "Only valid results enter the existing positive cache");
            assertTrue(deadlines.stream().allMatch(value -> value > 0 && value <= expectedTimeout));
            if (!outcome.equals("INVALID")) {
                var event = appender.list.stream().filter(item -> item.getFormattedMessage().startsWith("Userservice session validation failed stage="))
                        .findFirst().orElseThrow();
                assertTrue(event.getFormattedMessage().contains("stage=RPC grpcStatus=" + outcome + " timeoutMs=" + expectedTimeout));
                assertNotNull(event.getThrowableProxy());
                assertNull(event.getThrowableProxy().getMessage());
            }
            String messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.joining("\n"));
            for (String secret : List.of(host, "private.example", "remote-token-secret", "caller-token-secret", "session-secret"))
                assertFalse(messages.contains(secret));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            validator.shutdown();
            server.shutdownNow();
            server.awaitTermination();
        }
    }

    @Test
    void shouldAttachARecentlyIssuedCallerJwtToEveryRemoteValidation() throws Exception {
        Metadata.Key<String> tokenHeader = Metadata.Key.of(
                "authorization", Metadata.ASCII_STRING_MARSHALLER);
        Metadata.Key<String> legacyTokenHeader = Metadata.Key.of(
                "x-internal-token", Metadata.ASCII_STRING_MARSHALLER);
        List<String> observedTokens = new CopyOnWriteArrayList<>();
        List<Boolean> observedLegacyTokens = new CopyOnWriteArrayList<>();
        Server server = ServerBuilder.forPort(0)
                .addService(ServerInterceptors.intercept(
                        new UserAuthServiceGrpc.UserAuthServiceImplBase() {
                            @Override
                            public void validateSession(ValidateSessionRequest request,
                                                        StreamObserver<ValidateSessionResponse> responseObserver) {
                                responseObserver.onNext(ValidateSessionResponse.newBuilder().setValid(true).build());
                                responseObserver.onCompleted();
                            }
                        },
                        new ServerInterceptor() {
                            @Override
                            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                                    ServerCall<ReqT, RespT> call,
                                    Metadata headers,
                                    ServerCallHandler<ReqT, RespT> next) {
                                observedTokens.add(headers.get(tokenHeader));
                                observedLegacyTokens.add(headers.containsKey(legacyTokenHeader));
                                return next.startCall(call, headers);
                            }
                        }
                ))
                .build()
                .start();
        UserSessionValidator validator = null;
        try {
            String host = InetAddress.getLoopbackAddress().getHostAddress();
            int port = server.getPort();
            ConsulUserGrpcEndpointResolver resolver = mock(ConsulUserGrpcEndpointResolver.class);
            when(resolver.resolve()).thenReturn(Optional.of(
                    new ConsulUserGrpcEndpointResolver.Endpoint(host, port)));
            UserSessionValidationProperties validationProperties = new UserSessionValidationProperties();
            GrpcChannelFactory channelFactory = mock(GrpcChannelFactory.class);
            when(channelFactory.create(host, port)).thenReturn(
                    ManagedChannelBuilder.forAddress(host, port).usePlaintext().build());
            UserServiceJwtProvider tokenProvider = mock(UserServiceJwtProvider.class);
            when(tokenProvider.currentToken()).thenReturn("caller.jwt.one", "caller.jwt.two");
            validator = new UserSessionValidator(
                    resolver, validationProperties, channelFactory, tokenProvider);

            assertEquals(UserSessionValidator.ValidationResult.VALID, validator.validate(41L, "session-one"));
            assertEquals(UserSessionValidator.ValidationResult.VALID, validator.validate(42L, "session-two"));
            assertEquals(List.of("Bearer caller.jwt.one", "Bearer caller.jwt.two"), observedTokens);
            assertEquals(List.of(false, false), observedLegacyTokens);
        } finally {
            if (validator != null) {
                validator.shutdown();
            }
            server.shutdownNow();
            server.awaitTermination();
        }
    }

    @Test
    void shouldResolveGrpcEndpointFromConfiguredAddress() {
        UserSessionValidationProperties props = new UserSessionValidationProperties();
        props.setGrpcAddress("static://userservice.example:10001");

        ConsulUserGrpcEndpointResolver resolver = new ConsulUserGrpcEndpointResolver(props);
        Optional<ConsulUserGrpcEndpointResolver.Endpoint> endpoint = resolver.resolve();

        assertTrue(endpoint.isPresent());
        assertEquals("userservice.example", endpoint.get().host());
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
        Optional<ConsulUserGrpcEndpointResolver.Endpoint> endpoint = UserSessionValidator.parseGrpcAddress("dns:///userservice.example:10001");
        assertTrue(endpoint.isPresent());
        assertEquals("userservice.example", endpoint.get().host());
        assertEquals(10001, endpoint.get().port());
    }

    @Test
    void shouldRejectInvalidGrpcAddress() {
        Optional<ConsulUserGrpcEndpointResolver.Endpoint> endpoint = UserSessionValidator.parseGrpcAddress("invalid-address");
        assertTrue(endpoint.isEmpty());
    }

    @Test
    void shouldLogOnlyFixedEndpointFailureAndSafeThrowable() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            String host = server.getInetAddress().getHostAddress();
            int port = server.getLocalPort();
            ConsulUserGrpcEndpointResolver resolver = mock(ConsulUserGrpcEndpointResolver.class);
            when(resolver.resolve()).thenReturn(Optional.of(
                    new ConsulUserGrpcEndpointResolver.Endpoint(host, port)
            ));
            UserSessionValidationProperties properties = new UserSessionValidationProperties();
            GrpcChannelFactory channelFactory = mock(GrpcChannelFactory.class);
            when(channelFactory.create(host, port)).thenThrow(new IllegalStateException(
                    "endpoint=" + host + ":" + port + " token=remote-token-secret path=/private/session"
            ));
            UserSessionValidator validator = new UserSessionValidator(
                    resolver, properties, channelFactory, mock(UserServiceJwtProvider.class)
            );
            var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(UserSessionValidator.class);
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.start();
            logger.addAppender(appender);
            UserSessionValidator.ValidationResult valid;
            try {
                valid = validator.validate(42L, "session-secret");
            } finally {
                logger.detachAppender(appender);
                appender.stop();
            }

            assertEquals(UserSessionValidator.ValidationResult.UNAVAILABLE, valid);
            ILoggingEvent terminalEvent = appender.list.stream()
                    .filter(event -> event.getFormattedMessage().startsWith("Userservice session validation failed stage="))
                    .findFirst()
                    .orElseThrow();
            String messages = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.joining("\n"));
            assertTrue(terminalEvent.getFormattedMessage().contains("stage=CLIENT_CREATE"));
            assertTrue(terminalEvent.getFormattedMessage().contains("errorType=IllegalStateException"));
            assertTrue(terminalEvent.getFormattedMessage().contains("grpcStatus=UNKNOWN timeoutMs=5000"));
            assertFalse(messages.contains(host));
            assertFalse(messages.contains(Integer.toString(port)));
            assertFalse(messages.contains("internal-token-secret"));
            assertFalse(messages.contains("remote-token-secret"));
            assertFalse(messages.contains("/private/session"));
            assertFalse(messages.contains("session-secret"));
            assertNotNull(terminalEvent.getThrowableProxy());
            assertNull(terminalEvent.getThrowableProxy().getMessage());
        }
    }
}
