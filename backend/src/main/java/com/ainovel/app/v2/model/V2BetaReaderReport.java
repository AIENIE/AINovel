package com.ainovel.app.v2.model;

import com.ainovel.app.story.model.Story;
import com.ainovel.app.user.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "beta_reader_reports")
public class V2BetaReaderReport {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id")
    private Story story;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;
    @Column(length = 32)
    private String scope;
    @Column(length = 100)
    private String scopeReference;
    @Column(nullable = false, length = 32)
    private String status;
    @Lob
    private String analysisJson;
    @Column(length = 800)
    private String summary;
    private int scoreOverall;
    private int scorePacing;
    private int scoreCharacters;
    private int scoreDialogue;
    private int scoreConsistency;
    private int scoreEngagement;
    private int tokenCost;
    @CreationTimestamp
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;

    public UUID getId() { return id; }
    public Story getStory() { return story; }
    public void setStory(Story story) { this.story = story; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getScopeReference() { return scopeReference; }
    public void setScopeReference(String scopeReference) { this.scopeReference = scopeReference; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAnalysisJson() { return analysisJson; }
    public void setAnalysisJson(String analysisJson) { this.analysisJson = analysisJson; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public int getScoreOverall() { return scoreOverall; }
    public void setScoreOverall(int scoreOverall) { this.scoreOverall = scoreOverall; }
    public int getScorePacing() { return scorePacing; }
    public void setScorePacing(int scorePacing) { this.scorePacing = scorePacing; }
    public int getScoreCharacters() { return scoreCharacters; }
    public void setScoreCharacters(int scoreCharacters) { this.scoreCharacters = scoreCharacters; }
    public int getScoreDialogue() { return scoreDialogue; }
    public void setScoreDialogue(int scoreDialogue) { this.scoreDialogue = scoreDialogue; }
    public int getScoreConsistency() { return scoreConsistency; }
    public void setScoreConsistency(int scoreConsistency) { this.scoreConsistency = scoreConsistency; }
    public int getScoreEngagement() { return scoreEngagement; }
    public void setScoreEngagement(int scoreEngagement) { this.scoreEngagement = scoreEngagement; }
    public int getTokenCost() { return tokenCost; }
    public void setTokenCost(int tokenCost) { this.tokenCost = tokenCost; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
