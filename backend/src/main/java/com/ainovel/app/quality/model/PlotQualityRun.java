package com.ainovel.app.quality.model;

import com.ainovel.app.quality.PlotQualitySeverity;
import com.ainovel.app.quality.PlotQualityStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "plot_quality_runs", indexes = {
        @Index(name = "idx_plot_run_story", columnList = "story_id"),
        @Index(name = "idx_plot_run_manuscript", columnList = "manuscript_id"),
        @Index(name = "idx_plot_run_scene", columnList = "scene_id")
})
public class PlotQualityRun {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "story_id", nullable = false)
    private UUID storyId;

    @Column(name = "manuscript_id", nullable = false)
    private UUID manuscriptId;

    @Column(name = "scene_id", nullable = false)
    private UUID sceneId;

    @Column(name = "chapter_title", length = 200)
    private String chapterTitle;

    @Column(name = "scene_title", length = 200)
    private String sceneTitle;

    @Column(name = "chapter_order")
    private int chapterOrder;

    @Column(name = "scene_order")
    private int sceneOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PlotQualityStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "max_severity", nullable = false, length = 32)
    private PlotQualitySeverity maxSeverity;

    @Column(name = "overall_risk_score", nullable = false)
    private int overallRiskScore;

    @Column(name = "source_text_hash", length = 64)
    private String sourceTextHash;

    @Column(length = 800)
    private String summary;

    @Lob
    @Column(name = "rewrite_plan_json")
    private String rewritePlanJson;

    @Lob
    @Column(name = "surgical_fixes_json")
    private String surgicalFixesJson;

    @Lob
    @Column(name = "revision_candidate_text")
    private String revisionCandidateText;

    @Column(name = "revision_applied", nullable = false)
    private boolean revisionApplied;

    @Column(name = "revision_applied_at")
    private Instant revisionAppliedAt;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PlotQualityIssue> issues = new ArrayList<>();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getStoryId() { return storyId; }
    public void setStoryId(UUID storyId) { this.storyId = storyId; }
    public UUID getManuscriptId() { return manuscriptId; }
    public void setManuscriptId(UUID manuscriptId) { this.manuscriptId = manuscriptId; }
    public UUID getSceneId() { return sceneId; }
    public void setSceneId(UUID sceneId) { this.sceneId = sceneId; }
    public String getChapterTitle() { return chapterTitle; }
    public void setChapterTitle(String chapterTitle) { this.chapterTitle = chapterTitle; }
    public String getSceneTitle() { return sceneTitle; }
    public void setSceneTitle(String sceneTitle) { this.sceneTitle = sceneTitle; }
    public int getChapterOrder() { return chapterOrder; }
    public void setChapterOrder(int chapterOrder) { this.chapterOrder = chapterOrder; }
    public int getSceneOrder() { return sceneOrder; }
    public void setSceneOrder(int sceneOrder) { this.sceneOrder = sceneOrder; }
    public PlotQualityStatus getStatus() { return status; }
    public void setStatus(PlotQualityStatus status) { this.status = status; }
    public PlotQualitySeverity getMaxSeverity() { return maxSeverity; }
    public void setMaxSeverity(PlotQualitySeverity maxSeverity) { this.maxSeverity = maxSeverity; }
    public int getOverallRiskScore() { return overallRiskScore; }
    public void setOverallRiskScore(int overallRiskScore) { this.overallRiskScore = overallRiskScore; }
    public String getSourceTextHash() { return sourceTextHash; }
    public void setSourceTextHash(String sourceTextHash) { this.sourceTextHash = sourceTextHash; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getRewritePlanJson() { return rewritePlanJson; }
    public void setRewritePlanJson(String rewritePlanJson) { this.rewritePlanJson = rewritePlanJson; }
    public String getSurgicalFixesJson() { return surgicalFixesJson; }
    public void setSurgicalFixesJson(String surgicalFixesJson) { this.surgicalFixesJson = surgicalFixesJson; }
    public String getRevisionCandidateText() { return revisionCandidateText; }
    public void setRevisionCandidateText(String revisionCandidateText) { this.revisionCandidateText = revisionCandidateText; }
    public boolean isRevisionApplied() { return revisionApplied; }
    public void setRevisionApplied(boolean revisionApplied) { this.revisionApplied = revisionApplied; }
    public Instant getRevisionAppliedAt() { return revisionAppliedAt; }
    public void setRevisionAppliedAt(Instant revisionAppliedAt) { this.revisionAppliedAt = revisionAppliedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<PlotQualityIssue> getIssues() { return issues; }
    public void setIssues(List<PlotQualityIssue> issues) { this.issues = issues; }
}
