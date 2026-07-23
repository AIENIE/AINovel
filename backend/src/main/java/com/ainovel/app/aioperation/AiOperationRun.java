package com.ainovel.app.aioperation;

import com.ainovel.app.user.User;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_operation_runs")
public class AiOperationRun {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "operation_type", nullable = false, length = 64) private String operationType;
    @Column(name = "scope_type", length = 64) private String scopeType;
    @Column(name = "scope_id") private UUID scopeId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private AiOperationStatus status;
    @Column(name = "current_step", length = 160) private String currentStep;
    @Column(name = "total_steps", nullable = false) private int totalSteps;
    @Column(name = "completed_steps", nullable = false) private int completedSteps;
    @Column(name = "output_tokens", nullable = false) private long outputTokens;
    @Column(name = "output_tokens_estimated", nullable = false) private boolean outputTokensEstimated;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "stream_started", nullable = false) private boolean streamStarted;
    @Column(name = "request_id", length = 100) private String requestId;
    @Column(name = "model_key", length = 100) private String modelKey;
    @Lob @Column(name = "payload_json") private String payloadJson;
    @Lob @Column(name = "result_json") private String resultJson;
    @Column(name = "error_message", length = 500) private String errorMessage;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;

    @PrePersist void onCreate() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }
    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }
    public UUID getScopeId() { return scopeId; }
    public void setScopeId(UUID scopeId) { this.scopeId = scopeId; }
    public AiOperationStatus getStatus() { return status; }
    public void setStatus(AiOperationStatus status) { this.status = status; }
    public String getCurrentStep() { return currentStep; }
    public void setCurrentStep(String currentStep) { this.currentStep = currentStep; }
    public int getTotalSteps() { return totalSteps; }
    public void setTotalSteps(int totalSteps) { this.totalSteps = totalSteps; }
    public int getCompletedSteps() { return completedSteps; }
    public void setCompletedSteps(int completedSteps) { this.completedSteps = completedSteps; }
    public long getOutputTokens() { return outputTokens; }
    public void setOutputTokens(long outputTokens) { this.outputTokens = outputTokens; }
    public boolean isOutputTokensEstimated() { return outputTokensEstimated; }
    public void setOutputTokensEstimated(boolean outputTokensEstimated) { this.outputTokensEstimated = outputTokensEstimated; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public boolean isStreamStarted() { return streamStarted; }
    public void setStreamStarted(boolean streamStarted) { this.streamStarted = streamStarted; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getModelKey() { return modelKey; }
    public void setModelKey(String modelKey) { this.modelKey = modelKey; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
