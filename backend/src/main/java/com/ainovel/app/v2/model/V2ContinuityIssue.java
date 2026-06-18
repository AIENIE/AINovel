package com.ainovel.app.v2.model;

import com.ainovel.app.story.model.Story;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "continuity_issues")
public class V2ContinuityIssue {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id")
    private Story story;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id")
    private V2BetaReaderReport report;
    @Column(nullable = false, length = 50)
    private String issueType;
    @Column(nullable = false, length = 32)
    private String severity;
    @Lob
    private String description;
    @Lob
    private String evidenceJson;
    @Lob
    private String suggestion;
    @Column(nullable = false, length = 32)
    private String status;
    private Instant resolvedAt;
    @CreationTimestamp
    private Instant createdAt;

    public UUID getId() { return id; }
    public Story getStory() { return story; }
    public void setStory(Story story) { this.story = story; }
    public V2BetaReaderReport getReport() { return report; }
    public void setReport(V2BetaReaderReport report) { this.report = report; }
    public String getIssueType() { return issueType; }
    public void setIssueType(String issueType) { this.issueType = issueType; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getEvidenceJson() { return evidenceJson; }
    public void setEvidenceJson(String evidenceJson) { this.evidenceJson = evidenceJson; }
    public String getSuggestion() { return suggestion; }
    public void setSuggestion(String suggestion) { this.suggestion = suggestion; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
