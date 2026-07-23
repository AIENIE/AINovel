package com.ainovel.app.workflow.model;

import com.ainovel.app.user.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "async_jobs", uniqueConstraints = @UniqueConstraint(
        name = "uk_async_job_idempotency", columnNames = "idempotency_key"))
public class AsyncJob {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(nullable = false)
    private UUID scopeId;
    @Column(nullable = false, length = 64)
    private String jobType;
    @Enumerated(EnumType.STRING) @Column(length = 32)
    private GuidedCreationStep step;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private AsyncJobStatus status;
    @Column(nullable = false)
    private int progress;
    @Column(name = "output_tokens", nullable = false)
    private long outputTokens;
    @Column(name = "output_tokens_estimated", nullable = false)
    private boolean outputTokensEstimated = true;
    @Column(nullable = false)
    private int attemptCount;
    @Column(nullable = false, unique = true, length = 160)
    private String idempotencyKey;
    @Lob private String payloadJson;
    @Lob private String resultJson;
    @Column(length = 500)
    private String errorMessage;
    @Column(nullable = false)
    private long chargedCredits;
    @Column(nullable = false)
    private long remainingCredits;
    @Column(length = 100)
    private String leaseOwner;
    private Instant leaseUntil;
    private Instant completedAt;
    @CreationTimestamp private Instant createdAt;
    @UpdateTimestamp private Instant updatedAt;

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public UUID getScopeId() { return scopeId; }
    public void setScopeId(UUID scopeId) { this.scopeId = scopeId; }
    public String getJobType() { return jobType; }
    public void setJobType(String jobType) { this.jobType = jobType; }
    public GuidedCreationStep getStep() { return step; }
    public void setStep(GuidedCreationStep step) { this.step = step; }
    public AsyncJobStatus getStatus() { return status; }
    public void setStatus(AsyncJobStatus status) { this.status = status; }
    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }
    public long getOutputTokens() { return outputTokens; }
    public void setOutputTokens(long outputTokens) { this.outputTokens = outputTokens; }
    public boolean isOutputTokensEstimated() { return outputTokensEstimated; }
    public void setOutputTokensEstimated(boolean outputTokensEstimated) { this.outputTokensEstimated = outputTokensEstimated; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public long getChargedCredits() { return chargedCredits; }
    public void setChargedCredits(long chargedCredits) { this.chargedCredits = chargedCredits; }
    public long getRemainingCredits() { return remainingCredits; }
    public void setRemainingCredits(long remainingCredits) { this.remainingCredits = remainingCredits; }
    public String getLeaseOwner() { return leaseOwner; }
    public void setLeaseOwner(String leaseOwner) { this.leaseOwner = leaseOwner; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(Instant leaseUntil) { this.leaseUntil = leaseUntil; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
