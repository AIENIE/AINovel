package com.ainovel.app.world.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "world_relations")
public class WorldRelation {
    @Id private UUID id;
    private UUID worldId;
    private UUID sourceId;
    private UUID targetId;
    private String type;
    @Lob @Column(columnDefinition = "LONGTEXT")
    private String description;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getWorldId() { return worldId; }
    public void setWorldId(UUID value) { this.worldId = value; }
    public UUID getSourceId() { return sourceId; }
    public void setSourceId(UUID value) { this.sourceId = value; }
    public UUID getTargetId() { return targetId; }
    public void setTargetId(UUID value) { this.targetId = value; }
    public String getType() { return type; }
    public void setType(String value) { this.type = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { this.description = value; }
}
