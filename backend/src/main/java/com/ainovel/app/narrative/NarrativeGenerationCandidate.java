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
    private UUID operationId;
    private Integer generationSequence;
    private Long manuscriptVersion;
    private Integer minHan;
    private Integer maxHan;
    private Integer actualHan;
    @Column(length=32) private String qualityStatus;
    @Column(length=32) private String sourceKind;
    @Lob private String metadataJson;
    @Lob private String requestJson;
    public String getRequestJson(){return requestJson;}
    public void setRequestJson(String v){requestJson=v;}
    private Long adoptedVersion;
    public UUID getOperationId(){return operationId;}
    public void setOperationId(UUID v){operationId=v;}
    public Integer getGenerationSequence(){return generationSequence;}
    public void setGenerationSequence(Integer v){generationSequence=v;}
    public Long getManuscriptVersion(){return manuscriptVersion;}
    public void setManuscriptVersion(Long v){manuscriptVersion=v;}
    public Integer getMinHan(){return minHan;}
    public void setMinHan(Integer v){minHan=v;}
    public Integer getMaxHan(){return maxHan;}
    public void setMaxHan(Integer v){maxHan=v;}
    public Integer getActualHan(){return actualHan;}
    public void setActualHan(Integer v){actualHan=v;}
    public String getQualityStatus(){return qualityStatus;}
    public void setQualityStatus(String v){qualityStatus=v;}
    public String getSourceKind(){return sourceKind;}
    public void setSourceKind(String v){sourceKind=v;}
    public String getMetadataJson(){return metadataJson;}
    public void setMetadataJson(String v){metadataJson=v;}
    public Long getAdoptedVersion(){return adoptedVersion;}
    public void setAdoptedVersion(Long v){adoptedVersion=v;}
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
