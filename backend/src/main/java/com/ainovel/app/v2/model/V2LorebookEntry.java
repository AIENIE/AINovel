package com.ainovel.app.v2.model;

import com.ainovel.app.story.model.Story;
import com.ainovel.app.user.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lorebook_entries")
public class V2LorebookEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id")
    private Story story;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 200)
    private String entryKey;
    @Column(nullable = false, length = 200)
    private String displayName;
    @Column(nullable = false, length = 50)
    private String category;
    @Lob
    private String content;
    @Lob
    private String keywordsJson;
    @Column(nullable = false)
    private int priority;
    @Column(nullable = false)
    private boolean enabled = true;
    @Column(length = 32)
    private String insertionPosition;
    @Column(nullable = false)
    private int tokenBudget = 500;
    @CreationTimestamp
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;

    public UUID getId() { return id; }
    public Story getStory() { return story; }
    public void setStory(Story story) { this.story = story; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getEntryKey() { return entryKey; }
    public void setEntryKey(String entryKey) { this.entryKey = entryKey; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getKeywordsJson() { return keywordsJson; }
    public void setKeywordsJson(String keywordsJson) { this.keywordsJson = keywordsJson; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getInsertionPosition() { return insertionPosition; }
    public void setInsertionPosition(String insertionPosition) { this.insertionPosition = insertionPosition; }
    public int getTokenBudget() { return tokenBudget; }
    public void setTokenBudget(int tokenBudget) { this.tokenBudget = tokenBudget; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
