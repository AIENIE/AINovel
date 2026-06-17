package com.ainovel.app.v2.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "version_diffs", uniqueConstraints = @UniqueConstraint(columnNames = {"from_version_id", "to_version_id"}))
public class V2VersionDiff {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_version_id")
    private V2ManuscriptVersion fromVersion;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_version_id")
    private V2ManuscriptVersion toVersion;
    @Lob
    private String diffJson;
    @CreationTimestamp
    private Instant createdAt;

    public UUID getId() { return id; }
    public V2ManuscriptVersion getFromVersion() { return fromVersion; }
    public void setFromVersion(V2ManuscriptVersion fromVersion) { this.fromVersion = fromVersion; }
    public V2ManuscriptVersion getToVersion() { return toVersion; }
    public void setToVersion(V2ManuscriptVersion toVersion) { this.toVersion = toVersion; }
    public String getDiffJson() { return diffJson; }
    public void setDiffJson(String diffJson) { this.diffJson = diffJson; }
    public Instant getCreatedAt() { return createdAt; }
}
