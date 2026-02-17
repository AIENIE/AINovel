package com.ainovel.app.v2;

import com.ainovel.app.user.User;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@RestController
@RequestMapping("/v2")
public class V2AnalysisController {
    private final V2AccessGuard accessGuard;

    private final ConcurrentMap<UUID, ConcurrentMap<UUID, Map<String, Object>>> jobsByStory = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ConcurrentMap<UUID, Map<String, Object>>> reportsByStory = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ConcurrentMap<UUID, Map<String, Object>>> issuesByStory = new ConcurrentHashMap<>();

    public V2AnalysisController(V2AccessGuard accessGuard) {
        this.accessGuard = accessGuard;
    }

    @PostMapping("/stories/{storyId}/analysis/beta-reader")
    public Map<String, Object> triggerBetaReader(@AuthenticationPrincipal UserDetails principal,
                                                 @PathVariable UUID storyId,
                                                 @RequestBody(required = false) Map<String, Object> payload) {
        return createAnalysisJob(principal, storyId, payload, "beta_reader");
    }

    @PostMapping("/stories/{storyId}/analysis/continuity-check")
    public Map<String, Object> triggerContinuityCheck(@AuthenticationPrincipal UserDetails principal,
                                                      @PathVariable UUID storyId,
                                                      @RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> job = createAnalysisJob(principal, storyId, payload, "continuity_check");
        createContinuityIssue(storyId, (UUID) job.get("resultReference"), payload == null ? "" : str(payload.get("text"), ""));
        return job;
    }

