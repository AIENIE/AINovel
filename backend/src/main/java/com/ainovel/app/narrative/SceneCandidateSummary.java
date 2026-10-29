package com.ainovel.app.narrative;
import java.time.Instant;
import java.util.UUID;

/** Closed projection deliberately excludes body and metadata LOBs. */
public interface SceneCandidateSummary {
    UUID getId(); UUID getOperationId(); UUID getBranchId();
    Integer getGenerationSequence(); Long getManuscriptVersion();
    Integer getMinHan(); Integer getMaxHan(); Integer getActualHan();
    String getStatus(); String getQualityStatus(); String getSourceKind(); Instant getCreatedAt();
}
