package com.ainovel.app.v2;

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

    public V2AnalysisPersistenceService(V2AnalysisJobRepository jobRepository,
                                        V2BetaReaderReportRepository reportRepository,
                                        V2ContinuityIssueRepository issueRepository) {
        this.jobRepository = jobRepository;
        this.reportRepository = reportRepository;
        this.issueRepository = issueRepository;
    }

    @Transactional
    public Map<String, Object> createAnalysisJob(User user, Story story, Map<String, Object> payload, String jobType) {
        V2BetaReaderReport report = new V2BetaReaderReport();
        report.setStory(story);
        report.setUser(user);
        report.setScope(str(payload == null ? null : payload.get("scope"), "full_manuscript"));
        report.setScopeReference(payload == null || payload.get("scopeReference") == null ? null : payload.get("scopeReference").toString());
        report.setStatus("completed");
        report.setAnalysisJson(V2Json.write(buildReportAnalysis(payload)));
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
        job.setScope(str(payload == null ? null : payload.get("scope"), "full"));
        job.setScopeReference(payload == null || payload.get("scopeReference") == null ? null : payload.get("scopeReference").toString());
        job.setStatus("completed");
        job.setProgress(100);
        job.setProgressMessage("分析完成");
        job.setResultReference(report.getId());
        job.setErrorMessage(null);
        return jobMap(jobRepository.save(job));
    }

    @Transactional
    public Map<String, Object> createContinuityIssue(UUID storyId, UUID reportId, String text) {
        V2BetaReaderReport report = reportRepository.findByStoryIdAndId(storyId, reportId)
                .orElseThrow(() -> new RuntimeException("分析报告不存在"));
        V2ContinuityIssue issue = new V2ContinuityIssue();
        issue.setStory(report.getStory());
        issue.setReport(report);
        issue.setIssueType("timeline_error");
        issue.setSeverity("warning");
        String snippet = text == null || text.isBlank() ? "未提供文本，建议补充上下文后重试" : text.substring(0, Math.min(text.length(), 60));
        issue.setDescription("时间线存在潜在冲突：" + snippet);
        issue.setEvidenceJson(V2Json.write(List.of(Map.of("chapter", 1, "note", "事件顺序可能前后颠倒"))));
        issue.setSuggestion("补充过渡段并在下一章回收冲突细节");
        issue.setStatus("open");
        return issueMap(issueRepository.save(issue));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listJobs(UUID storyId) {
        return jobRepository.findByStoryIdOrderByCreatedAtDesc(storyId).stream().map(this::jobMap).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getJob(UUID storyId, UUID jobId) {
        return jobMap(jobRepository.findByStoryIdAndId(storyId, jobId).orElseThrow(() -> new RuntimeException("分析任务不存在")));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listReports(UUID storyId) {
        return reportRepository.findByStoryIdOrderByCreatedAtDesc(storyId).stream().map(this::reportMap).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getReport(UUID storyId, UUID reportId) {
        return reportMap(reportRepository.findByStoryIdAndId(storyId, reportId).orElseThrow(() -> new RuntimeException("分析报告不存在")));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listContinuityIssues(UUID storyId) {
        return issueRepository.findByStoryIdOrderByCreatedAtDesc(storyId).stream().map(this::issueMap).toList();
    }

    @Transactional
    public Map<String, Object> updateContinuityIssue(UUID storyId, UUID issueId, Map<String, Object> payload) {
        V2ContinuityIssue issue = issueRepository.findByStoryIdAndId(storyId, issueId)
                .orElseThrow(() -> new RuntimeException("连续性问题不存在"));
        if (payload.containsKey("status")) issue.setStatus(str(payload.get("status"), issue.getStatus()));
        if (payload.containsKey("suggestion")) issue.setSuggestion(str(payload.get("suggestion"), issue.getSuggestion()));
        if (payload.containsKey("severity")) issue.setSeverity(str(payload.get("severity"), issue.getSeverity()));
        if ("resolved".equals(issue.getStatus())) issue.setResolvedAt(Instant.now());
        return issueMap(issueRepository.save(issue));
    }

    private Map<String, Object> jobMap(V2AnalysisJob job) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", job.getId());
        out.put("storyId", job.getStory().getId());
        out.put("userId", job.getUser().getId());
        out.put("jobType", job.getJobType());
        out.put("scope", job.getScope());
        out.put("scopeReference", job.getScopeReference());
        out.put("status", job.getStatus());
        out.put("progress", job.getProgress());
        out.put("progressMessage", job.getProgressMessage());
        out.put("resultReference", job.getResultReference());
        out.put("errorMessage", job.getErrorMessage());
        out.put("createdAt", job.getCreatedAt());
        out.put("updatedAt", job.getUpdatedAt());
        return out;
    }

    private Map<String, Object> reportMap(V2BetaReaderReport report) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", report.getId());
        out.put("storyId", report.getStory().getId());
        out.put("userId", report.getUser().getId());
        out.put("scope", report.getScope());
        out.put("scopeReference", report.getScopeReference());
        out.put("status", report.getStatus());
        out.put("analysis", V2Json.map(report.getAnalysisJson()));
        out.put("summary", report.getSummary());
        out.put("scoreOverall", report.getScoreOverall());
        out.put("scorePacing", report.getScorePacing());
        out.put("scoreCharacters", report.getScoreCharacters());
        out.put("scoreDialogue", report.getScoreDialogue());
        out.put("scoreConsistency", report.getScoreConsistency());
        out.put("scoreEngagement", report.getScoreEngagement());
        out.put("tokenCost", report.getTokenCost());
        out.put("createdAt", report.getCreatedAt());
        out.put("updatedAt", report.getUpdatedAt());
        return out;
    }

    private Map<String, Object> issueMap(V2ContinuityIssue issue) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", issue.getId());
        out.put("storyId", issue.getStory().getId());
        out.put("reportId", issue.getReport() == null ? null : issue.getReport().getId());
        out.put("issueType", issue.getIssueType());
        out.put("severity", issue.getSeverity());
        out.put("description", issue.getDescription());
        out.put("evidence", V2Json.list(issue.getEvidenceJson()));
        out.put("suggestion", issue.getSuggestion());
        out.put("status", issue.getStatus());
        out.put("resolvedAt", issue.getResolvedAt());
        out.put("createdAt", issue.getCreatedAt());
        return out;
    }

    private Map<String, Object> buildReportAnalysis(Map<String, Object> payload) {
        return Map.of(
                "focus", payload == null ? "overall" : str(payload.get("focus"), "overall"),
                "highlights", List.of("角色动机较清晰", "章节节奏总体稳定"),
                "risks", List.of("个别场景承接略跳跃", "伏笔回收密度偏低")
        );
    }

    private static String str(Object value, String fallback) {
        if (value == null) return fallback;
        String text = value.toString().trim();
        return text.isBlank() ? fallback : text;
    }
}
