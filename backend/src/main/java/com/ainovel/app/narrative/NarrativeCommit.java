package com.ainovel.app.narrative;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="narrative_commits",uniqueConstraints={
    @UniqueConstraint(columnNames={"branch_id","revision"}),
    @UniqueConstraint(columnNames={"branch_id","idempotency_key"})})
public class NarrativeCommit {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="branch_id") @OnDelete(action=OnDeleteAction.CASCADE)
    private NarrativeLedger ledger;
    @Column(nullable=false) private long revision;
    @Column(nullable=false,length=20) private String kind;
    @Column(length=128) private String idempotencyKey;
    @Column(length=64) private String requestHash;
    private UUID authorId;
    @Lob @Column(nullable=false) private String detailJson;
    @Column(nullable=false) private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID value) { this.id = value; }
    public NarrativeLedger getLedger() { return ledger; }
    public void setLedger(NarrativeLedger value) { this.ledger = value; }
    public long getRevision() { return revision; }
    public void setRevision(long value) { this.revision = value; }
    public String getKind() { return kind; }
    public void setKind(String value) { this.kind = value; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String value) { this.idempotencyKey = value; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String value) { this.requestHash = value; }
    public UUID getAuthorId() { return authorId; }
    public void setAuthorId(UUID value) { this.authorId = value; }
    public String getDetailJson() { return detailJson; }
    public void setDetailJson(String value) { this.detailJson = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { this.createdAt = value; }
}
