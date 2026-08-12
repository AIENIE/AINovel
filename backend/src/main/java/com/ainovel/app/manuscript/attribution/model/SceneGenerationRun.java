package com.ainovel.app.manuscript.attribution.model;

import com.ainovel.app.manuscript.attribution.SceneAttributionStatus;
import com.ainovel.app.manuscript.attribution.SceneGenerationRunStatus;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scene_generation_runs", indexes = {
        @Index(name = "idx_scene_generation_run_scene", columnList = "manuscript_id,scene_id,created_at"),
        @Index(name = "idx_scene_generation_run_active", columnList = "manuscript_id,scene_id,status"),
        @Index(name = "idx_scene_generation_run_version", columnList = "generation_version_id")
})
public class SceneGenerationRun {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manuscript_id", nullable = false)
    private Manuscript manuscript;

    @Column(name = "scene_id", nullable = false)
    private UUID sceneId;

    @Column(name = "generation_version_id", nullable = false)
    private UUID generationVersionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "previous_run_id")
    private UUID previousRunId;

    @Column(name = "superseded_by_run_id")
    private UUID supersededByRunId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SceneGenerationRunStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "attribution_status", nullable = false, length = 32)
    private SceneAttributionStatus attributionStatus;

    @Column(nullable = false, length = 16)
    private String mode;

    @Column(name = "model_key", nullable = false, length = 120)
    private String modelKey;

    @Column(name = "prompt_version", nullable = false, length = 120)
    private String promptVersion;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "context_hash", length = 64)
    private String contextHash;

    @Column(name = "prompt_hash", nullable = false, length = 64)
    private String promptHash;

    @Lob
    @Column(name = "context_manifest_json")
    private String contextManifestJson;

    @Column(name = "generated_content_hash", nullable = false, length = 64)
    private String generatedContentHash;

    @Column(name = "current_content_hash", length = 64)
    private String currentContentHash;

    @Column(name = "generated_characters", nullable = false)
    private int generatedCharacters;

    @Column(name = "current_characters")
    private Integer currentCharacters;

    @Column(name = "retained_characters")
    private Integer retainedCharacters;

    @Column(name = "added_characters")
    private Integer addedCharacters;

    @Column(name = "deleted_characters")
    private Integer deletedCharacters;

    @Column(name = "retention_rate")
    private Double retentionRate;

    @Column(name = "exact_match")
    private Boolean exactMatch;

    @Lob
    @Column(name = "diff_json")
    private String diffJson;

    @Lob
    @Column(name = "tags_json")
    private String tagsJson;

    @Column(length = 500)
    private String note;

    @Column(name = "preference_confirmed", nullable = false)
    private boolean preferenceConfirmed;

    @Column(name = "preference_confirmed_at")
    private Instant preferenceConfirmedAt;

    @Column(name = "first_edited_at")
    private Instant firstEditedAt;

    @Column(name = "last_edited_at")
    private Instant lastEditedAt;

    @Column(name = "recomputed_at")
    private Instant recomputedAt;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Manuscript getManuscript() { return manuscript; }
    public void setManuscript(Manuscript manuscript) { this.manuscript = manuscript; }
    public UUID getSceneId() { return sceneId; }
    public void setSceneId(UUID sceneId) { this.sceneId = sceneId; }
    public UUID getGenerationVersionId() { return generationVersionId; }
    public void setGenerationVersionId(UUID generationVersionId) { this.generationVersionId = generationVersionId; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public UUID getPreviousRunId() { return previousRunId; }
    public void setPreviousRunId(UUID previousRunId) { this.previousRunId = previousRunId; }
    public UUID getSupersededByRunId() { return supersededByRunId; }
    public void setSupersededByRunId(UUID supersededByRunId) { this.supersededByRunId = supersededByRunId; }
    public SceneGenerationRunStatus getStatus() { return status; }
    public void setStatus(SceneGenerationRunStatus status) { this.status = status; }
    public SceneAttributionStatus getAttributionStatus() { return attributionStatus; }
    public void setAttributionStatus(SceneAttributionStatus attributionStatus) { this.attributionStatus = attributionStatus; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getModelKey() { return modelKey; }
    public void setModelKey(String modelKey) { this.modelKey = modelKey; }
    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public String getContextHash() { return contextHash; }
    public void setContextHash(String contextHash) { this.contextHash = contextHash; }
    public String getPromptHash() { return promptHash; }
    public void setPromptHash(String promptHash) { this.promptHash = promptHash; }
    public String getContextManifestJson() { return contextManifestJson; }
    public void setContextManifestJson(String contextManifestJson) { this.contextManifestJson = contextManifestJson; }
    public String getGeneratedContentHash() { return generatedContentHash; }
    public void setGeneratedContentHash(String generatedContentHash) { this.generatedContentHash = generatedContentHash; }
    public String getCurrentContentHash() { return currentContentHash; }
    public void setCurrentContentHash(String currentContentHash) { this.currentContentHash = currentContentHash; }
    public int getGeneratedCharacters() { return generatedCharacters; }
    public void setGeneratedCharacters(int generatedCharacters) { this.generatedCharacters = generatedCharacters; }
    public Integer getCurrentCharacters() { return currentCharacters; }
    public void setCurrentCharacters(Integer currentCharacters) { this.currentCharacters = currentCharacters; }
    public Integer getRetainedCharacters() { return retainedCharacters; }
    public void setRetainedCharacters(Integer retainedCharacters) { this.retainedCharacters = retainedCharacters; }
    public Integer getAddedCharacters() { return addedCharacters; }
    public void setAddedCharacters(Integer addedCharacters) { this.addedCharacters = addedCharacters; }
    public Integer getDeletedCharacters() { return deletedCharacters; }
    public void setDeletedCharacters(Integer deletedCharacters) { this.deletedCharacters = deletedCharacters; }
    public Double getRetentionRate() { return retentionRate; }
    public void setRetentionRate(Double retentionRate) { this.retentionRate = retentionRate; }
    public Boolean getExactMatch() { return exactMatch; }
    public void setExactMatch(Boolean exactMatch) { this.exactMatch = exactMatch; }
    public String getDiffJson() { return diffJson; }
    public void setDiffJson(String diffJson) { this.diffJson = diffJson; }
    public String getTagsJson() { return tagsJson; }
    public void setTagsJson(String tagsJson) { this.tagsJson = tagsJson; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public boolean isPreferenceConfirmed() { return preferenceConfirmed; }
    public void setPreferenceConfirmed(boolean preferenceConfirmed) { this.preferenceConfirmed = preferenceConfirmed; }
    public Instant getPreferenceConfirmedAt() { return preferenceConfirmedAt; }
    public void setPreferenceConfirmedAt(Instant preferenceConfirmedAt) { this.preferenceConfirmedAt = preferenceConfirmedAt; }
    public Instant getFirstEditedAt() { return firstEditedAt; }
    public void setFirstEditedAt(Instant firstEditedAt) { this.firstEditedAt = firstEditedAt; }
    public Instant getLastEditedAt() { return lastEditedAt; }
    public void setLastEditedAt(Instant lastEditedAt) { this.lastEditedAt = lastEditedAt; }
    public Instant getRecomputedAt() { return recomputedAt; }
    public void setRecomputedAt(Instant recomputedAt) { this.recomputedAt = recomputedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
