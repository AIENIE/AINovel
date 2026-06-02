package com.ainovel.app.quality;

import com.ainovel.app.quality.dto.PlotQualityIssueDto;
import com.ainovel.app.quality.dto.PlotQualityRunDto;
import com.ainovel.app.quality.model.PlotQualityIssue;
import com.ainovel.app.quality.model.PlotQualityRun;

public final class PlotQualityMapper {
    private PlotQualityMapper() {
    }

    public static PlotQualityRunDto toDto(PlotQualityRun run) {
        return new PlotQualityRunDto(
                run.getId(),
                run.getStoryId(),
                run.getManuscriptId(),
                run.getSceneId(),
                run.getChapterTitle(),
                run.getSceneTitle(),
                run.getChapterOrder(),
                run.getSceneOrder(),
                run.getStatus().name(),
                run.getMaxSeverity().name(),
                run.getOverallRiskScore(),
                run.getSummary(),
                run.getRewritePlanJson(),
                run.getSurgicalFixesJson(),
                run.getRevisionCandidateText(),
                run.isRevisionApplied(),
                run.getRevisionAppliedAt(),
                run.getCreatedAt(),
                run.getIssues().stream().map(PlotQualityMapper::toIssueDto).toList()
        );
    }

    private static PlotQualityIssueDto toIssueDto(PlotQualityIssue issue) {
        return new PlotQualityIssueDto(
                issue.getId(),
                issue.getDimension().name(),
                issue.getSeverity().name(),
                issue.getRiskScore(),
                issue.getEvidence(),
                issue.getWhyItMatters(),
                issue.getMinimalFix()
        );
    }
}
