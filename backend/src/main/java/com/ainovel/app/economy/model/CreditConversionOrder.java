package com.ainovel.app.economy.model;

import com.ainovel.app.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "credit_conversion_orders",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_conversion_order_no", columnNames = {"order_no"}),
                @UniqueConstraint(name = "uk_conversion_user_idempotency", columnNames = {"user_id", "idempotency_key"})
        }
)
public class CreditConversionOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_no", nullable = false, length = 64)
    private String orderNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(nullable = false)
    private long requestedAmount;

    @Column(nullable = false)
    private long convertedAmount;

    @Column(nullable = false)
    private long projectBefore = 0L;

    @Column(nullable = false)
    private long projectAfter = 0L;

    @Column(nullable = false)
    private long publicBefore = 0L;

    @Column(nullable = false)
    private long publicAfter = 0L;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ConversionOrderStatus status = ConversionOrderStatus.PENDING;

    @Column(name = "remote_request_id", length = 128)
    private String remoteRequestId;

    @Column(name = "remote_message", length = 500)
    private String remoteMessage;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public long getRequestedAmount() {
        return requestedAmount;
    }

    public void setRequestedAmount(long requestedAmount) {
        this.requestedAmount = requestedAmount;
    }

    public long getConvertedAmount() {
        return convertedAmount;
    }

    public void setConvertedAmount(long convertedAmount) {
        this.convertedAmount = convertedAmount;
    }

    public long getProjectBefore() {
        return projectBefore;
    }

    public void setProjectBefore(long projectBefore) {
        this.projectBefore = projectBefore;
    }

    public long getProjectAfter() {
        return projectAfter;
    }

    public void setProjectAfter(long projectAfter) {
        this.projectAfter = projectAfter;
    }

    public long getPublicBefore() {
        return publicBefore;
    }

    public void setPublicBefore(long publicBefore) {
        this.publicBefore = publicBefore;
    }

    public long getPublicAfter() {
        return publicAfter;
    }

    public void setPublicAfter(long publicAfter) {
        this.publicAfter = publicAfter;
    }

    public ConversionOrderStatus getStatus() {
        return status;
    }

    public void setStatus(ConversionOrderStatus status) {
        this.status = status;
    }

    public String getRemoteRequestId() {
        return remoteRequestId;
    }

    public void setRemoteRequestId(String remoteRequestId) {
        this.remoteRequestId = remoteRequestId;
    }

    public String getRemoteMessage() {
        return remoteMessage;
    }

    public void setRemoteMessage(String remoteMessage) {
        this.remoteMessage = remoteMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
