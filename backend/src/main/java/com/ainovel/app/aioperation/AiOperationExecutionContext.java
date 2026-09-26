package com.ainovel.app.aioperation;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Worker-scoped identity. Business writes must enter apply/complete, never wrap remote calls. */
public final class AiOperationExecutionContext {
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
    private AiOperationExecutionContext() { }

    public static <T> T apply(Supplier<T> mutation) {
        Scope scope = CURRENT.get();
        return scope == null ? mutation.get() : scope.fence.apply(mutation, null);
    }

    /** Atomically applies the final business result and completes the operation against cancellation. */
    public static <T> T complete(Supplier<T> mutation) {
        Scope scope = CURRENT.get();
        return scope == null ? mutation.get() : scope.fence.apply(mutation, value -> value);
    }

    public static <T> T complete(Supplier<T> mutation, java.util.function.Function<T, Object> resultView) {
        Scope scope = CURRENT.get();
        return scope == null ? mutation.get() : scope.fence.apply(mutation, resultView);
    }

    public static String nextCallKey() {
        Scope scope = CURRENT.get();
        return scope == null ? UUID.randomUUID().toString()
                : "operation:" + scope.operationId + ":call:" + scope.calls.getAndIncrement();
    }

    public static String callKey(String requestHash) {
        Scope scope = CURRENT.get();
        if (scope == null) return UUID.randomUUID().toString();
        int occurrence = scope.requestCalls.merge(requestHash, 1, Integer::sum) - 1;
        return scope.operationId + ":" + requestHash + ":" + occurrence;
    }

    static AutoCloseable install(UUID operationId, Fence fence) {
        if (CURRENT.get() != null) throw new IllegalStateException("Nested AI operation context");
        CURRENT.set(new Scope(operationId, fence));
        return CURRENT::remove;
    }

    interface Fence { <T> T apply(Supplier<T> mutation, java.util.function.Function<T, Object> resultView); }
    private static final class Scope {
        private final UUID operationId;
        private final Fence fence;
        private final AtomicInteger calls = new AtomicInteger();
        private final java.util.Map<String,Integer> requestCalls = new java.util.HashMap<>();
        private Scope(UUID operationId, Fence fence) { this.operationId = operationId; this.fence = fence; }
    }
}
