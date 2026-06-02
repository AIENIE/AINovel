package com.ainovel.app.quality.model;

import com.ainovel.app.quality.PlotQualityDimension;
import com.ainovel.app.quality.PlotQualitySeverity;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "plot_quality_issues", indexes = {
        @Index(name = "idx_plot_issue_run", columnList = "run_id"),
        @Index(name = "idx_plot_issue_dimension", columnList = "dimension")
})
public class PlotQualityIssue {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private PlotQualityRun run;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PlotQualityDimension dimension;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PlotQualitySeverity severity;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Column(length = 800)
    private String evidence;

    @Column(name = "why_it_matters", length = 800)
    private String whyItMatters;

    @Column(name = "minimal_fix", length = 800)
    private String minimalFix;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public PlotQualityRun getRun() { return run; }
    public void setRun(PlotQualityRun run) { this.run = run; }
    public PlotQualityDimension getDimension() { return dimension; }
    public void setDimension(PlotQualityDimension dimension) { this.dimension = dimension; }
    public PlotQualitySeverity getSeverity() { return severity; }
    public void setSeverity(PlotQualitySeverity severity) { this.severity = severity; }
    public int getRiskScore() { return riskScore; }
    public void setRiskScore(int riskScore) { this.riskScore = riskScore; }
    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }
    public String getWhyItMatters() { return whyItMatters; }
    public void setWhyItMatters(String whyItMatters) { this.whyItMatters = whyItMatters; }
    public String getMinimalFix() { return minimalFix; }
    public void setMinimalFix(String minimalFix) { this.minimalFix = minimalFix; }
    public Instant getCreatedAt() { return createdAt; }
}
