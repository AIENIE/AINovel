package com.ainovel.app.quality;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "slop_patterns")
public class SlopPattern {
    @Id
    @Column(columnDefinition = "binary(16)")
    private UUID id;

    @Column(nullable = false, length = 32)
    private String category;

    @Column(nullable = false, length = 255)
    private String pattern;

    @Column(nullable = false)
    private int weight;

    @Column(nullable = false)
    private boolean enabled;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }
    public int getWeight() { return weight; }
    public void setWeight(int weight) { this.weight = weight; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
