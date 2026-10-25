package com.ainovel.app.world.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name="story_world_eras",uniqueConstraints=@UniqueConstraint(columnNames={"story_id","world_id"}))
public class StoryWorldEras {
    @Id private UUID id;
    @Column(name="story_id",nullable=false) private UUID storyId;
    @Column(name="world_id",nullable=false) private UUID worldId;
    private UUID reviewedVersionId;
    @Column(nullable=false) private long revision;
    @Lob @Column(nullable=false,columnDefinition="LONGTEXT") private String eraIdsJson;
    protected StoryWorldEras() {}
    public StoryWorldEras(UUID storyId,UUID worldId){this.id=UUID.randomUUID();this.storyId=storyId;this.worldId=worldId;this.eraIdsJson="[]";}
    public long getRevision(){return revision;}
    public UUID getReviewedVersionId(){return reviewedVersionId;}
    public String getEraIdsJson(){return eraIdsJson;}
    public void review(UUID versionId,String ids){reviewedVersionId=versionId;eraIdsJson=ids;revision++;}
}
