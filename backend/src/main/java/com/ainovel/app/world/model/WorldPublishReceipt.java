package com.ainovel.app.world.model;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name="world_publish_receipts")
@org.hibernate.annotations.Immutable
public class WorldPublishReceipt {
    @Id private UUID id;
    private UUID worldId;
    private UUID versionId;
    private String requestKey;
    private String requestHash;
    public WorldPublishReceipt() {}
    public WorldPublishReceipt(UUID worldId,UUID versionId,String key,String hash) {
        this.id=UUID.randomUUID();this.worldId=worldId;this.versionId=versionId;this.requestKey=key;this.requestHash=hash;
    }
    public UUID getId(){return id;}
    public UUID getVersionId(){return versionId;}
    public String getRequestHash(){return requestHash;}
}
