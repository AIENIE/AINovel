package com.ainovel.app.manuscript.model;

import com.ainovel.app.story.model.Outline;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "manuscripts")
public class Manuscript {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "outline_id")
    private Outline outline;

    private String title;
    private String worldId;

    @Lob
    private String sectionsJson; // sceneId -> content

    @Lob
    private String characterLogsJson; // optional

    private UUID currentBranchId;

    @CreationTimestamp
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;

    public Manuscript() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Outline getOutline() { return outline; }
    public void setOutline(Outline outline) { this.outline = outline; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getWorldId() { return worldId; }
    public void setWorldId(String worldId) { this.worldId = worldId; }
    public String getSectionsJson() { return sectionsJson; }
    public void setSectionsJson(String sectionsJson) { this.sectionsJson = sectionsJson; }
    public String getCharacterLogsJson() { return characterLogsJson; }
    public void setCharacterLogsJson(String characterLogsJson) { this.characterLogsJson = characterLogsJson; }
    public UUID getCurrentBranchId() { return currentBranchId; }
    public void setCurrentBranchId(UUID currentBranchId) { this.currentBranchId = currentBranchId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
