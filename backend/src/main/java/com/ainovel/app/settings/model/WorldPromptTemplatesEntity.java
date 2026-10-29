package com.ainovel.app.settings.model;

import com.ainovel.app.user.User;
import jakarta.persistence.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "world_prompt_templates")
public class WorldPromptTemplatesEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Lob
    private String modulesJson;

    @Lob
    private String finalTemplatesJson;

    @Lob
    private String fieldRefine;

    @Lob @Column(columnDefinition = "LONGTEXT")
    private String actionTemplatesJson;
    public String getActionTemplatesJson() { return actionTemplatesJson; }
    public void setActionTemplatesJson(String value) { actionTemplatesJson = value; }

    @UpdateTimestamp
    private Instant updatedAt;

    public WorldPromptTemplatesEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getModulesJson() { return modulesJson; }
    public void setModulesJson(String modulesJson) { this.modulesJson = modulesJson; }
    public String getFinalTemplatesJson() { return finalTemplatesJson; }
    public void setFinalTemplatesJson(String finalTemplatesJson) { this.finalTemplatesJson = finalTemplatesJson; }
    public String getFieldRefine() { return fieldRefine; }
    public void setFieldRefine(String fieldRefine) { this.fieldRefine = fieldRefine; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
