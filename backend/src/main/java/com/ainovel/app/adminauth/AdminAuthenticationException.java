package com.ainovel.app.adminauth;

import org.springframework.http.HttpStatus;

public class AdminAuthenticationException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final int retryAfterSeconds;

    public AdminAuthenticationException(HttpStatus status, String code, String message) {
        this(status, code, message, 0);
    }

    public AdminAuthenticationException(HttpStatus status, String code, String message, int retryAfterSeconds) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public int retryAfterSeconds() { return retryAfterSeconds; }
}
