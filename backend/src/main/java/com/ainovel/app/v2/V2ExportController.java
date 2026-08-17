package com.ainovel.app.v2;

import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Tag(name = "V2", description = "AINovel v2 and quality APIs")
@RestController
@RequestMapping("/v2")
public class V2ExportController {
    private final ResourceAccessGuard accessGuard;
    private final V2ExportJobService jobs;
    private final V2ExportPersistenceService persistence;
    private final ObjectMapper objectMapper;

    public V2ExportController(ResourceAccessGuard accessGuard, V2ExportJobService jobs,
                              V2ExportPersistenceService persistence, ObjectMapper objectMapper) {
        this.accessGuard = accessGuard; this.jobs = jobs; this.persistence = persistence; this.objectMapper = objectMapper;
    }

    @Operation(summary = "创建异步导出任务")
    @PostMapping("/manuscripts/{manuscriptId}/export")
    public V2ExportDtos.JobResponse createExportJob(@AuthenticationPrincipal UserDetails principal,
                                                     @PathVariable UUID manuscriptId,
                                                     @Valid @RequestBody V2ExportDtos.CreateJobRequest request) {
        User user = accessGuard.currentUser(principal);
        Manuscript manuscript = accessGuard.requireOwnedManuscript(manuscriptId, user);
        return job(jobs.create(user, manuscript, payload(request)));
    }

    @GetMapping("/manuscripts/{manuscriptId}/export/jobs")
    @Operation(summary = "列出稿件导出任务")
    public List<V2ExportDtos.JobResponse> listExportJobs(@AuthenticationPrincipal UserDetails principal,
                                                         @PathVariable UUID manuscriptId) {
        User user = accessGuard.currentUser(principal); accessGuard.requireOwnedManuscript(manuscriptId, user);
        return jobs.list(manuscriptId).stream().map(this::job).toList();
    }

    @GetMapping("/manuscripts/{manuscriptId}/export/jobs/{jobId}")
    @Operation(summary = "查询稿件导出任务")
    public V2ExportDtos.JobResponse getExportJob(@AuthenticationPrincipal UserDetails principal,
                                                  @PathVariable UUID manuscriptId, @PathVariable UUID jobId) {
        User user = accessGuard.currentUser(principal); accessGuard.requireOwnedManuscript(manuscriptId, user);
        return job(jobs.get(manuscriptId, jobId));
    }

    @GetMapping("/manuscripts/{manuscriptId}/export/jobs/{jobId}/download")
    @Operation(summary = "流式下载已完成的导出产物")
    public ResponseEntity<StreamingResponseBody> download(@AuthenticationPrincipal UserDetails principal,
                                                           @PathVariable UUID manuscriptId, @PathVariable UUID jobId) {
        User user = accessGuard.currentUser(principal); accessGuard.requireOwnedManuscript(manuscriptId, user);
        V2ExportJobService.Download download = jobs.download(manuscriptId, jobId, user);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, download.contentType())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("Digest", "sha-256=" + download.checksum());
        if (download.size() != null) response.contentLength(download.size());
        return response.body(download.body());
    }

    @GetMapping("/export-templates")
    @Operation(summary = "列出导出模板")
    public List<V2ExportDtos.TemplateResponse> listTemplates(@AuthenticationPrincipal UserDetails principal) {
        return persistence.listTemplates(accessGuard.currentUser(principal)).stream().map(this::template).toList();
    }

    @PostMapping("/export-templates")
    @Operation(summary = "创建导出模板")
    public V2ExportDtos.TemplateResponse createTemplate(@AuthenticationPrincipal UserDetails principal,
                                                         @Valid @RequestBody V2ExportDtos.TemplateRequest request) {
        return template(persistence.createTemplate(accessGuard.currentUser(principal), payload(request)));
    }

    @PutMapping("/export-templates/{id}")
    @Operation(summary = "更新导出模板")
    public V2ExportDtos.TemplateResponse updateTemplate(@AuthenticationPrincipal UserDetails principal, @PathVariable UUID id,
                                                         @Valid @RequestBody V2ExportDtos.TemplateRequest request) {
        return template(persistence.updateTemplate(accessGuard.currentUser(principal), id, payload(request)));
    }

    @DeleteMapping("/export-templates/{id}")
    @Operation(summary = "删除导出模板")
    public ResponseEntity<Void> deleteTemplate(@AuthenticationPrincipal UserDetails principal, @PathVariable UUID id) {
        persistence.deleteTemplate(accessGuard.currentUser(principal), id); return ResponseEntity.noContent().build();
    }

    private Map<String, Object> payload(Object value) {
        Map<String, Object> converted = objectMapper.convertValue(value, new com.fasterxml.jackson.core.type.TypeReference<>() { });
        converted.values().removeIf(Objects::isNull);
        return converted;
    }
    private V2ExportDtos.JobResponse job(Map<String, Object> row) {
        return new V2ExportDtos.JobResponse(uuid(row,"id"),uuid(row,"userId"),uuid(row,"storyId"),uuid(row,"manuscriptId"),uuid(row,"templateId"),
                text(row,"format"),json(row.get("config")),text(row,"chapterRange"),text(row,"status"),number(row,"progress").intValue(),
                text(row,"fileName"),text(row,"filePath"),number(row,"fileSizeBytes").longValue(),text(row,"checksum"),text(row,"errorMessage"),
                text(row,"contentType"),instant(row,"expiresAt"),instant(row,"createdAt"),instant(row,"startedAt"),instant(row,"completedAt"));
    }
    private V2ExportDtos.TemplateResponse template(Map<String,Object> row) { return new V2ExportDtos.TemplateResponse(uuid(row,"id"),uuid(row,"userId"),text(row,"name"),text(row,"description"),text(row,"format"),json(row.get("config")),Boolean.TRUE.equals(row.get("isDefault")),instant(row,"createdAt"),instant(row,"updatedAt")); }
    private UUID uuid(Map<String,Object> row,String key){Object value=row.get(key);return value==null?null:value instanceof UUID id?id:UUID.fromString(value.toString());}
    private String text(Map<String,Object> row,String key){Object value=row.get(key);return value==null?null:value.toString();}
    private Number number(Map<String,Object> row,String key){Object value=row.get(key);return value instanceof Number number?number:0;}
    private Instant instant(Map<String,Object> row,String key){Object value=row.get(key);return value instanceof Instant instant?instant:null;}
    private JsonNode json(Object value){return value==null?objectMapper.createObjectNode():objectMapper.valueToTree(value);}
}
