package com.ainovel.app.economy.model;

import com.ainovel.app.user.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_credit_reservations", uniqueConstraints =
        @UniqueConstraint(name = "uk_ai_reservation_user_key", columnNames = {"user_id", "idempotency_key"}))
public class AiCreditReservation {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(nullable = false, length = 128)
    private String idempotencyKey;
    @Column(nullable = false, length = 64)
    private String requestHash;
    @Column(nullable = false, length = 32)
    private String referenceType;
    @Column(nullable = false, length = 128)
    private String referenceId;
    @Column(nullable = false)
    private long reservedAmount;
    @Column(nullable = false)
    private long settledAmount;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24)
    private Status status;
    @Lob @Column(columnDefinition = "longtext")
    private String resultContent;
    private long promptTokens;
    private long completionTokens;
    private long cacheTokens;
    private int attemptCount;
    @CreationTimestamp private Instant createdAt;
    @UpdateTimestamp private Instant updatedAt;

    public enum Status { RESERVED, COMPLETED, RELEASED }
    public UUID getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String value) { this.idempotencyKey = value; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String value) { this.requestHash = value; }
    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String value) { this.referenceType = value; }
    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String value) { this.referenceId = value; }
    public long getReservedAmount() { return reservedAmount; }
    public void setReservedAmount(long value) { this.reservedAmount = value; }
    public long getSettledAmount() { return settledAmount; }
    public void setSettledAmount(long value) { this.settledAmount = value; }
    public Status getStatus() { return status; }
    public void setStatus(Status value) { this.status = value; }
    public String getResultContent() { return resultContent; }
    public void setResultContent(String value) { this.resultContent = value; }
    public long getPromptTokens() { return promptTokens; }
    public void setPromptTokens(long value) { this.promptTokens = value; }
    public long getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(long value) { this.completionTokens = value; }
    public long getCacheTokens() { return cacheTokens; }
    public void setCacheTokens(long value) { this.cacheTokens = value; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int value) { this.attemptCount = value; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
