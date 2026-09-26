package com.ainovel.app.manuscript.model;

import com.ainovel.app.story.model.Outline;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "manuscripts")
@org.hibernate.annotations.DynamicUpdate
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
    @Basic(fetch = FetchType.LAZY)
    private String sectionsJson; // sceneId -> content

    @Lob
    @Basic(fetch = FetchType.LAZY)
    private String characterLogsJson; // optional

    private int contentStorageVersion = 1;

    private UUID currentBranchId;

    @Version
    private long version;

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
    public int getContentStorageVersion() { return contentStorageVersion; }
    public void setContentStorageVersion(int value) { this.contentStorageVersion = value; }
    public String getCharacterLogsJson() { return characterLogsJson; }
    public void setCharacterLogsJson(String characterLogsJson) { this.characterLogsJson = characterLogsJson; }
    public UUID getCurrentBranchId() { return currentBranchId; }
    public void setCurrentBranchId(UUID currentBranchId) { this.currentBranchId = currentBranchId; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
