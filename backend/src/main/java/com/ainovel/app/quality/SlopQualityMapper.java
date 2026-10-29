package com.ainovel.app.quality;

import com.ainovel.app.quality.dto.SlopQualityIssueDto;
import com.ainovel.app.quality.dto.SlopQualityRunDto;
import com.ainovel.app.quality.model.SlopQualityIssue;
import com.ainovel.app.quality.model.SlopQualityRun;

public final class SlopQualityMapper {
    private SlopQualityMapper() {
    }

    public static SlopQualityRunDto toDto(SlopQualityRun run) {
        var review = SlopEvidence.view(run.getReviewContextJson());
        boolean assessed = run.getStatus() != SlopQualityStatus.DEGRADED
                && (review == null || "COMPLETE".equals(review.coverage()));
        return new SlopQualityRunDto(
                run.getId(),
                run.getStoryId(),
                run.getManuscriptId(),
                run.getSceneId(),
                run.getStatus().name(),
                assessed ? run.getMaxSeverity().name() : null,
                assessed ? run.getOverallRiskScore() : null,
                run.isRevised(),
                run.getRevisionCount(),
                run.getSummary(),
                run.getAnalysisMode(),
                assessed ? run.getRiskLabel() : null,
                run.getEvidenceLevel(),
                run.getSafeClaim(),
                run.getModuleScoresJson(),
                run.getAlternativeExplanationsJson(),
                run.getRevisionPrioritiesJson(),
                run.getRewriteTasksJson(),
                run.getCreatedAt(),
                run.getIssues().stream().map(SlopQualityMapper::toIssueDto).toList(),
                review
        );
    }

    private static SlopQualityIssueDto toIssueDto(SlopQualityIssue issue) {
        return new SlopQualityIssueDto(
                issue.getId(),
                issue.getDimension().name(),
                issue.getSeverity().name(),
                issue.getRiskScore(),
                issue.getEvidence(),
                issue.getWhyItMatters(),
                issue.getMinimalFix(),
                issue.getCharStart(),
                issue.getCharEnd(),
                issue.getQuote(),
                issue.getModule(),
                issue.getPatternId(),
                issue.getIssueType(),
                issue.getEvidenceLevel(),
                issue.getAlternativeExplanationsJson(),
                issue.getRepairHint()
        );
    }
}
