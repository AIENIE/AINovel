package com.ainovel.app.style.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "style_profile_scene_overrides", uniqueConstraints = {
        @UniqueConstraint(name = "uk_style_profile_scene", columnNames = {"style_profile_id", "scene_type"})
})
public class StyleProfileSceneOverride {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "style_profile_id", nullable = false)
    private StyleProfile styleProfile;

    @Column(name = "scene_type", nullable = false, length = 32)
    private String sceneType;

    @Column(name = "override_json", columnDefinition = "longtext")
    private String overrideJson;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public StyleProfile getStyleProfile() { return styleProfile; }
    public void setStyleProfile(StyleProfile styleProfile) { this.styleProfile = styleProfile; }
    public String getSceneType() { return sceneType; }
    public void setSceneType(String sceneType) { this.sceneType = sceneType; }
    public String getOverrideJson() { return overrideJson; }
    public void setOverrideJson(String overrideJson) { this.overrideJson = overrideJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
