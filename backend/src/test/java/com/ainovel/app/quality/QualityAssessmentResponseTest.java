package com.ainovel.app.quality;

import com.ainovel.app.quality.model.SlopDriftRun;
import com.ainovel.app.quality.model.SlopQualityRun;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QualityAssessmentResponseTest {
    @Test
    void incompleteAndFailedReportsHideStoredPlaceholderAssessments() {
        var slop = new SlopQualityRun();
        slop.setStatus(SlopQualityStatus.ACCEPTED);
        slop.setMaxSeverity(SlopSeverity.MEDIUM);
        slop.setOverallRiskScore(45);
        for (String coverage : new String[]{"PENDING", "FAILED", "PARTIAL"}) {
            slop.setReviewContextJson("{\"coverage\":\"" + coverage + "\"}");
            assertNull(SlopQualityMapper.toDto(slop).overallRiskScore());
            assertNull(SlopQualityMapper.toDto(slop).maxSeverity());
        }
        slop.setReviewContextJson("{bad}");
        assertEquals("FAILED", SlopQualityMapper.toDto(slop).review().coverage());
        assertNull(SlopQualityMapper.toDto(slop).overallRiskScore());
        slop.setReviewContextJson(null);
        assertEquals(45, SlopQualityMapper.toDto(slop).overallRiskScore());
        slop.setStatus(SlopQualityStatus.DEGRADED);
        assertNull(SlopQualityMapper.toDto(slop).overallRiskScore());
        assertEquals(45, slop.getOverallRiskScore());

        var drift = new SlopDriftRun();
        drift.setOverallRiskScore(45);
        for (var status : new SlopDriftStatus[]{SlopDriftStatus.DEGRADED, SlopDriftStatus.INSUFFICIENT_TEXT}) {
            drift.setStatus(status);
            assertNull(SlopDriftMapper.toDto(drift).overallRiskScore());
            assertNull(SlopDriftMapper.toDto(drift).riskLabel());
        }
        drift.setStatus(SlopDriftStatus.COMPLETED);
        assertEquals(45, SlopDriftMapper.toDto(drift).overallRiskScore());
    }
}
