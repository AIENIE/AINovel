package com.ainovel.app.aioperation;

import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.UUID;

public record AiOperationExecution(
        UUID operationId,
        User user,
        String payloadJson,
        ObjectMapper objectMapper,
        AiOperationProgressUpdater progress
) {}
