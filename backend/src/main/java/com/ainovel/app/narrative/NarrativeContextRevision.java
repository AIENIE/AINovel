package com.ainovel.app.narrative;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="narrative_context_revisions", uniqueConstraints={
        @UniqueConstraint(columnNames={"branch_id", "revision"}),
        @UniqueConstraint(columnNames={"branch_id", "idempotency_key"})})
public class NarrativeContextRevision {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(nullable=false) private UUID branchId;
    @Column(nullable=false) private long revision;
    @Column(length=128) private String idempotencyKey;
    @Column(length=64) private String requestHash;
    @Lob @Column(nullable=false) private String documentJson;
    @Lob private String receiptJson;
    @Column(nullable=false) private Instant createdAt;
    private UUID authorId;
    public UUID getId() { return id; }
    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID value) { branchId=value; }
    public long getRevision() { return revision; }
    public void setRevision(long value) { revision=value; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String value) { idempotencyKey=value; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String value) { requestHash=value; }
    public String getDocumentJson() { return documentJson; }
    public void setDocumentJson(String value) { documentJson=value; }
    public String getReceiptJson() { return receiptJson; }
    public void setReceiptJson(String value) { receiptJson=value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt=value; }
    public void setAuthorId(UUID value) { authorId=value; }
}
