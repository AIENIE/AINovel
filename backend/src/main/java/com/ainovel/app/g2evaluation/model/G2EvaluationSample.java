package com.ainovel.app.g2evaluation.model;

import com.ainovel.app.user.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "g2_evaluation_samples", uniqueConstraints = @UniqueConstraint(
        name = "uk_g2_sample_source", columnNames = {"experiment_id", "author_id", "manuscript_id", "scene_id"}))
public class G2EvaluationSample {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "experiment_id", nullable = false)
    private G2EvaluationExperiment experiment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "author_id", nullable = false)
    private User author;
    @Column(nullable = false) private UUID manuscriptId;
    @Column(nullable = false) private UUID sceneId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private G2EvaluationSampleStatus status;
    @Lob @Column(name = "fast_text", columnDefinition = "mediumtext") private String fastText;
    @Lob @Column(name = "crafted_text", columnDefinition = "mediumtext") private String craftedText;
    @Column(length = 500) private String failureMessage;
    private Instant refundedAt;
    @CreationTimestamp private Instant createdAt;
    @UpdateTimestamp private Instant updatedAt;

    public UUID getId() { return id; }
    public G2EvaluationExperiment getExperiment() { return experiment; }
    public void setExperiment(G2EvaluationExperiment experiment) { this.experiment = experiment; }
    public User getAuthor() { return author; }
    public void setAuthor(User author) { this.author = author; }
    public UUID getManuscriptId() { return manuscriptId; }
    public void setManuscriptId(UUID manuscriptId) { this.manuscriptId = manuscriptId; }
    public UUID getSceneId() { return sceneId; }
    public void setSceneId(UUID sceneId) { this.sceneId = sceneId; }
    public G2EvaluationSampleStatus getStatus() { return status; }
    public void setStatus(G2EvaluationSampleStatus status) { this.status = status; }
    public String getFastText() { return fastText; }
    public void setFastText(String fastText) { this.fastText = fastText; }
    public String getCraftedText() { return craftedText; }
    public void setCraftedText(String craftedText) { this.craftedText = craftedText; }
    public String getFailureMessage() { return failureMessage; }
    public void setFailureMessage(String failureMessage) { this.failureMessage = failureMessage; }
    public Instant getRefundedAt() { return refundedAt; }
    public void setRefundedAt(Instant refundedAt) { this.refundedAt = refundedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
