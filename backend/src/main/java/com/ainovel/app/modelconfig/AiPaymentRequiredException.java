package com.ainovel.app.modelconfig;

/** The inference result is durable; only settlement may be retried. */
public final class AiPaymentRequiredException extends com.ainovel.app.common.ApiStatusException {
    public AiPaymentRequiredException(){super(org.springframework.http.HttpStatus.PAYMENT_REQUIRED,"AI_PAYMENT_REQUIRED");}
}
