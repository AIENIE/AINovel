package com.ainovel.app.aioperation;

public interface AiOperationHandler {
    String type();
    Object execute(AiOperationExecution execution) throws Exception;

    /** Called only after an abandoned execution's persisted lease expires. Must be idempotent. */
    default void recoverExpiredLease(AiOperationExecution execution) throws Exception {}
}
