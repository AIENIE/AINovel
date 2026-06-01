package com.ainovel.app.quality.model;

import com.ainovel.app.quality.SlopQualityStatus;
import com.ainovel.app.quality.SlopSeverity;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "slop_quality_runs", indexes = {
        @Index(name = "idx_slop_run_manuscript", columnList = "manuscript_id"),
        @Index(name = "idx_slop_run_scene", columnList = "scene_id"),
        @Index(name = "idx_slop_run_story", columnList = "story_id")
})
public class SlopQualityRun {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "story_id", nullable = false)
    private UUID storyId;

    @Column(name = "manuscript_id", nullable = false)
    private UUID manuscriptId;

    @Column(name = "scene_id", nullable = false)
    private UUID sceneId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SlopQualityStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "max_severity", nullable = false, length = 32)
    private SlopSeverity maxSeverity;

    @Column(name = "overall_risk_score", nullable = false)
    private int overallRiskScore;

    @Column(nullable = false)
    private boolean revised;

    @Column(name = "revision_count", nullable = false)
    private int revisionCount;

    @Column(name = "candidate_text_hash", length = 64)
    private String candidateTextHash;

    @Column(name = "accepted_text_hash", length = 64)
    private String acceptedTextHash;

    @Column(length = 800)
    private String summary;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SlopQualityIssue> issues = new ArrayList<>();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getStoryId() { return storyId; }
    public void setStoryId(UUID storyId) { this.storyId = storyId; }
    public UUID getManuscriptId() { return manuscriptId; }
    public void setManuscriptId(UUID manuscriptId) { this.manuscriptId = manuscriptId; }
    public UUID getSceneId() { return sceneId; }
    public void setSceneId(UUID sceneId) { this.sceneId = sceneId; }
    public SlopQualityStatus getStatus() { return status; }
    public void setStatus(SlopQualityStatus status) { this.status = status; }
    public SlopSeverity getMaxSeverity() { return maxSeverity; }
    public void setMaxSeverity(SlopSeverity maxSeverity) { this.maxSeverity = maxSeverity; }
    public int getOverallRiskScore() { return overallRiskScore; }
    public void setOverallRiskScore(int overallRiskScore) { this.overallRiskScore = overallRiskScore; }
    public boolean isRevised() { return revised; }
    public void setRevised(boolean revised) { this.revised = revised; }
    public int getRevisionCount() { return revisionCount; }
    public void setRevisionCount(int revisionCount) { this.revisionCount = revisionCount; }
    public String getCandidateTextHash() { return candidateTextHash; }
    public void setCandidateTextHash(String candidateTextHash) { this.candidateTextHash = candidateTextHash; }
    public String getAcceptedTextHash() { return acceptedTextHash; }
    public void setAcceptedTextHash(String acceptedTextHash) { this.acceptedTextHash = acceptedTextHash; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<SlopQualityIssue> getIssues() { return issues; }
    public void setIssues(List<SlopQualityIssue> issues) { this.issues = issues; }
}
