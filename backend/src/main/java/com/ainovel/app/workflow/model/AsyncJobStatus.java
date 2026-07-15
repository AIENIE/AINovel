package com.ainovel.app.workflow.model;

public enum AsyncJobStatus {
    QUEUED,
    RUNNING,
    CALLING_AI,
    SUCCEEDED,
    FAILED,
    RECOVERY_REQUIRED
}
