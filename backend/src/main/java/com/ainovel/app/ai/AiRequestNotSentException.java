package com.ainovel.app.ai;

import com.ainovel.app.common.ApiStatusException;
import org.springframework.http.HttpStatus;

/** Local admission or a documented gateway preflight rejection proves that no provider executed. */
public final class AiRequestNotSentException extends ApiStatusException {
    public AiRequestNotSentException(HttpStatus status, String message) { super(status, message); }
}
