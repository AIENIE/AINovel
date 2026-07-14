package com.ainovel.app.g2evaluation.model;

import com.ainovel.app.user.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "g2_evaluation_invites", uniqueConstraints = @UniqueConstraint(
        name = "uk_g2_invite_experiment_reviewer", columnNames = {"experiment_id", "reviewer_id"}))
public class G2EvaluationInvite {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "experiment_id", nullable = false)
    private G2EvaluationExperiment experiment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private G2EvaluationInviteStatus status;
    @CreationTimestamp private Instant createdAt;
    private Instant acceptedAt;

    public UUID getId() { return id; }
    public G2EvaluationExperiment getExperiment() { return experiment; }
    public void setExperiment(G2EvaluationExperiment experiment) { this.experiment = experiment; }
    public User getReviewer() { return reviewer; }
    public void setReviewer(User reviewer) { this.reviewer = reviewer; }
    public G2EvaluationInviteStatus getStatus() { return status; }
    public void setStatus(G2EvaluationInviteStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(Instant acceptedAt) { this.acceptedAt = acceptedAt; }
}
