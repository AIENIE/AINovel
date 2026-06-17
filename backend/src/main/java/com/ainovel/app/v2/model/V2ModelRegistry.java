package com.ainovel.app.v2.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "model_registry")
public class V2ModelRegistry {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, unique = true, length = 100)
    private String modelKey;
    @Column(nullable = false, length = 200)
    private String displayName;
    @Column(nullable = false, length = 50)
    private String provider;
    @Lob
    private String capabilitiesJson;
    @Column(nullable = false)
    private int maxContextTokens;
    @Column(nullable = false)
    private int maxOutputTokens;
    @Column(name = "cost_per_1k_input", nullable = false, precision = 10, scale = 6)
    private BigDecimal costPer1kInput = BigDecimal.ZERO;
    @Column(name = "cost_per_1k_output", nullable = false, precision = 10, scale = 6)
    private BigDecimal costPer1kOutput = BigDecimal.ZERO;
    @Column(nullable = false)
    private boolean supportsStreaming = true;
    @Column(name = "is_available", nullable = false)
    private boolean available = true;
    @Column(nullable = false)
    private int priority;
    @CreationTimestamp
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;

    public UUID getId() { return id; }
    public String getModelKey() { return modelKey; }
    public void setModelKey(String modelKey) { this.modelKey = modelKey; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getCapabilitiesJson() { return capabilitiesJson; }
    public void setCapabilitiesJson(String capabilitiesJson) { this.capabilitiesJson = capabilitiesJson; }
    public int getMaxContextTokens() { return maxContextTokens; }
    public void setMaxContextTokens(int maxContextTokens) { this.maxContextTokens = maxContextTokens; }
    public int getMaxOutputTokens() { return maxOutputTokens; }
    public void setMaxOutputTokens(int maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }
    public BigDecimal getCostPer1kInput() { return costPer1kInput; }
    public void setCostPer1kInput(BigDecimal costPer1kInput) { this.costPer1kInput = costPer1kInput; }
    public BigDecimal getCostPer1kOutput() { return costPer1kOutput; }
    public void setCostPer1kOutput(BigDecimal costPer1kOutput) { this.costPer1kOutput = costPer1kOutput; }
    public boolean isSupportsStreaming() { return supportsStreaming; }
    public void setSupportsStreaming(boolean supportsStreaming) { this.supportsStreaming = supportsStreaming; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
