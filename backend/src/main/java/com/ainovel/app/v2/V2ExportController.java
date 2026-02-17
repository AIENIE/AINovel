package com.ainovel.app.v2;

import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.user.User;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@RestController
@RequestMapping("/v2")
public class V2ExportController {
    private final V2AccessGuard accessGuard;

    private final ConcurrentMap<UUID, Map<String, Object>> templates = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ConcurrentMap<UUID, Map<String, Object>>> jobsByManuscript = new ConcurrentHashMap<>();

    public V2ExportController(V2AccessGuard accessGuard) {
        this.accessGuard = accessGuard;
        seedSystemTemplates();
    }

    @PostMapping("/manuscripts/{manuscriptId}/export")
    public Map<String, Object> createExportJob(@AuthenticationPrincipal UserDetails principal,
                                               @PathVariable UUID manuscriptId,
                                               @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        Manuscript manuscript = accessGuard.requireOwnedManuscript(manuscriptId, user);

        String format = str(payload.get("format"), "txt");
        UUID templateId = payload.get("templateId") == null ? null : UUID.fromString(payload.get("templateId").toString());
        Map<String, Object> template = templateId == null ? null : templates.get(templateId);
        if (templateId != null && template == null) {
            throw new RuntimeException("导出模板不存在");
        }

        UUID jobId = UUID.randomUUID();
        Map<String, Object> job = new HashMap<>();
        job.put("id", jobId);
        job.put("userId", user.getId());
        job.put("storyId", manuscript.getOutline().getStory().getId());
        job.put("manuscriptId", manuscriptId);
        job.put("templateId", templateId);
        job.put("format", format);
        job.put("config", payload.getOrDefault("config", template == null ? Map.of() : template.get("config")));
        job.put("chapterRange", payload.get("chapterRange"));
        job.put("status", "completed");
        job.put("progress", 100);
        job.put("fileName", manuscript.getTitle() + "-" + Instant.now().toEpochMilli() + "." + format);
        job.put("filePath", "exports/" + manuscriptId + "/" + jobId + "." + format);
        job.put("fileSizeBytes", 1024L);
        job.put("errorMessage", null);
        job.put("expiresAt", Instant.now().plus(7, ChronoUnit.DAYS));
        job.put("createdAt", Instant.now());

        jobsByManuscript.computeIfAbsent(manuscriptId, key -> new ConcurrentHashMap<>()).put(jobId, job);
        return job;
    }

