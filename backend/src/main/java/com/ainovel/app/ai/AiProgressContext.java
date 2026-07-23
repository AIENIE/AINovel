package com.ainovel.app.ai;

import com.ainovel.app.integration.AiGatewayGrpcClient;

import java.util.function.Supplier;

/** Propagates progress only across the worker thread executing one AI operation. */
public final class AiProgressContext {
    private static final ThreadLocal<AiGatewayGrpcClient.StreamProgressListener> CURRENT = new ThreadLocal<>();

    private AiProgressContext() {}

    public static AiGatewayGrpcClient.StreamProgressListener current() {
        return CURRENT.get();
    }

    public static <T> T withListener(AiGatewayGrpcClient.StreamProgressListener listener, Supplier<T> action) {
        AiGatewayGrpcClient.StreamProgressListener previous = CURRENT.get();
        CURRENT.set(listener);
        try {
            return action.get();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
