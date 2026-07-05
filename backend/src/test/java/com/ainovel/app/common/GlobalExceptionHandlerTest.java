package com.ainovel.app.common;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void businessExceptionShouldReturnBadRequestWithOriginalMessage() {
        ResponseEntity<ApiError> response = handler.handleBusiness(new BusinessException("用户不存在"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("用户不存在", response.getBody().message());
    }

    @Test
    void grpcUnavailableShouldReturnServiceUnavailable() {
        ResponseEntity<ApiError> response = handler.handleGrpc(
                new StatusRuntimeException(Status.UNAVAILABLE.withDescription("billing down"))
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("UNAVAILABLE: billing down", response.getBody().message());
    }

    @Test
    void grpcInternalShouldReturnBadGateway() {
        ResponseEntity<ApiError> response = handler.handleGrpc(
                new StatusRuntimeException(Status.INTERNAL.withDescription("upstream failure"))
        );

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals("INTERNAL: upstream failure", response.getBody().message());
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
        ResponseEntity<ApiError> response = handler.handleRuntime(new RuntimeException("boom"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Internal server error", response.getBody().message());
    }
}
