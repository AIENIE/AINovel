package com.ainovel.app.g2evaluation.model;

import com.ainovel.app.user.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "g2_evaluation_votes", uniqueConstraints = @UniqueConstraint(
        name = "uk_g2_vote_sample_reviewer", columnNames = {"sample_id", "reviewer_id"}))
public class G2EvaluationVote {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "experiment_id", nullable = false)
    private G2EvaluationExperiment experiment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "sample_id", nullable = false)
    private G2EvaluationSample sample;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private G2EvaluationVoteChoice choice;
    @CreationTimestamp private Instant createdAt;

    public UUID getId() { return id; }
    public G2EvaluationExperiment getExperiment() { return experiment; }
    public void setExperiment(G2EvaluationExperiment experiment) { this.experiment = experiment; }
    public G2EvaluationSample getSample() { return sample; }
    public void setSample(G2EvaluationSample sample) { this.sample = sample; }
    public User getReviewer() { return reviewer; }
    public void setReviewer(User reviewer) { this.reviewer = reviewer; }
    public G2EvaluationVoteChoice getChoice() { return choice; }
    public void setChoice(G2EvaluationVoteChoice choice) { this.choice = choice; }
    public Instant getCreatedAt() { return createdAt; }
}
