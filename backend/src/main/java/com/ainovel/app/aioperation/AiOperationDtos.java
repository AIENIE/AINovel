package com.ainovel.app.aioperation;

import java.time.Instant;
import java.util.UUID;

public final class AiOperationDtos {
    private AiOperationDtos() {}

    public record Accepted(UUID operationId) {}
    public record Progress(
            UUID id, String operationType, String scopeType, UUID scopeId, AiOperationStatus status,
            String currentStep, int totalSteps, int completedSteps, int remainingSteps,
            long currentStepOutputTokens, boolean outputTokensEstimated, int attemptCount,
            String resultJson, String errorMessage, Instant createdAt, Instant updatedAt, Instant completedAt
    ) {}
}
