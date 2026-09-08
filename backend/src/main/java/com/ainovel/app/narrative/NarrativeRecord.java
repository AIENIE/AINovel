package com.ainovel.app.narrative;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="narrative_records")
public class NarrativeRecord {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="extraction_id") @OnDelete(action=OnDeleteAction.CASCADE)
    private NarrativeExtraction extraction;
    @Lob @Column(nullable=false) private String assertionJson;
    @Lob @Column(nullable=false) private String dependencyIdsJson;
    @Column(nullable=false) private long createdRevision;
    private Long invalidatedRevision;
    private Long supersededRevision;
    @Column(nullable=false) private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID value) { this.id = value; }
    public NarrativeExtraction getExtraction() { return extraction; }
    public void setExtraction(NarrativeExtraction value) { this.extraction = value; }
    public String getAssertionJson() { return assertionJson; }
    public void setAssertionJson(String value) { this.assertionJson = value; }
    public String getDependencyIdsJson() { return dependencyIdsJson; }
    public void setDependencyIdsJson(String value) { this.dependencyIdsJson = value; }
    public long getCreatedRevision() { return createdRevision; }
    public void setCreatedRevision(long value) { this.createdRevision = value; }
    public Long getInvalidatedRevision() { return invalidatedRevision; }
    public void setInvalidatedRevision(Long value) { this.invalidatedRevision = value; }
    public Long getSupersededRevision() { return supersededRevision; }
    public void setSupersededRevision(Long value) { this.supersededRevision = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { this.createdAt = value; }
}
