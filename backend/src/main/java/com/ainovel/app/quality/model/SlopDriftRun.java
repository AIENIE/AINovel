package com.ainovel.app.quality.model;

import com.ainovel.app.quality.SlopDriftStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "slop_drift_runs", indexes = {
        @Index(name = "idx_slop_drift_story", columnList = "story_id"),
        @Index(name = "idx_slop_drift_manuscript", columnList = "manuscript_id")
})
public class SlopDriftRun {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "story_id", nullable = false)
    private UUID storyId;

    @Column(name = "manuscript_id", nullable = false)
    private UUID manuscriptId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SlopDriftStatus status;

    @Column(name = "overall_risk_score", nullable = false)
    private int overallRiskScore;

    @Column(name = "risk_label", length = 32)
    private String riskLabel;

    @Column(name = "safe_claim", length = 500)
    private String safeClaim;

    @Column(length = 800)
    private String summary;

    @Column(name = "total_characters", nullable = false)
    private int totalCharacters;

    @Column(name = "window_count", nullable = false)
    private int windowCount;

    @Column(name = "source_text_hash", length = 64)
    private String sourceTextHash;

    @Column(name = "window_summaries_json", columnDefinition = "longtext")
    private String windowSummariesJson;

    @Column(name = "metric_curves_json", columnDefinition = "longtext")
    private String metricCurvesJson;

    @Column(name = "drift_points_json", columnDefinition = "longtext")
    private String driftPointsJson;

    @Column(name = "evidence_items_json", columnDefinition = "longtext")
    private String evidenceItemsJson;

    @Column(name = "alternative_explanations_json", columnDefinition = "longtext")
    private String alternativeExplanationsJson;

    @Column(name = "rewrite_tasks_json", columnDefinition = "longtext")
    private String rewriteTasksJson;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getStoryId() { return storyId; }
    public void setStoryId(UUID storyId) { this.storyId = storyId; }
    public UUID getManuscriptId() { return manuscriptId; }
    public void setManuscriptId(UUID manuscriptId) { this.manuscriptId = manuscriptId; }
    public SlopDriftStatus getStatus() { return status; }
    public void setStatus(SlopDriftStatus status) { this.status = status; }
    public int getOverallRiskScore() { return overallRiskScore; }
    public void setOverallRiskScore(int overallRiskScore) { this.overallRiskScore = overallRiskScore; }
    public String getRiskLabel() { return riskLabel; }
    public void setRiskLabel(String riskLabel) { this.riskLabel = riskLabel; }
    public String getSafeClaim() { return safeClaim; }
    public void setSafeClaim(String safeClaim) { this.safeClaim = safeClaim; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public int getTotalCharacters() { return totalCharacters; }
    public void setTotalCharacters(int totalCharacters) { this.totalCharacters = totalCharacters; }
    public int getWindowCount() { return windowCount; }
    public void setWindowCount(int windowCount) { this.windowCount = windowCount; }
    public String getSourceTextHash() { return sourceTextHash; }
    public void setSourceTextHash(String sourceTextHash) { this.sourceTextHash = sourceTextHash; }
    public String getWindowSummariesJson() { return windowSummariesJson; }
    public void setWindowSummariesJson(String windowSummariesJson) { this.windowSummariesJson = windowSummariesJson; }
    public String getMetricCurvesJson() { return metricCurvesJson; }
    public void setMetricCurvesJson(String metricCurvesJson) { this.metricCurvesJson = metricCurvesJson; }
    public String getDriftPointsJson() { return driftPointsJson; }
    public void setDriftPointsJson(String driftPointsJson) { this.driftPointsJson = driftPointsJson; }
    public String getEvidenceItemsJson() { return evidenceItemsJson; }
    public void setEvidenceItemsJson(String evidenceItemsJson) { this.evidenceItemsJson = evidenceItemsJson; }
    public String getAlternativeExplanationsJson() { return alternativeExplanationsJson; }
    public void setAlternativeExplanationsJson(String alternativeExplanationsJson) { this.alternativeExplanationsJson = alternativeExplanationsJson; }
    public String getRewriteTasksJson() { return rewriteTasksJson; }
    public void setRewriteTasksJson(String rewriteTasksJson) { this.rewriteTasksJson = rewriteTasksJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
