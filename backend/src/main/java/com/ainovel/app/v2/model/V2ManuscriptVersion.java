package com.ainovel.app.v2.model;

import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.user.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "manuscript_versions")
public class V2ManuscriptVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manuscript_id")
    private Manuscript manuscript;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id")
    private V2ManuscriptBranch branch;
    @Column(nullable = false)
    private int versionNumber;
    @Column(length = 200)
    private String label;
    @Column(nullable = false, length = 32)
    private String snapshotType;
    @Column(nullable = false, length = 64)
    private String contentHash;
    @Lob
    private String sectionsJson;
    @Lob
    private String metadataJson;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_version_id")
    private V2ManuscriptVersion parentVersion;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by")
    private User createdBy;
    @CreationTimestamp
    private Instant createdAt;

    public UUID getId() { return id; }
    public Manuscript getManuscript() { return manuscript; }
    public void setManuscript(Manuscript manuscript) { this.manuscript = manuscript; }
    public V2ManuscriptBranch getBranch() { return branch; }
    public void setBranch(V2ManuscriptBranch branch) { this.branch = branch; }
    public int getVersionNumber() { return versionNumber; }
    public void setVersionNumber(int versionNumber) { this.versionNumber = versionNumber; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getSnapshotType() { return snapshotType; }
    public void setSnapshotType(String snapshotType) { this.snapshotType = snapshotType; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public String getSectionsJson() { return sectionsJson; }
    public void setSectionsJson(String sectionsJson) { this.sectionsJson = sectionsJson; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public V2ManuscriptVersion getParentVersion() { return parentVersion; }
    public void setParentVersion(V2ManuscriptVersion parentVersion) { this.parentVersion = parentVersion; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
