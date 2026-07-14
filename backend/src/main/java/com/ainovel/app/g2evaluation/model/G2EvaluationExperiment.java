package com.ainovel.app.g2evaluation.model;

import com.ainovel.app.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "g2_evaluation_experiments")
public class G2EvaluationExperiment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 180)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private G2EvaluationStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(nullable = false)
    private int minimumVotes = 100;
    @Column(nullable = false)
    private int minimumSamplePairs = 20;
    @Column(nullable = false)
    private int minimumReviewers = 10;
    @Column(nullable = false)
    private double craftedWinRateTarget = 55d;

    @CreationTimestamp
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public G2EvaluationStatus getStatus() { return status; }
    public void setStatus(G2EvaluationStatus status) { this.status = status; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public int getMinimumVotes() { return minimumVotes; }
    public void setMinimumVotes(int minimumVotes) { this.minimumVotes = minimumVotes; }
    public int getMinimumSamplePairs() { return minimumSamplePairs; }
    public void setMinimumSamplePairs(int minimumSamplePairs) { this.minimumSamplePairs = minimumSamplePairs; }
    public int getMinimumReviewers() { return minimumReviewers; }
    public void setMinimumReviewers(int minimumReviewers) { this.minimumReviewers = minimumReviewers; }
    public double getCraftedWinRateTarget() { return craftedWinRateTarget; }
    public void setCraftedWinRateTarget(double craftedWinRateTarget) { this.craftedWinRateTarget = craftedWinRateTarget; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
