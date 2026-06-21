package com.ainovel.app.admin.dto;

import java.util.List;
import java.util.Map;

public record SlopReviewSampleReportDto(
        long total,
        long reviewed,
        long pending,
        long approved,
        long rejected,
        long needsDiscussion,
        long matchedReviewed,
        long mismatchedReviewed,
        long highRiskReviewed,
        Map<String, Long> expectedEvidenceCounts,
        Map<String, Long> observedEvidenceCounts,
        Map<String, Map<String, Long>> evidenceMatrix,
        SlopReviewSampleAiReviewMatrixDto aiReviewMatrix,
        SlopReviewSamplePolicyDto currentPolicy,
        List<SlopReviewSampleMismatchDto> mismatchSamples
) {
}
