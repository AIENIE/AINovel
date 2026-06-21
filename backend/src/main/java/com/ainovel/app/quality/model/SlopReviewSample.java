package com.ainovel.app.quality.model;

import com.ainovel.app.quality.SlopReviewSampleStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "slop_review_samples", indexes = {
        @Index(name = "idx_slop_review_status", columnList = "status"),
        @Index(name = "idx_slop_review_source_run", columnList = "source_type, source_run_id"),
        @Index(name = "idx_slop_review_evidence", columnList = "expected_evidence_level")
})
public class SlopReviewSample {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "source_run_id")
    private UUID sourceRunId;

    @Column(name = "story_id")
    private UUID storyId;

    @Column(name = "manuscript_id")
    private UUID manuscriptId;

    @Column(name = "scene_id")
    private UUID sceneId;

    @Column(name = "sample_id", length = 120)
    private String sampleId;

    @Column(nullable = false, columnDefinition = "longtext")
    private String text;

    @Column(length = 120)
    private String genre;

    @Column(length = 120)
    private String tone;

    @Column(name = "chapter_title", length = 200)
    private String chapterTitle;

    @Column(name = "scene_title", length = 200)
    private String sceneTitle;

    @Column(name = "character_context", columnDefinition = "longtext")
    private String characterContext;

    @Column(name = "style_context", columnDefinition = "longtext")
    private String styleContext;

    @Column(name = "expected_evidence_level", nullable = false, length = 8)
    private String expectedEvidenceLevel;

    @Column(name = "expected_requires_ai_review", nullable = false)
    private boolean expectedRequiresAiReview;

    @Column(name = "observed_evidence_level", nullable = false, length = 8)
    private String observedEvidenceLevel;

    @Column(name = "observed_requires_ai_review", nullable = false)
    private boolean observedRequiresAiReview;

    @Column(name = "observed_risk_score", nullable = false)
    private int observedRiskScore;

    @Column(name = "observed_max_severity", nullable = false, length = 32)
    private String observedMaxSeverity;

    @Column(name = "matches_expected", nullable = false)
    private boolean matchesExpected;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SlopReviewSampleStatus status = SlopReviewSampleStatus.PENDING;

    @Column(name = "reviewer_note", length = 1000)
    private String reviewerNote;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Column(name = "reviewed_by", length = 120)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public UUID getSourceRunId() { return sourceRunId; }
    public void setSourceRunId(UUID sourceRunId) { this.sourceRunId = sourceRunId; }
    public UUID getStoryId() { return storyId; }
    public void setStoryId(UUID storyId) { this.storyId = storyId; }
    public UUID getManuscriptId() { return manuscriptId; }
    public void setManuscriptId(UUID manuscriptId) { this.manuscriptId = manuscriptId; }
    public UUID getSceneId() { return sceneId; }
    public void setSceneId(UUID sceneId) { this.sceneId = sceneId; }
    public String getSampleId() { return sampleId; }
    public void setSampleId(String sampleId) { this.sampleId = sampleId; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }
    public String getTone() { return tone; }
    public void setTone(String tone) { this.tone = tone; }
    public String getChapterTitle() { return chapterTitle; }
    public void setChapterTitle(String chapterTitle) { this.chapterTitle = chapterTitle; }
    public String getSceneTitle() { return sceneTitle; }
    public void setSceneTitle(String sceneTitle) { this.sceneTitle = sceneTitle; }
    public String getCharacterContext() { return characterContext; }
    public void setCharacterContext(String characterContext) { this.characterContext = characterContext; }
    public String getStyleContext() { return styleContext; }
    public void setStyleContext(String styleContext) { this.styleContext = styleContext; }
    public String getExpectedEvidenceLevel() { return expectedEvidenceLevel; }
    public void setExpectedEvidenceLevel(String expectedEvidenceLevel) { this.expectedEvidenceLevel = expectedEvidenceLevel; }
    public boolean isExpectedRequiresAiReview() { return expectedRequiresAiReview; }
    public void setExpectedRequiresAiReview(boolean expectedRequiresAiReview) { this.expectedRequiresAiReview = expectedRequiresAiReview; }
    public String getObservedEvidenceLevel() { return observedEvidenceLevel; }
    public void setObservedEvidenceLevel(String observedEvidenceLevel) { this.observedEvidenceLevel = observedEvidenceLevel; }
    public boolean isObservedRequiresAiReview() { return observedRequiresAiReview; }
    public void setObservedRequiresAiReview(boolean observedRequiresAiReview) { this.observedRequiresAiReview = observedRequiresAiReview; }
    public int getObservedRiskScore() { return observedRiskScore; }
    public void setObservedRiskScore(int observedRiskScore) { this.observedRiskScore = observedRiskScore; }
    public String getObservedMaxSeverity() { return observedMaxSeverity; }
    public void setObservedMaxSeverity(String observedMaxSeverity) { this.observedMaxSeverity = observedMaxSeverity; }
    public boolean isMatchesExpected() { return matchesExpected; }
    public void setMatchesExpected(boolean matchesExpected) { this.matchesExpected = matchesExpected; }
    public SlopReviewSampleStatus getStatus() { return status; }
    public void setStatus(SlopReviewSampleStatus status) { this.status = status; }
    public String getReviewerNote() { return reviewerNote; }
    public void setReviewerNote(String reviewerNote) { this.reviewerNote = reviewerNote; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void copyMutableFieldsFrom(SlopReviewSample source) {
        this.sourceType = source.sourceType;
        this.sourceRunId = source.sourceRunId;
        this.storyId = source.storyId;
        this.manuscriptId = source.manuscriptId;
        this.sceneId = source.sceneId;
        this.sampleId = source.sampleId;
        this.text = source.text;
        this.genre = source.genre;
        this.tone = source.tone;
        this.chapterTitle = source.chapterTitle;
        this.sceneTitle = source.sceneTitle;
        this.characterContext = source.characterContext;
        this.styleContext = source.styleContext;
        this.expectedEvidenceLevel = source.expectedEvidenceLevel;
        this.expectedRequiresAiReview = source.expectedRequiresAiReview;
        this.observedEvidenceLevel = source.observedEvidenceLevel;
        this.observedRequiresAiReview = source.observedRequiresAiReview;
        this.observedRiskScore = source.observedRiskScore;
        this.observedMaxSeverity = source.observedMaxSeverity;
        this.matchesExpected = source.matchesExpected;
        this.status = source.status;
        this.reviewerNote = source.reviewerNote;
        this.createdBy = source.createdBy;
        this.reviewedBy = source.reviewedBy;
        this.reviewedAt = source.reviewedAt;
    }
}
