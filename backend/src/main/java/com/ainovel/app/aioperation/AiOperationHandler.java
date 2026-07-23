package com.ainovel.app.aioperation;

public interface AiOperationHandler {
    String type();
    Object execute(AiOperationExecution execution) throws Exception;
}
