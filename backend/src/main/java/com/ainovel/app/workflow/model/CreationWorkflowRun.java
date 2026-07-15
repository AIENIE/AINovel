package com.ainovel.app.workflow.model;

import com.ainovel.app.story.model.Outline;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.user.User;
import com.ainovel.app.world.model.World;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "creation_workflow_runs")
public class CreationWorkflowRun {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "story_id")
    private Story story;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "world_id")
    private World world;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "outline_id")
    private Outline outline;

    private UUID activeJobId;
    @Column(nullable = false, length = 64)
    private String templateKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private GuidedCreationStep currentStep;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private CreationWorkflowStatus status;
    @Column(nullable = false, length = 1000)
    private String seedIdea;
    @Column(length = 100)
    private String genre;
    @Column(length = 100)
    private String tone;
    @Column(nullable = false)
    private int targetChapterCount = 6;
    @Column(nullable = false)
    private boolean autoRun;
    @Lob @Column(nullable = false)
    private String stepsJson = "{}";
    @Column(length = 500)
    private String errorMessage;
    private Instant completedAt;
    @CreationTimestamp private Instant createdAt;
    @UpdateTimestamp private Instant updatedAt;
    @Version private long version;

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Story getStory() { return story; }
    public void setStory(Story story) { this.story = story; }
    public World getWorld() { return world; }
    public void setWorld(World world) { this.world = world; }
    public Outline getOutline() { return outline; }
    public void setOutline(Outline outline) { this.outline = outline; }
    public UUID getActiveJobId() { return activeJobId; }
    public void setActiveJobId(UUID activeJobId) { this.activeJobId = activeJobId; }
    public String getTemplateKey() { return templateKey; }
    public void setTemplateKey(String templateKey) { this.templateKey = templateKey; }
    public GuidedCreationStep getCurrentStep() { return currentStep; }
    public void setCurrentStep(GuidedCreationStep currentStep) { this.currentStep = currentStep; }
    public CreationWorkflowStatus getStatus() { return status; }
    public void setStatus(CreationWorkflowStatus status) { this.status = status; }
    public String getSeedIdea() { return seedIdea; }
    public void setSeedIdea(String seedIdea) { this.seedIdea = seedIdea; }
    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }
    public String getTone() { return tone; }
    public void setTone(String tone) { this.tone = tone; }
    public int getTargetChapterCount() { return targetChapterCount; }
    public void setTargetChapterCount(int targetChapterCount) { this.targetChapterCount = targetChapterCount; }
    public boolean isAutoRun() { return autoRun; }
    public void setAutoRun(boolean autoRun) { this.autoRun = autoRun; }
    public String getStepsJson() { return stepsJson; }
    public void setStepsJson(String stepsJson) { this.stepsJson = stepsJson; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
