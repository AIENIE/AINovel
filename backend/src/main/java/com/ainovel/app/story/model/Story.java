package com.ainovel.app.story.model;

import com.ainovel.app.user.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stories")
@org.hibernate.annotations.DynamicUpdate
public class Story {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private String title;
    @Lob
    private String synopsis;
    private String genre;
    private String tone;
    private String status; // draft/published/archived
    private String worldId;
    private UUID worldVersionId;
    private long worldBindingRevision;
    public UUID getWorldVersionId() { return worldVersionId; }
    public void setWorldVersionId(UUID value) { worldVersionId = value; }
    public long getWorldBindingRevision() { return worldBindingRevision; }
    public void setWorldBindingRevision(long value) { worldBindingRevision = value; }

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    public Story() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSynopsis() { return synopsis; }
    public void setSynopsis(String synopsis) { this.synopsis = synopsis; }
    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }
    public String getTone() { return tone; }
    public void setTone(String tone) { this.tone = tone; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getWorldId() { return worldId; }
    public void setWorldId(String worldId) { this.worldId = worldId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
