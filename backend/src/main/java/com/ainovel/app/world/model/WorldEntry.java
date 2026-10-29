package com.ainovel.app.world.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "world_entries")
public class WorldEntry {
    @Id private UUID id;
    private UUID worldId;
    private String type;
    private String title;
    @Lob @Column(columnDefinition = "LONGTEXT")
    private String summary;
    @Lob @Column(columnDefinition = "LONGTEXT")
    private String content;
    @Lob @Column(columnDefinition = "LONGTEXT")
    private String aliasesJson;
    @Lob @Column(columnDefinition = "LONGTEXT")
    private String tagsJson;
    private String legacyModuleKey;
    private String legacyFieldKey;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getWorldId() { return worldId; }
    public void setWorldId(UUID value) { this.worldId = value; }
    public String getType() { return type; }
    public void setType(String value) { this.type = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { this.title = value; }
    public String getSummary() { return summary; }
    public void setSummary(String value) { this.summary = value; }
    public String getContent() { return content; }
    public void setContent(String value) { this.content = value; }
    public String getAliasesJson() { return aliasesJson; }
    public void setAliasesJson(String value) { this.aliasesJson = value; }
    public String getTagsJson() { return tagsJson; }
    public void setTagsJson(String value) { this.tagsJson = value; }
    public String getLegacyModuleKey() { return legacyModuleKey; }
    public void setLegacyModuleKey(String value) { this.legacyModuleKey = value; }
    public String getLegacyFieldKey() { return legacyFieldKey; }
    public void setLegacyFieldKey(String value) { this.legacyFieldKey = value; }
}