    @GetMapping("/manuscripts/{manuscriptId}/export/jobs")
    public List<Map<String, Object>> listExportJobs(@AuthenticationPrincipal UserDetails principal,
                                                    @PathVariable UUID manuscriptId) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedManuscript(manuscriptId, user);
        return new ArrayList<>(jobsByManuscript.computeIfAbsent(manuscriptId, key -> new ConcurrentHashMap<>()).values());
    }

    @GetMapping("/manuscripts/{manuscriptId}/export/jobs/{jobId}")
    public Map<String, Object> getExportJob(@AuthenticationPrincipal UserDetails principal,
                                            @PathVariable UUID manuscriptId,
                                            @PathVariable UUID jobId) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedManuscript(manuscriptId, user);
        Map<String, Object> job = requireJob(manuscriptId, jobId);
        if (((Instant) job.get("expiresAt")).isBefore(Instant.now())) {
            job.put("status", "expired");
        }
        return job;
    }

    @GetMapping("/manuscripts/{manuscriptId}/export/jobs/{jobId}/download")
    public ResponseEntity<String> download(@AuthenticationPrincipal UserDetails principal,
                                           @PathVariable UUID manuscriptId,
                                           @PathVariable UUID jobId) {
        User user = accessGuard.currentUser(principal);
        accessGuard.requireOwnedManuscript(manuscriptId, user);
        Map<String, Object> job = requireJob(manuscriptId, jobId);
        Instant expiresAt = (Instant) job.get("expiresAt");
        if (expiresAt.isBefore(Instant.now())) {
            throw new RuntimeException("导出文件已过期");
        }

        String format = str(job.get("format"), "txt");
        String fileName = str(job.get("fileName"), "export." + format);
        String content = "# AINovel Export\n\nThis is a generated " + format + " preview for manuscript " + manuscriptId + ".";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(content);
    }

    @GetMapping("/export-templates")
    public List<Map<String, Object>> listTemplates(@AuthenticationPrincipal UserDetails principal) {
        User user = accessGuard.currentUser(principal);
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> template : templates.values()) {
            Object owner = template.get("userId");
            if (owner == null || owner.equals(user.getId())) {
                results.add(template);
            }
        }
        return results;
    }

    @PostMapping("/export-templates")
    public Map<String, Object> createTemplate(@AuthenticationPrincipal UserDetails principal,
                                              @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        UUID id = UUID.randomUUID();

        Map<String, Object> template = new HashMap<>();
        template.put("id", id);
        template.put("userId", user.getId());
        template.put("name", str(payload.get("name"), "自定义模板"));
        template.put("description", str(payload.get("description"), ""));
        template.put("format", str(payload.get("format"), "txt"));
        template.put("config", payload.getOrDefault("config", Map.of()));
        template.put("isDefault", boolVal(payload.get("isDefault"), false));
        template.put("createdAt", Instant.now());
        template.put("updatedAt", Instant.now());

        templates.put(id, template);
        return template;
    }

    @PutMapping("/export-templates/{id}")
    public Map<String, Object> updateTemplate(@AuthenticationPrincipal UserDetails principal,
                                              @PathVariable UUID id,
                                              @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        Map<String, Object> template = requireTemplate(id);
        if (template.get("userId") == null || !template.get("userId").equals(user.getId())) {
            throw new RuntimeException("无权修改该模板");
        }

        mergeIfPresent(payload, template, "name", "description", "format");
        if (payload.containsKey("config")) {
            template.put("config", payload.get("config"));
        }
        if (payload.containsKey("isDefault")) {
            template.put("isDefault", boolVal(payload.get("isDefault"), false));
        }
        template.put("updatedAt", Instant.now());
        return template;
    }

    @DeleteMapping("/export-templates/{id}")
    public ResponseEntity<Void> deleteTemplate(@AuthenticationPrincipal UserDetails principal,
                                               @PathVariable UUID id) {
        User user = accessGuard.currentUser(principal);
        Map<String, Object> template = requireTemplate(id);
        if (template.get("userId") == null || !template.get("userId").equals(user.getId())) {
            throw new RuntimeException("无权删除该模板");
        }
        templates.remove(id);
        return ResponseEntity.noContent().build();
    }

    private void seedSystemTemplates() {
        for (String format : List.of("txt", "docx", "epub", "pdf")) {
            UUID id = UUID.randomUUID();
            Map<String, Object> template = new HashMap<>();
            template.put("id", id);
            template.put("userId", null);
            template.put("name", "系统默认 " + format.toUpperCase(Locale.ROOT));
            template.put("description", "系统预设模板");
            template.put("format", format);
            template.put("config", Map.of("font", "default", "lineSpacing", 1.5));
            template.put("isDefault", true);
            template.put("createdAt", Instant.now());
            template.put("updatedAt", Instant.now());
            templates.put(id, template);
        }
    }

    private Map<String, Object> requireJob(UUID manuscriptId, UUID jobId) {
        Map<String, Object> job = jobsByManuscript.computeIfAbsent(manuscriptId, key -> new ConcurrentHashMap<>()).get(jobId);
        if (job == null) {
            throw new RuntimeException("导出任务不存在");
        }
        return job;
    }

    private Map<String, Object> requireTemplate(UUID id) {
        Map<String, Object> template = templates.get(id);
        if (template == null) {
            throw new RuntimeException("导出模板不存在");
        }
        return template;
    }

    private String str(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? fallback : text;
    }

    private boolean boolVal(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private void mergeIfPresent(Map<String, Object> source, Map<String, Object> target, String... keys) {
        for (String key : keys) {
            if (source.containsKey(key)) {
                target.put(key, source.get(key));
            }
        }
    }
}
