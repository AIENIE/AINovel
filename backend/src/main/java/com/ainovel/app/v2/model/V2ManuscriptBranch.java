package com.ainovel.app.v2.model;

import com.ainovel.app.manuscript.model.Manuscript;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "manuscript_branches")
public class V2ManuscriptBranch {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manuscript_id")
    private Manuscript manuscript;
    @Column(nullable = false, length = 100)
    private String name;
    @Lob
    private String description;
    private UUID sourceVersionId;
    @Column(nullable = false, length = 16)
    private String status;
    @Column(name = "is_main", nullable = false)
    private boolean main;
    @CreationTimestamp
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;

    public UUID getId() { return id; }
    public Manuscript getManuscript() { return manuscript; }
    public void setManuscript(Manuscript manuscript) { this.manuscript = manuscript; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public UUID getSourceVersionId() { return sourceVersionId; }
    public void setSourceVersionId(UUID sourceVersionId) { this.sourceVersionId = sourceVersionId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isMain() { return main; }
    public void setMain(boolean main) { this.main = main; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
