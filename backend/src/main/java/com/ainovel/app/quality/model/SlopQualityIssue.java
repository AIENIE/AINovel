package com.ainovel.app.quality.model;

import com.ainovel.app.quality.SlopDimension;
import com.ainovel.app.quality.SlopSeverity;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "slop_quality_issues", indexes = {
        @Index(name = "idx_slop_issue_run", columnList = "run_id"),
        @Index(name = "idx_slop_issue_dimension", columnList = "dimension")
})
public class SlopQualityIssue {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private SlopQualityRun run;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SlopDimension dimension;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SlopSeverity severity;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Column(length = 600)
    private String evidence;

    @Column(name = "why_it_matters", length = 800)
    private String whyItMatters;

    @Column(name = "minimal_fix", length = 800)
    private String minimalFix;

    @Column(name = "char_start")
    private Integer charStart;

    @Column(name = "char_end")
    private Integer charEnd;

    @Column(length = 800)
    private String quote;

    @Column(length = 80)
    private String module;

    @Column(name = "pattern_id", length = 80)
    private String patternId;

    @Column(name = "issue_type", length = 80)
    private String issueType;

    @Column(name = "evidence_level", length = 8)
    private String evidenceLevel;

    @Column(name = "alternative_explanations_json", columnDefinition = "longtext")
    private String alternativeExplanationsJson;

    @Column(name = "repair_hint", length = 800)
    private String repairHint;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public SlopQualityRun getRun() { return run; }
    public void setRun(SlopQualityRun run) { this.run = run; }
    public SlopDimension getDimension() { return dimension; }
    public void setDimension(SlopDimension dimension) { this.dimension = dimension; }
    public SlopSeverity getSeverity() { return severity; }
    public void setSeverity(SlopSeverity severity) { this.severity = severity; }
    public int getRiskScore() { return riskScore; }
    public void setRiskScore(int riskScore) { this.riskScore = riskScore; }
    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }
    public String getWhyItMatters() { return whyItMatters; }
    public void setWhyItMatters(String whyItMatters) { this.whyItMatters = whyItMatters; }
    public String getMinimalFix() { return minimalFix; }
    public void setMinimalFix(String minimalFix) { this.minimalFix = minimalFix; }
    public Integer getCharStart() { return charStart; }
    public void setCharStart(Integer charStart) { this.charStart = charStart; }
    public Integer getCharEnd() { return charEnd; }
    public void setCharEnd(Integer charEnd) { this.charEnd = charEnd; }
    public String getQuote() { return quote; }
    public void setQuote(String quote) { this.quote = quote; }
    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    public String getPatternId() { return patternId; }
    public void setPatternId(String patternId) { this.patternId = patternId; }
    public String getIssueType() { return issueType; }
    public void setIssueType(String issueType) { this.issueType = issueType; }
    public String getEvidenceLevel() { return evidenceLevel; }
    public void setEvidenceLevel(String evidenceLevel) { this.evidenceLevel = evidenceLevel; }
    public String getAlternativeExplanationsJson() { return alternativeExplanationsJson; }
    public void setAlternativeExplanationsJson(String alternativeExplanationsJson) { this.alternativeExplanationsJson = alternativeExplanationsJson; }
    public String getRepairHint() { return repairHint; }
    public void setRepairHint(String repairHint) { this.repairHint = repairHint; }
    public Instant getCreatedAt() { return createdAt; }
}
