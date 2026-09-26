package com.ainovel.app.narrative;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="narrative_generation_candidates")
public class NarrativeGenerationCandidate {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(nullable=false) private UUID manuscriptId;
    @Column(nullable=false) private UUID branchId;
    @Column(nullable=false) private UUID sceneId;
    @Lob @Column(nullable=false) private String content;
    @Lob @Column(nullable=false) private String stampJson;
    @Column(nullable=false,length=32) private String status;
    @Column(nullable=false) private Instant createdAt;
    public UUID getId(){return id;}
    public UUID getManuscriptId(){return manuscriptId;}
    public void setManuscriptId(UUID v){manuscriptId=v;}
    public UUID getBranchId(){return branchId;}
    public void setBranchId(UUID v){branchId=v;}
    public UUID getSceneId(){return sceneId;}
    public void setSceneId(UUID v){sceneId=v;}
    public String getContent(){return content;}
    public void setContent(String v){content=v;}
    public String getStampJson(){return stampJson;}
    public void setStampJson(String v){stampJson=v;}
    public String getStatus(){return status;}
    public void setStatus(String v){status=v;}
    public Instant getCreatedAt(){return createdAt;}
    public void setCreatedAt(Instant v){createdAt=v;}
}
