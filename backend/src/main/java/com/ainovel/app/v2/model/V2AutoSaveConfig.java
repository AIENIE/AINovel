package com.ainovel.app.v2.model;

import com.ainovel.app.user.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auto_save_config")
public class V2AutoSaveConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;
    @Column(nullable = false)
    private int autoSaveIntervalSeconds = 300;
    @Column(nullable = false)
    private int maxAutoVersions = 100;
    @CreationTimestamp
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public int getAutoSaveIntervalSeconds() { return autoSaveIntervalSeconds; }
    public void setAutoSaveIntervalSeconds(int autoSaveIntervalSeconds) { this.autoSaveIntervalSeconds = autoSaveIntervalSeconds; }
    public int getMaxAutoVersions() { return maxAutoVersions; }
    public void setMaxAutoVersions(int maxAutoVersions) { this.maxAutoVersions = maxAutoVersions; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
