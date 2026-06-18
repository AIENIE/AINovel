package com.ainovel.app.v2.model;

import com.ainovel.app.story.model.Story;
import com.ainovel.app.user.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "model_usage_logs")
public class V2ModelUsageLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "story_id")
    private Story story;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "model_id")
    private V2ModelRegistry model;
    @Column(nullable = false, length = 50)
    private String taskType;
    @Column(nullable = false)
    private int inputTokens;
    @Column(nullable = false)
    private int outputTokens;
    @Column(nullable = false)
    private int latencyMs;
    @Column(nullable = false, precision = 10, scale = 6)
    private BigDecimal costEstimate = BigDecimal.ZERO;
    @Column(nullable = false)
    private boolean success;
    @Lob
    private String errorMessage;
    @CreationTimestamp
    private Instant createdAt;

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Story getStory() { return story; }
    public void setStory(Story story) { this.story = story; }
    public V2ModelRegistry getModel() { return model; }
    public void setModel(V2ModelRegistry model) { this.model = model; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public int getInputTokens() { return inputTokens; }
    public void setInputTokens(int inputTokens) { this.inputTokens = inputTokens; }
    public int getOutputTokens() { return outputTokens; }
    public void setOutputTokens(int outputTokens) { this.outputTokens = outputTokens; }
    public int getLatencyMs() { return latencyMs; }
    public void setLatencyMs(int latencyMs) { this.latencyMs = latencyMs; }
    public BigDecimal getCostEstimate() { return costEstimate; }
    public void setCostEstimate(BigDecimal costEstimate) { this.costEstimate = costEstimate; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
}
