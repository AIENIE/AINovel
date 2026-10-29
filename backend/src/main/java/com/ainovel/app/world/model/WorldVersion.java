package com.ainovel.app.world.model;

import jakarta.persistence.*;
import java.util.UUID;
import java.time.Instant;

@Entity
@Table(name = "world_versions")
@org.hibernate.annotations.Immutable
public class WorldVersion {
    @Id private UUID id;
    private UUID worldId;
    private int sequenceNumber;
    private String origin;
    private String legacyVersion;
    private long draftRevision;
    private String contentHash;
    @Lob @Column(columnDefinition = "LONGTEXT")
    private String snapshotJson;
    @Lob @Column(columnDefinition = "LONGTEXT")
    private String releaseNote;
    private String requestKey;
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getWorldId() { return worldId; }
    public void setWorldId(UUID value) { this.worldId = value; }
    public int getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(int value) { this.sequenceNumber = value; }
    public String getOrigin() { return origin; }
    public void setOrigin(String value) { this.origin = value; }
    public String getLegacyVersion() { return legacyVersion; }
    public void setLegacyVersion(String value) { this.legacyVersion = value; }
    public long getDraftRevision() { return draftRevision; }
    public void setDraftRevision(long value) { this.draftRevision = value; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String value) { this.contentHash = value; }
    public String getSnapshotJson() { return snapshotJson; }
    public void setSnapshotJson(String value) { this.snapshotJson = value; }
    public String getReleaseNote() { return releaseNote; }
    public void setReleaseNote(String value) { this.releaseNote = value; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String value) { this.requestKey = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { this.createdAt = value; }
}
