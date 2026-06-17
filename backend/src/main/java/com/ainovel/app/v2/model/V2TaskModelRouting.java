package com.ainovel.app.v2.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_model_routing")
public class V2TaskModelRouting {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, unique = true, length = 50)
    private String taskType;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recommended_model_id")
    private V2ModelRegistry recommendedModel;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fallback_model_id")
    private V2ModelRegistry fallbackModel;
    @Column(nullable = false, length = 30)
    private String routingStrategy = "fixed";
    @Lob
    private String configJson;
    @CreationTimestamp
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;

    public UUID getId() { return id; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public V2ModelRegistry getRecommendedModel() { return recommendedModel; }
    public void setRecommendedModel(V2ModelRegistry recommendedModel) { this.recommendedModel = recommendedModel; }
    public V2ModelRegistry getFallbackModel() { return fallbackModel; }
    public void setFallbackModel(V2ModelRegistry fallbackModel) { this.fallbackModel = fallbackModel; }
    public String getRoutingStrategy() { return routingStrategy; }
    public void setRoutingStrategy(String routingStrategy) { this.routingStrategy = routingStrategy; }
    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
