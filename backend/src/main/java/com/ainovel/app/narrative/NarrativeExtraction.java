package com.ainovel.app.narrative;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="narrative_extractions")
public class NarrativeExtraction {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="approval_id",unique=true) @OnDelete(action=OnDeleteAction.CASCADE)
    private NarrativeApproval approval;
    private UUID operationId;
    @Column(nullable=false) private long baseCanonRevision;
    @Column(nullable=false,length=40) private String promptVersion;
    @Column(length=120) private String model;
    @Lob @Column(nullable=false) private String inputJson;
    @Lob String candidatesJson;
    @Lob String usageJson;
    @Lob String reviewJson;
    @Column(length=80) private String error;
    @Column(nullable=false) private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID value) { this.id = value; }
    public NarrativeApproval getApproval() { return approval; }
    public void setApproval(NarrativeApproval value) { this.approval = value; }
    public UUID getOperationId() { return operationId; }
    public void setOperationId(UUID value) { this.operationId = value; }
    public long getBaseCanonRevision() { return baseCanonRevision; }
    public void setBaseCanonRevision(long value) { this.baseCanonRevision = value; }
    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String value) { this.promptVersion = value; }
    public String getModel() { return model; }
    public void setModel(String value) { this.model = value; }
    public String getInputJson() { return inputJson; }
    public void setInputJson(String value) { this.inputJson = value; }
    public String getCandidatesJson() { return candidatesJson; }
    public void setCandidatesJson(String value) { this.candidatesJson = value; }
    public String getUsageJson() { return usageJson; }
    public void setUsageJson(String value) { this.usageJson = value; }
    public String getReviewJson() { return reviewJson; }
    public void setReviewJson(String value) { this.reviewJson = value; }
    public String getError() { return error; }
    public void setError(String value) { this.error = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { this.createdAt = value; }
}
