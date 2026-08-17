package com.ainovel.app.v2;

import com.ainovel.app.common.BusinessException;
import com.ainovel.app.v2.model.V2AnalysisJob;
import com.ainovel.app.v2.model.V2BetaReaderReport;
import com.ainovel.app.v2.model.V2ContinuityIssue;
import com.ainovel.app.v2.repo.V2AnalysisJobRepository;
import com.ainovel.app.v2.repo.V2BetaReaderReportRepository;
import com.ainovel.app.v2.repo.V2ContinuityIssueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class V2AnalysisPersistenceService {
    private final V2AnalysisJobRepository jobRepository;
    private final V2BetaReaderReportRepository reportRepository;
    private final V2ContinuityIssueRepository issueRepository;
    private final V2Json v2Json;

    public V2AnalysisPersistenceService(V2AnalysisJobRepository jobRepository,
                                        V2BetaReaderReportRepository reportRepository,
                                        V2ContinuityIssueRepository issueRepository,
                                        V2Json v2Json) {
        this.jobRepository = jobRepository;
        this.reportRepository = reportRepository;
        this.issueRepository = issueRepository;
        this.v2Json = v2Json;
    }

    @Transactional(readOnly = true)
    public List<V2AnalysisDtos.AnalysisJobResponse> listJobs(UUID storyId) {
        return jobRepository.findByStoryIdOrderByCreatedAtDesc(storyId).stream().map(this::jobResponse).toList();
    }

    @Transactional(readOnly = true)
    public V2AnalysisDtos.AnalysisJobResponse getJob(UUID storyId, UUID jobId) {
        return jobResponse(jobRepository.findByStoryIdAndId(storyId, jobId).orElseThrow(() -> new BusinessException("分析任务不存在")));
    }

    @Transactional(readOnly = true)
    public List<V2AnalysisDtos.AnalysisReportResponse> listReports(UUID storyId) {
        return reportRepository.findByStoryIdOrderByCreatedAtDesc(storyId).stream().map(this::reportResponse).toList();
    }

    @Transactional(readOnly = true)
    public V2AnalysisDtos.AnalysisReportResponse getReport(UUID storyId, UUID reportId) {
        return reportResponse(reportRepository.findByStoryIdAndId(storyId, reportId).orElseThrow(() -> new BusinessException("分析报告不存在")));
    }

    @Transactional(readOnly = true)
    public List<V2AnalysisDtos.ContinuityIssueResponse> listContinuityIssues(UUID storyId) {
        return issueRepository.findByStoryIdOrderByCreatedAtDesc(storyId).stream().map(this::issueResponse).toList();
    }

    @Transactional
    public V2AnalysisDtos.ContinuityIssueResponse updateContinuityIssue(UUID storyId, UUID issueId, Map<String, Object> payload) {
        V2ContinuityIssue issue = issueRepository.findByStoryIdAndId(storyId, issueId)
                .orElseThrow(() -> new BusinessException("连续性问题不存在"));
        if (payload.containsKey("status")) issue.setStatus(str(payload.get("status"), issue.getStatus()));
        if (payload.containsKey("suggestion")) issue.setSuggestion(str(payload.get("suggestion"), issue.getSuggestion()));
        if (payload.containsKey("severity")) issue.setSeverity(str(payload.get("severity"), issue.getSeverity()));
        if ("resolved".equals(issue.getStatus())) issue.setResolvedAt(Instant.now());
        return issueResponse(issueRepository.save(issue));
    }

    private V2AnalysisDtos.AnalysisJobResponse jobResponse(V2AnalysisJob job) {
        return new V2AnalysisDtos.AnalysisJobResponse(
                job.getId(),
                job.getStory().getId(),
                job.getUser().getId(),
                job.getJobType(),
                job.getScope(),
                job.getScopeReference(),
                job.getStatus(),
                job.getProgress(),
                job.getProgressMessage(),
                job.getResultReference(),
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }

    private V2AnalysisDtos.AnalysisReportResponse reportResponse(V2BetaReaderReport report) {
        return new V2AnalysisDtos.AnalysisReportResponse(
                report.getId(),
                report.getStory().getId(),
                report.getUser().getId(),
                report.getScope(),
                report.getScopeReference(),
                report.getStatus(),
                analysisSummary(report.getAnalysisJson()),
                report.getSummary(),
                report.getScoreOverall(),
                report.getScorePacing(),
                report.getScoreCharacters(),
                report.getScoreDialogue(),
                report.getScoreConsistency(),
                report.getScoreEngagement(),
                report.getTokenCost(),
                report.getCreatedAt(),
                report.getUpdatedAt()
        );
    }

    private V2AnalysisDtos.ContinuityIssueResponse issueResponse(V2ContinuityIssue issue) {
        return new V2AnalysisDtos.ContinuityIssueResponse(
                issue.getId(),
                issue.getStory().getId(),
                issue.getReport() == null ? null : issue.getReport().getId(),
                issue.getIssueType(),
                issue.getSeverity(),
                issue.getDescription(),
                evidenceItems(issue.getEvidenceJson()),
                issue.getSuggestion(),
                issue.getStatus(),
                issue.getResolvedAt(),
                issue.getCreatedAt()
        );
    }

    private V2AnalysisDtos.AnalysisSummaryResponse analysisSummary(String json) {
        Map<String, Object> raw = v2Json.map(json);
        return new V2AnalysisDtos.AnalysisSummaryResponse(
                str(raw.get("focus"), "overall"),
                stringList(raw.get("highlights")),
                stringList(raw.get("risks"))
        );
    }

    private List<V2AnalysisDtos.ContinuityEvidenceItem> evidenceItems(String json) {
        return v2Json.list(json).stream()
                .map(this::evidenceItem)
                .filter(Objects::nonNull)
                .toList();
    }

    private V2AnalysisDtos.ContinuityEvidenceItem evidenceItem(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            return new V2AnalysisDtos.ContinuityEvidenceItem(
                    integerValue(map.get("chapter")),
                    str(map.get("note"), "")
            );
        }
        return new V2AnalysisDtos.ContinuityEvidenceItem(null, str(raw, ""));
    }

    private List<String> stringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .toList();
    }

    private Integer integerValue(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw == null) {
            return null;
        }
        try {
            return Integer.parseInt(raw.toString().trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String str(Object value, String fallback) {
        if (value == null) return fallback;
        String text = value.toString().trim();
        return text.isBlank() ? fallback : text;
    }
}
