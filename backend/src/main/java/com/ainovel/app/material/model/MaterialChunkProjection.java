package com.ainovel.app.material.model;

import jakarta.persistence.*;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "material_chunks")
public class MaterialChunkProjection {
    @Id @Column(length = 36) private String chunkId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "material_id") private Material material;
    @Column(name = "owner_user_id") private UUID ownerUserId;
    @Column(nullable = false, length = 32) private String status;
    private String title;
    @Lob @Column(nullable = false, columnDefinition = "text") private String text;
    @Lob @Column(columnDefinition = "text") private String tags;
    private int chunkSeq;
    @UpdateTimestamp private Instant updatedAt;
    public String getChunkId(){return chunkId;} public void setChunkId(String v){chunkId=v;}
    public Material getMaterial(){return material;} public void setMaterial(Material v){material=v;}
    public UUID getOwnerUserId(){return ownerUserId;} public void setOwnerUserId(UUID v){ownerUserId=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getText(){return text;} public void setText(String v){text=v;}
    public String getTags(){return tags;} public void setTags(String v){tags=v;}
    public int getChunkSeq(){return chunkSeq;} public void setChunkSeq(int v){chunkSeq=v;}
}