    @GetMapping("/stories/{storyId}/analysis/jobs")
    public List<Map<String, Object>> listJobs(@AuthenticationPrincipal UserDetails principal,
                                              @PathVariable UUID storyId) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);
        return new ArrayList<>(jobsByStory.computeIfAbsent(storyId, key -> new ConcurrentHashMap<>()).values());
    }

    @GetMapping("/stories/{storyId}/analysis/jobs/{jobId}")
    public Map<String, Object> getJob(@AuthenticationPrincipal UserDetails principal,
                                      @PathVariable UUID storyId,
                                      @PathVariable UUID jobId) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);
        Map<String, Object> job = jobsByStory.computeIfAbsent(storyId, key -> new ConcurrentHashMap<>()).get(jobId);
        if (job == null) {
            throw new RuntimeException("分析任务不存在");
        }
        return job;
    }

    @GetMapping("/stories/{storyId}/analysis/reports")
    public List<Map<String, Object>> listReports(@AuthenticationPrincipal UserDetails principal,
                                                 @PathVariable UUID storyId) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);
        return new ArrayList<>(reportsByStory.computeIfAbsent(storyId, key -> new ConcurrentHashMap<>()).values());
    }

    @GetMapping("/stories/{storyId}/analysis/reports/{reportId}")
    public Map<String, Object> getReport(@AuthenticationPrincipal UserDetails principal,
                                         @PathVariable UUID storyId,
                                         @PathVariable UUID reportId) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);
        Map<String, Object> report = reportsByStory.computeIfAbsent(storyId, key -> new ConcurrentHashMap<>()).get(reportId);
        if (report == null) {
            throw new RuntimeException("分析报告不存在");
        }
        return report;
    }

    @GetMapping("/stories/{storyId}/analysis/continuity-issues")
    public List<Map<String, Object>> listContinuityIssues(@AuthenticationPrincipal UserDetails principal,
                                                          @PathVariable UUID storyId) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);
        return new ArrayList<>(issuesByStory.computeIfAbsent(storyId, key -> new ConcurrentHashMap<>()).values());
    }

    @PutMapping("/stories/{storyId}/analysis/continuity-issues/{issueId}")
    public Map<String, Object> updateContinuityIssue(@AuthenticationPrincipal UserDetails principal,
                                                     @PathVariable UUID storyId,
                                                     @PathVariable UUID issueId,
                                                     @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);
        Map<String, Object> issue = issuesByStory.computeIfAbsent(storyId, key -> new ConcurrentHashMap<>()).get(issueId);
        if (issue == null) {
            throw new RuntimeException("连续性问题不存在");
        }
        if (payload.containsKey("status")) {
            issue.put("status", str(payload.get("status"), "open"));
        }
        if (payload.containsKey("suggestion")) {
            issue.put("suggestion", payload.get("suggestion"));
        }
        if (payload.containsKey("severity")) {
            issue.put("severity", payload.get("severity"));
        }
        if ("resolved".equals(issue.get("status"))) {
            issue.put("resolvedAt", Instant.now());
        }
        return issue;
    }

    private Map<String, Object> createAnalysisJob(UserDetails principal,
                                                  UUID storyId,
                                                  Map<String, Object> payload,
                                                  String jobType) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedStory(storyId, user);
        UUID reportId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        Map<String, Object> report = new HashMap<>();
        report.put("id", reportId);
        report.put("storyId", storyId);
        report.put("userId", user.getId());
        report.put("scope", str(payload == null ? null : payload.get("scope"), "full_manuscript"));
        report.put("scopeReference", payload == null ? null : payload.get("scopeReference"));
        report.put("status", "completed");
        report.put("analysis", buildReportAnalysis(payload));
        report.put("summary", "分析已完成，可查看建议与风险项。");
        report.put("scoreOverall", 80);
        report.put("scorePacing", 78);
        report.put("scoreCharacters", 82);
        report.put("scoreDialogue", 76);
        report.put("scoreConsistency", 79);
        report.put("scoreEngagement", 81);
        report.put("tokenCost", 1200);
        report.put("createdAt", Instant.now());
        report.put("updatedAt", Instant.now());
        reportsByStory.computeIfAbsent(storyId, key -> new ConcurrentHashMap<>()).put(reportId, report);

        Map<String, Object> job = new HashMap<>();
        job.put("id", jobId);
        job.put("storyId", storyId);
        job.put("userId", user.getId());
        job.put("jobType", jobType);
        job.put("scope", str(payload == null ? null : payload.get("scope"), "full"));
        job.put("scopeReference", payload == null ? null : payload.get("scopeReference"));
        job.put("status", "completed");
        job.put("progress", 100);
        job.put("progressMessage", "分析完成");
        job.put("resultReference", reportId);
        job.put("errorMessage", null);
        job.put("createdAt", Instant.now());
        job.put("updatedAt", Instant.now());
        jobsByStory.computeIfAbsent(storyId, key -> new ConcurrentHashMap<>()).put(jobId, job);

        return job;
    }

    private void createContinuityIssue(UUID storyId, UUID reportId, String text) {
        UUID issueId = UUID.randomUUID();
        String snippet = text == null || text.isBlank() ? "未提供文本，建议补充上下文后重试" : text.substring(0, Math.min(text.length(), 60));
        Map<String, Object> issue = new HashMap<>();
        issue.put("id", issueId);
        issue.put("storyId", storyId);
        issue.put("reportId", reportId);
        issue.put("issueType", "timeline_error");
        issue.put("severity", "warning");
        issue.put("description", "时间线存在潜在冲突：" + snippet);
        issue.put("evidence", List.of(Map.of("chapter", 1, "note", "事件顺序可能前后颠倒")));
        issue.put("suggestion", "补充过渡段并在下一章回收冲突细节");
        issue.put("status", "open");
        issue.put("resolvedAt", null);
        issue.put("createdAt", Instant.now());
        issuesByStory.computeIfAbsent(storyId, key -> new ConcurrentHashMap<>()).put(issueId, issue);
    }

    private Map<String, Object> buildReportAnalysis(Map<String, Object> payload) {
        String focus = payload == null ? "overall" : str(payload.get("focus"), "overall");
        return Map.of(
                "focus", focus,
                "highlights", List.of("角色动机较清晰", "章节节奏总体稳定"),
                "risks", List.of("个别场景承接略跳跃", "伏笔回收密度偏低")
        );
    }

    private String str(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? fallback : text;
    }
}
