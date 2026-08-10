package com.ainovel.app.security.remote;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ainovel.app.integration.ExternalServiceProperties;
import com.ainovel.app.integration.GrpcChannelFactory;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
            ExternalServiceProperties externalProperties = new ExternalServiceProperties();
            externalProperties.getSecurity().getUser().setInternalGrpcToken("internal-token-secret");
            GrpcChannelFactory channelFactory = mock(GrpcChannelFactory.class);
            when(channelFactory.create(host, port)).thenThrow(new IllegalStateException(
                    "endpoint=" + host + ":" + port + " token=remote-token-secret path=/private/session"
            ));
            UserSessionValidator validator = new UserSessionValidator(
                    resolver, properties, externalProperties, channelFactory
            );
            var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(UserSessionValidator.class);
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.start();
            logger.addAppender(appender);
            boolean valid;
            try {
                valid = validator.validate(42L, "session-secret");
            } finally {
                logger.detachAppender(appender);
                appender.stop();
            }

            assertFalse(valid);
            ILoggingEvent terminalEvent = appender.list.stream()
                    .filter(event -> event.getFormattedMessage().startsWith("Userservice session validation failed stage="))
                    .findFirst()
                    .orElseThrow();
            String messages = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.joining("\n"));
            assertTrue(terminalEvent.getFormattedMessage().contains("stage=CLIENT_CREATE"));
            assertTrue(terminalEvent.getFormattedMessage().contains("errorType=IllegalStateException"));
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
