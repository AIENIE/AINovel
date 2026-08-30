package com.ainovel.app.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "external_identities", uniqueConstraints = {
        @UniqueConstraint(name = "uk_external_identity_issuer_uid", columnNames = {"issuer", "remote_uid"}),
        @UniqueConstraint(name = "uk_external_identity_user_issuer", columnNames = {"user_id", "issuer"})
})
public class ExternalIdentity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String issuer;

    @Column(name = "remote_uid", nullable = false)
    private long remoteUid;

    @Column(name = "verified_username", nullable = false, length = 255)
    private String verifiedUsername;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public long getRemoteUid() { return remoteUid; }
    public void setRemoteUid(long remoteUid) { this.remoteUid = remoteUid; }
    public String getVerifiedUsername() { return verifiedUsername; }
    public void setVerifiedUsername(String verifiedUsername) { this.verifiedUsername = verifiedUsername; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
