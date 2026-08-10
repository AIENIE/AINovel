package com.ainovel.app.common;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void businessExceptionShouldReturnBadRequestWithOriginalMessage() {
        ResponseEntity<ApiError> response = handler.handleBusiness(new BusinessException("用户不存在"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("用户不存在", response.getBody().message());
    }

    @Test
    void grpcUnavailableShouldReturnServiceUnavailable() {
        GrpcResult result = invokeGrpc(Status.UNAVAILABLE.withDescription("token=upstream-secret"));
        ResponseEntity<ApiError> response = result.response();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("UPSTREAM_SERVICE_UNAVAILABLE", response.getBody().message());
        assertGrpcLogIsSanitized(result.event(), "UNAVAILABLE");
    }

    @Test
    void grpcInternalShouldReturnBadGateway() {
        GrpcResult result = invokeGrpc(Status.INTERNAL.withDescription("password=upstream-secret"));
        ResponseEntity<ApiError> response = result.response();

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals("UPSTREAM_SERVICE_ERROR", response.getBody().message());
        assertGrpcLogIsSanitized(result.event(), "INTERNAL");
    }

    @Test
    void dataIntegrityViolationShouldReturnConflict() {
        ResponseEntity<ApiError> response = handler.handleDataIntegrity(
                new DataIntegrityViolationException("duplicate key")
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("数据冲突", response.getBody().message());
    }

    @Test
    void runtimeExceptionShouldReturnInternalServerError() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        ResponseEntity<ApiError> response;
        try {
            response = handler.handleRuntime(new RuntimeException("remote-token-secret"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Internal server error", response.getBody().message());
        assertEquals(1, appender.list.size());
        ILoggingEvent event = appender.list.get(0);
        assertTrue(event.getFormattedMessage().contains("errorType=RuntimeException"));
        assertFalse(event.getFormattedMessage().contains("remote-token-secret"));
        assertNotNull(event.getThrowableProxy());
        assertNull(event.getThrowableProxy().getMessage());
    }

    private GrpcResult invokeGrpc(Status status) {
        MDC.put(RequestIdFilter.MDC_KEY, "grpc-request-123");
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        ResponseEntity<ApiError> response;
        try {
            response = handler.handleGrpc(new StatusRuntimeException(status));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
        assertEquals(1, appender.list.size());
        return new GrpcResult(response, appender.list.getFirst());
    }

    private void assertGrpcLogIsSanitized(ILoggingEvent event, String code) {
        assertEquals("grpc-request-123", event.getMDCPropertyMap().get(RequestIdFilter.MDC_KEY));
        assertTrue(event.getFormattedMessage().contains("code=" + code));
        assertTrue(event.getFormattedMessage().contains("errorType=StatusRuntimeException"));
        assertFalse(event.getFormattedMessage().contains("upstream-secret"));
        assertNotNull(event.getThrowableProxy());
        assertNull(event.getThrowableProxy().getMessage());
    }

    private record GrpcResult(ResponseEntity<ApiError> response, ILoggingEvent event) {
    }
}
