package com.ainovel.app.v2.model;

import com.ainovel.app.story.model.Story;
import com.ainovel.app.user.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "writing_goals")
public class V2WritingGoal {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "story_id")
    private Story story;
    @Column(nullable = false, length = 32)
    private String goalType;
    @Column(nullable = false)
    private int targetValue;
    @Column(nullable = false)
    private int currentValue;
    private LocalDate deadline;
    @Column(nullable = false, length = 16)
    private String status;
    @CreationTimestamp
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Story getStory() { return story; }
    public void setStory(Story story) { this.story = story; }
    public String getGoalType() { return goalType; }
    public void setGoalType(String goalType) { this.goalType = goalType; }
    public int getTargetValue() { return targetValue; }
    public void setTargetValue(int targetValue) { this.targetValue = targetValue; }
    public int getCurrentValue() { return currentValue; }
    public void setCurrentValue(int currentValue) { this.currentValue = currentValue; }
    public LocalDate getDeadline() { return deadline; }
    public void setDeadline(LocalDate deadline) { this.deadline = deadline; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
