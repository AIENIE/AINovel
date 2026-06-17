package com.ainovel.app.v2.model;

import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.story.model.Story;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "entity_extractions")
public class V2EntityExtraction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id")
    private Story story;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manuscript_id")
    private Manuscript manuscript;
    @Column(nullable = false, length = 200)
    private String entityName;
    @Column(nullable = false, length = 50)
    private String entityType;
    @Lob
    private String attributesJson;
    @Lob
    private String sourceText;
    @Column(nullable = false)
    private double confidence;
    @Column(nullable = false)
    private boolean reviewed;
    @Column(length = 32)
    private String reviewAction;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_lorebook_id")
    private V2LorebookEntry linkedLorebook;
    @CreationTimestamp
    private Instant createdAt;

    public UUID getId() { return id; }
    public Story getStory() { return story; }
    public void setStory(Story story) { this.story = story; }
    public Manuscript getManuscript() { return manuscript; }
    public void setManuscript(Manuscript manuscript) { this.manuscript = manuscript; }
    public String getEntityName() { return entityName; }
    public void setEntityName(String entityName) { this.entityName = entityName; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public String getAttributesJson() { return attributesJson; }
    public void setAttributesJson(String attributesJson) { this.attributesJson = attributesJson; }
    public String getSourceText() { return sourceText; }
    public void setSourceText(String sourceText) { this.sourceText = sourceText; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public boolean isReviewed() { return reviewed; }
    public void setReviewed(boolean reviewed) { this.reviewed = reviewed; }
    public String getReviewAction() { return reviewAction; }
    public void setReviewAction(String reviewAction) { this.reviewAction = reviewAction; }
    public V2LorebookEntry getLinkedLorebook() { return linkedLorebook; }
    public void setLinkedLorebook(V2LorebookEntry linkedLorebook) { this.linkedLorebook = linkedLorebook; }
    public Instant getCreatedAt() { return createdAt; }
}
