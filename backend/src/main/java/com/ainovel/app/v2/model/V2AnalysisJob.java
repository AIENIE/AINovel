package com.ainovel.app.v2.model;

import com.ainovel.app.story.model.Story;
import com.ainovel.app.user.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "analysis_jobs")
public class V2AnalysisJob {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id")
    private Story story;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;
    @Column(nullable = false, length = 50)
    private String jobType;
    @Column(length = 32)
    private String scope;
    @Column(length = 100)
    private String scopeReference;
    @Column(nullable = false, length = 32)
    private String status;
    private int progress;
    @Column(length = 500)
    private String progressMessage;
    private UUID resultReference;
    @Lob
    private String errorMessage;
    @CreationTimestamp
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;

    public UUID getId() { return id; }
    public Story getStory() { return story; }
    public void setStory(Story story) { this.story = story; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getJobType() { return jobType; }
    public void setJobType(String jobType) { this.jobType = jobType; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getScopeReference() { return scopeReference; }
    public void setScopeReference(String scopeReference) { this.scopeReference = scopeReference; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }
    public String getProgressMessage() { return progressMessage; }
    public void setProgressMessage(String progressMessage) { this.progressMessage = progressMessage; }
    public UUID getResultReference() { return resultReference; }
    public void setResultReference(UUID resultReference) { this.resultReference = resultReference; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
