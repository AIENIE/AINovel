package com.ainovel.app.v2.model;

import com.ainovel.app.user.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_model_preferences", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "task_type"}))
public class V2UserModelPreference {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;
    @Column(nullable = false, length = 50)
    private String taskType;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_model_id")
    private V2ModelRegistry preferredModel;
    @CreationTimestamp
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public V2ModelRegistry getPreferredModel() { return preferredModel; }
    public void setPreferredModel(V2ModelRegistry preferredModel) { this.preferredModel = preferredModel; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
