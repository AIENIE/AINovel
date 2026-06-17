package com.ainovel.app.v2.model;

import com.ainovel.app.story.model.Story;
import com.ainovel.app.user.User;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "writing_sessions")
public class V2WritingSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id")
    private Story story;
    private Instant startedAt;
    private Instant endedAt;
    @Column(nullable = false)
    private int wordsWritten;
    @Column(nullable = false)
    private int wordsDeleted;
    @Column(nullable = false)
    private int netWords;
    @Column(nullable = false)
    private int durationSeconds;
    @Lob
    private String chaptersEditedJson;

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Story getStory() { return story; }
    public void setStory(Story story) { this.story = story; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }
    public int getWordsWritten() { return wordsWritten; }
    public void setWordsWritten(int wordsWritten) { this.wordsWritten = wordsWritten; }
    public int getWordsDeleted() { return wordsDeleted; }
    public void setWordsDeleted(int wordsDeleted) { this.wordsDeleted = wordsDeleted; }
    public int getNetWords() { return netWords; }
    public void setNetWords(int netWords) { this.netWords = netWords; }
    public int getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }
    public String getChaptersEditedJson() { return chaptersEditedJson; }
    public void setChaptersEditedJson(String chaptersEditedJson) { this.chaptersEditedJson = chaptersEditedJson; }
}
