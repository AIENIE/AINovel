package com.ainovel.app.ai;

import java.util.Locale;

/**
 * Identifies a billable AI operation that belongs to a durable business record.
 * The normal chat path intentionally keeps using an ephemeral reference.
 */
public record AiUsageContext(String referenceType, String referenceId, String operation) {
    public AiUsageContext {
        if (referenceType == null || referenceType.isBlank()) {
            throw new IllegalArgumentException("referenceType 不能为空");
        }
        if (referenceId == null || referenceId.isBlank()) {
            throw new IllegalArgumentException("referenceId 不能为空");
        }
        operation = operation == null || operation.isBlank() ? "chat" : operation.trim();
    }

    public AiUsageContext forOperation(String nextOperation) {
        String normalized = nextOperation == null || nextOperation.isBlank() ? "chat" : nextOperation.trim();
        return new AiUsageContext(referenceType, referenceId, operation + ":" + normalized);
    }

    public String idempotencyKey() {
        return "ai:" + referenceType.toLowerCase(Locale.ROOT) + ":" + referenceId + ":" + operation;
    }
}
