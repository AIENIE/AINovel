package com.ainovel.app.ai;

import com.ainovel.app.common.ApiStatusException;
import org.springframework.http.HttpStatus;

/** Raised only before the gateway transport is entered; no provider execution is possible. */
public final class AiRequestNotSentException extends ApiStatusException {
    public AiRequestNotSentException(HttpStatus status, String message) { super(status, message); }
}
