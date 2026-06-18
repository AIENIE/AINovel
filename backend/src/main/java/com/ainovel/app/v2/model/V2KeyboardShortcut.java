package com.ainovel.app.v2.model;

import com.ainovel.app.user.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "keyboard_shortcuts", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "action"}))
public class V2KeyboardShortcut {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;
    @Column(nullable = false, length = 80)
    private String action;
    @Column(nullable = false, length = 50)
    private String shortcut;
    @Column(name = "is_custom", nullable = false)
    private boolean custom;
    @CreationTimestamp
    private Instant createdAt;

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getShortcut() { return shortcut; }
    public void setShortcut(String shortcut) { this.shortcut = shortcut; }
    public boolean isCustom() { return custom; }
    public void setCustom(boolean custom) { this.custom = custom; }
    public Instant getCreatedAt() { return createdAt; }
}
