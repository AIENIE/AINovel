package com.ainovel.app.style.model;

import com.ainovel.app.story.model.Story;
import com.ainovel.app.user.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "style_profiles", indexes = {
        @Index(name = "idx_style_profile_user", columnList = "user_id"),
        @Index(name = "idx_style_profile_story", columnList = "story_id")
})
public class StyleProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "story_id")
    private Story story;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "profile_type", length = 32)
    private String profileType;

    @Column(name = "dimensions_json", columnDefinition = "longtext")
    private String dimensionsJson;

    @Column(name = "sample_text", columnDefinition = "longtext")
    private String sampleText;

    @Column(name = "ai_analysis_json", columnDefinition = "longtext")
    private String aiAnalysisJson;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "styleProfile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StyleProfileSceneOverride> sceneOverrides = new ArrayList<>();

    public void addSceneOverride(StyleProfileSceneOverride override) {
        sceneOverrides.add(override);
        override.setStyleProfile(this);
    }

    public void replaceSceneOverrides(List<StyleProfileSceneOverride> overrides) {
        sceneOverrides.clear();
        if (overrides != null) {
            overrides.forEach(this::addSceneOverride);
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Story getStory() { return story; }
    public void setStory(Story story) { this.story = story; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getProfileType() { return profileType; }
    public void setProfileType(String profileType) { this.profileType = profileType; }
    public String getDimensionsJson() { return dimensionsJson; }
    public void setDimensionsJson(String dimensionsJson) { this.dimensionsJson = dimensionsJson; }
    public String getSampleText() { return sampleText; }
    public void setSampleText(String sampleText) { this.sampleText = sampleText; }
    public String getAiAnalysisJson() { return aiAnalysisJson; }
    public void setAiAnalysisJson(String aiAnalysisJson) { this.aiAnalysisJson = aiAnalysisJson; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<StyleProfileSceneOverride> getSceneOverrides() { return sceneOverrides; }
    public void setSceneOverrides(List<StyleProfileSceneOverride> sceneOverrides) { this.sceneOverrides = sceneOverrides; }
}
