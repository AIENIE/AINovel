package com.ainovel.app.quality;

import com.ainovel.app.admin.dto.SlopReviewSampleImportRequest;
import com.ainovel.app.admin.dto.SlopReviewSampleImportResultDto;
import com.ainovel.app.admin.dto.SlopReviewSampleReportDto;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.quality.model.SlopReviewSample;
import com.ainovel.app.quality.repo.SlopQualityRunRepository;
import com.ainovel.app.quality.repo.SlopReviewSampleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlopReviewSamplePhase7ServiceTest {
    private SlopReviewSampleRepository sampleRepository;
    private SlopReviewSampleService service;

    @BeforeEach
    void setUp() {
        sampleRepository = mock(SlopReviewSampleRepository.class);
        service = new SlopReviewSampleService(
                sampleRepository,
                mock(SlopQualityRunRepository.class),
                mock(ManuscriptRepository.class),
                new LocalSlopHeuristics()
        );
    }

    @Test
    void reportShouldSummarizeReviewedSamplesAndEvidenceMatrix() {
        SlopReviewSample approvedMatch = sample("approved-match", "E1", false, "E1", false, 34, "LOW", true, SlopReviewSampleStatus.APPROVED);
        SlopReviewSample approvedMismatch = sample("approved-mismatch", "E2", true, "E1", false, 34, "LOW", false, SlopReviewSampleStatus.APPROVED);
        SlopReviewSample rejectedHigh = sample("rejected-high", "E4", true, "E4", true, 88, "HIGH", true, SlopReviewSampleStatus.REJECTED);
        SlopReviewSample pending = sample("pending", "E1", false, "E1", false, 34, "LOW", true, SlopReviewSampleStatus.PENDING);
        when(sampleRepository.findAll()).thenReturn(List.of(approvedMatch, approvedMismatch, rejectedHigh, pending));

        SlopReviewSampleReportDto report = service.report();

        assertEquals(4, report.total());
        assertEquals(3, report.reviewed());
        assertEquals(2, report.approved());
        assertEquals(1, report.rejected());
        assertEquals(1, report.pending());
        assertEquals(1, report.mismatchedReviewed());
        assertEquals(1, report.highRiskReviewed());
        assertEquals(1, report.expectedEvidenceCounts().get("E2"));
        assertEquals(1, report.evidenceMatrix().get("E2").get("E1"));
        assertEquals(1, report.aiReviewMatrix().expectedTrueObservedFalse());
        assertEquals(55, report.currentPolicy().aiReviewRiskThreshold());
        assertEquals(List.of("approved-mismatch"), report.mismatchSamples().stream().map(item -> item.sampleId()).toList());
    }

    @Test
    void importSamplesShouldCreateJsonlRowsAndSkipDuplicateSampleIds() {
        String jsonl = """
                {"sampleId":"P7-001","text":"嘴角微微上扬，空气仿佛凝固。","expectedEvidenceLevel":"E1","expectedRequiresAiReview":false,"genre":"悬疑","tone":"冷峻"}
                {"sampleId":"P7-001","text":"重复样本","expectedEvidenceLevel":"E1","expectedRequiresAiReview":false}
                {"sampleId":"P7-002","text":"以下是修改版：她推开门。","expectedEvidenceLevel":"E4","expectedRequiresAiReview":true}
                """;
        when(sampleRepository.existsBySampleId("P7-001")).thenReturn(false);
        when(sampleRepository.existsBySampleId("P7-002")).thenReturn(false);
        when(sampleRepository.save(any(SlopReviewSample.class))).thenAnswer(invocation -> {
            SlopReviewSample sample = invocation.getArgument(0);
            sample.setId(UUID.randomUUID());
            return sample;
        });

        SlopReviewSampleImportResultDto result = service.importSamples(new SlopReviewSampleImportRequest(jsonl), "ops-admin");

        assertEquals(2, result.imported());
        assertEquals(1, result.skipped());
        assertEquals(0, result.errors().size());
        assertEquals(List.of("P7-001", "P7-002"), result.samples().stream().map(item -> item.sampleId()).toList());
        verify(sampleRepository, times(2)).save(any(SlopReviewSample.class));
    }

    private SlopReviewSample sample(
            String sampleId,
            String expectedEvidenceLevel,
            boolean expectedRequiresAiReview,
            String observedEvidenceLevel,
            boolean observedRequiresAiReview,
            int observedRiskScore,
            String observedMaxSeverity,
            boolean matchesExpected,
            SlopReviewSampleStatus status
    ) {
        SlopReviewSample sample = new SlopReviewSample();
        sample.setId(UUID.randomUUID());
        sample.setSourceType("MANUAL");
        sample.setSampleId(sampleId);
        sample.setText("样本文本 " + sampleId);
        sample.setExpectedEvidenceLevel(expectedEvidenceLevel);
        sample.setExpectedRequiresAiReview(expectedRequiresAiReview);
        sample.setObservedEvidenceLevel(observedEvidenceLevel);
        sample.setObservedRequiresAiReview(observedRequiresAiReview);
        sample.setObservedRiskScore(observedRiskScore);
        sample.setObservedMaxSeverity(observedMaxSeverity);
        sample.setMatchesExpected(matchesExpected);
        sample.setStatus(status);
        return sample;
    }
}
