package com.ainovel.app.quality;

import com.ainovel.app.quality.dto.SlopDriftRunDto;
import com.ainovel.app.quality.model.SlopDriftRun;

public final class SlopDriftMapper {
    private SlopDriftMapper() {
    }

    public static SlopDriftRunDto toDto(SlopDriftRun run) {
        boolean assessed = run.getStatus() == SlopDriftStatus.COMPLETED;
        return new SlopDriftRunDto(
                run.getId(),
                run.getStoryId(),
                run.getManuscriptId(),
                run.getStatus().name(),
                assessed ? run.getOverallRiskScore() : null,
                assessed ? run.getRiskLabel() : null,
                run.getSafeClaim(),
                run.getSummary(),
                run.getTotalCharacters(),
                run.getWindowCount(),
                run.getSourceTextHash(),
                run.getWindowSummariesJson(),
                run.getMetricCurvesJson(),
                run.getDriftPointsJson(),
                run.getEvidenceItemsJson(),
                run.getAlternativeExplanationsJson(),
                run.getRewriteTasksJson(),
                run.getCreatedAt()
        );
    }
}
