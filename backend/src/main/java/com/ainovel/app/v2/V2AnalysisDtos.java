package com.ainovel.app.v2;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class V2AnalysisDtos {
    private V2AnalysisDtos() {
    }

    public record AnalysisSummaryResponse(
            String focus,
            List<String> highlights,
            List<String> risks
    ) {
    }

    public record AnalysisJobResponse(
            UUID id,
            UUID storyId,
            UUID userId,
            String jobType,
            String scope,
            String scopeReference,
            String status,
            Integer progress,
            String progressMessage,
            UUID resultReference,
            String errorMessage,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record AnalysisReportResponse(
            UUID id,
            UUID storyId,
            UUID userId,
            String scope,
            String scopeReference,
            String status,
            AnalysisSummaryResponse analysis,
            String summary,
            Integer scoreOverall,
            Integer scorePacing,
            Integer scoreCharacters,
            Integer scoreDialogue,
            Integer scoreConsistency,
            Integer scoreEngagement,
            Integer tokenCost,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record ContinuityEvidenceItem(
            Integer chapter,
            String note
    ) {
    }

    public record ContinuityIssueResponse(
            UUID id,
            UUID storyId,
            UUID reportId,
            String issueType,
            String severity,
            String description,
            List<ContinuityEvidenceItem> evidence,
            String suggestion,
            String status,
            Instant resolvedAt,
            Instant createdAt
    ) {
    }
}
