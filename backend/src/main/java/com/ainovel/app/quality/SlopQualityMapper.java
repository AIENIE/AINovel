package com.ainovel.app.quality;

import com.ainovel.app.quality.dto.SlopQualityIssueDto;
import com.ainovel.app.quality.dto.SlopQualityRunDto;
import com.ainovel.app.quality.model.SlopQualityIssue;
import com.ainovel.app.quality.model.SlopQualityRun;

public final class SlopQualityMapper {
    private SlopQualityMapper() {
    }

    public static SlopQualityRunDto toDto(SlopQualityRun run) {
        return new SlopQualityRunDto(
                run.getId(),
                run.getStoryId(),
                run.getManuscriptId(),
                run.getSceneId(),
                run.getStatus().name(),
                run.getMaxSeverity().name(),
                run.getOverallRiskScore(),
                run.isRevised(),
                run.getRevisionCount(),
                run.getSummary(),
                run.getAnalysisMode(),
                run.getRiskLabel(),
                run.getEvidenceLevel(),
                run.getSafeClaim(),
                run.getModuleScoresJson(),
                run.getAlternativeExplanationsJson(),
                run.getRevisionPrioritiesJson(),
                run.getRewriteTasksJson(),
                run.getCreatedAt(),
                run.getIssues().stream().map(SlopQualityMapper::toIssueDto).toList()
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
