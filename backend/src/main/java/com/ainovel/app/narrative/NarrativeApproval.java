package com.ainovel.app.narrative;
import com.ainovel.app.v2.model.V2ManuscriptVersion;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="narrative_approvals", uniqueConstraints=@UniqueConstraint(columnNames={"branch_id","idempotency_key"}))
public class NarrativeApproval {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="branch_id") @OnDelete(action=OnDeleteAction.CASCADE)
    private NarrativeLedger ledger;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="version_id") @OnDelete(action=OnDeleteAction.CASCADE)
    private V2ManuscriptVersion version;
    @Column(nullable=false) private UUID sceneId;
    @Column(nullable=false) private UUID confirmedBy;
    @Column(nullable=false,length=128) private String idempotencyKey;
    @Column(nullable=false,length=64) private String requestHash;
    @Column(nullable=false,length=64) private String textHash;
    @Lob @Column(nullable=false) private String blocksJson;
    @Lob @Column(nullable=false) private String positionJson;
    @Column(nullable=false) private Instant confirmedAt;

    public UUID getId() { return id; }
    public void setId(UUID value) { this.id = value; }
    public NarrativeLedger getLedger() { return ledger; }
    public void setLedger(NarrativeLedger value) { this.ledger = value; }
    public V2ManuscriptVersion getVersion() { return version; }
    public void setVersion(V2ManuscriptVersion value) { this.version = value; }
    public UUID getSceneId() { return sceneId; }
    public void setSceneId(UUID value) { this.sceneId = value; }
    public UUID getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(UUID value) { this.confirmedBy = value; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String value) { this.idempotencyKey = value; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String value) { this.requestHash = value; }
    public String getTextHash() { return textHash; }
    public void setTextHash(String value) { this.textHash = value; }
    public String getBlocksJson() { return blocksJson; }
    public void setBlocksJson(String value) { this.blocksJson = value; }
    public String getPositionJson() { return positionJson; }
    public void setPositionJson(String value) { this.positionJson = value; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant value) { this.confirmedAt = value; }
}
