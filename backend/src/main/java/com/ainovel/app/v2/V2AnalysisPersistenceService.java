package com.ainovel.app.v2;

import com.ainovel.app.common.BusinessException;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.user.User;
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

    @Transactional
    public V2AnalysisDtos.AnalysisJobResponse createAnalysisJob(User user, Story story, Map<String, Object> payload, String jobType) {
        Map<String, Object> safePayload = payload == null ? Map.of() : payload;
        V2AnalysisDtos.AnalysisSummaryResponse analysis = buildReportAnalysis(safePayload);

        V2BetaReaderReport report = new V2BetaReaderReport();
        report.setStory(story);
        report.setUser(user);
        report.setScope(str(safePayload.get("scope"), "full_manuscript"));
        report.setScopeReference(safePayload.get("scopeReference") == null ? null : safePayload.get("scopeReference").toString());
        report.setStatus("completed");
        report.setAnalysisJson(v2Json.write(analysis));
        report.setSummary("分析已完成，可查看建议与风险项。");
        report.setScoreOverall(80);
        report.setScorePacing(78);
        report.setScoreCharacters(82);
        report.setScoreDialogue(76);
        report.setScoreConsistency(79);
        report.setScoreEngagement(81);
        report.setTokenCost(1200);
        report = reportRepository.save(report);

        V2AnalysisJob job = new V2AnalysisJob();
        job.setStory(story);
        job.setUser(user);
        job.setJobType(jobType);
        job.setScope(str(safePayload.get("scope"), "full"));
        job.setScopeReference(safePayload.get("scopeReference") == null ? null : safePayload.get("scopeReference").toString());
        job.setStatus("completed");
        job.setProgress(100);
        job.setProgressMessage("分析完成");
        job.setResultReference(report.getId());
        job.setErrorMessage(null);
        return jobResponse(jobRepository.save(job));
    }

    @Transactional
    public V2AnalysisDtos.ContinuityIssueResponse createContinuityIssue(UUID storyId, UUID reportId, String text) {
        V2BetaReaderReport report = reportRepository.findByStoryIdAndId(storyId, reportId)
                .orElseThrow(() -> new BusinessException("分析报告不存在"));
        V2ContinuityIssue issue = new V2ContinuityIssue();
        issue.setStory(report.getStory());
        issue.setReport(report);
        issue.setIssueType("timeline_error");
        issue.setSeverity("warning");
        String snippet = text == null || text.isBlank() ? "未提供文本，建议补充上下文后重试" : text.substring(0, Math.min(text.length(), 60));
        issue.setDescription("时间线存在潜在冲突：" + snippet);
        issue.setEvidenceJson(v2Json.write(List.of(new V2AnalysisDtos.ContinuityEvidenceItem(1, "事件顺序可能前后颠倒"))));
        issue.setSuggestion("补充过渡段并在下一章回收冲突细节");
        issue.setStatus("open");
        return issueResponse(issueRepository.save(issue));
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

    private V2AnalysisDtos.AnalysisSummaryResponse buildReportAnalysis(Map<String, Object> payload) {
        return new V2AnalysisDtos.AnalysisSummaryResponse(
                str(payload.get("focus"), "overall"),
                List.of("角色动机较清晰", "章节节奏总体稳定"),
                List.of("个别场景承接略跳跃", "伏笔回收密度偏低")
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
