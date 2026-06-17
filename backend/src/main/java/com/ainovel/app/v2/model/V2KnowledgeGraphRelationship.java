package com.ainovel.app.v2.model;

import com.ainovel.app.story.model.Story;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "knowledge_graph_relationships")
public class V2KnowledgeGraphRelationship {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id")
    private Story story;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_lorebook_id")
    private V2LorebookEntry source;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_lorebook_id")
    private V2LorebookEntry target;
    @Column(nullable = false, length = 80)
    private String relationType;
    @CreationTimestamp
    private Instant createdAt;

    public UUID getId() { return id; }
    public Story getStory() { return story; }
    public void setStory(Story story) { this.story = story; }
    public V2LorebookEntry getSource() { return source; }
    public void setSource(V2LorebookEntry source) { this.source = source; }
    public V2LorebookEntry getTarget() { return target; }
    public void setTarget(V2LorebookEntry target) { this.target = target; }
    public String getRelationType() { return relationType; }
    public void setRelationType(String relationType) { this.relationType = relationType; }
    public Instant getCreatedAt() { return createdAt; }
}
