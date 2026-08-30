package com.ainovel.app.v2;

import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class V2ExportControllerTests {
    private ResourceAccessGuard accessGuard;
    private V2ExportJobService jobService;
    private V2ExportPersistenceService persistence;
    private V2ExportController controller;
    private UserDetails principal;
    private User user;
    private Manuscript manuscript;
    private UUID manuscriptId;

    @BeforeEach
    void setUp() {
        accessGuard = mock(ResourceAccessGuard.class);
        jobService = mock(V2ExportJobService.class);
        persistence = mock(V2ExportPersistenceService.class);
        controller = new V2ExportController(accessGuard, jobService, persistence, new ObjectMapper());
        principal = mock(UserDetails.class);
        user = new User();
        user.setId(UUID.randomUUID());
        manuscript = new Manuscript();
        manuscriptId = UUID.randomUUID();
        manuscript.setId(manuscriptId);
        manuscript.setTitle("测试导出");
        when(accessGuard.currentUser(any())).thenReturn(user);
        when(accessGuard.requireOwnedManuscript(manuscriptId, user)).thenReturn(manuscript);
    }

    @Test
    void createReturnsQueuedJobAndDelegatesRenderingToWorker() {
        UUID jobId = UUID.randomUUID();
        when(jobService.create(eq(user), eq(manuscript), any())).thenReturn(exportJob(jobId, "queued"));

        V2ExportDtos.JobResponse response = controller.createExportJob(
                principal, manuscriptId, new V2ExportDtos.CreateJobRequest("txt", null, null, "all"));

        assertEquals(jobId, response.id());
        assertEquals("queued", response.status());
        verify(jobService).create(eq(user), eq(manuscript), any());
    }

    @Test
    void downloadStreamsCompletedArtifactWithChecksum() throws Exception {
        UUID jobId = UUID.randomUUID();
        StreamingResponseBody body = output -> output.write("hello".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(jobService.download(manuscriptId, jobId, user)).thenReturn(
                new V2ExportJobService.Download("正文稿.txt", "text/plain; charset=UTF-8", 5L, "abc123", body));

        ResponseEntity<StreamingResponseBody> response = controller.download(principal, manuscriptId, jobId);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertNotNull(response.getBody());
        response.getBody().writeTo(output);

        assertEquals("hello", output.toString(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("sha-256=abc123", response.getHeaders().getFirst("Digest"));
        assertTrue(response.getHeaders().getFirst("Content-Disposition").contains("filename*=UTF-8''"));
    }

    @Test
    void listTemplatesUsesTypedResponse() {
        UUID id = UUID.randomUUID();
        Map<String, Object> template = new HashMap<>();
        template.put("id", id);
        template.put("name", "系统默认 TXT");
        template.put("format", "txt");
        template.put("isDefault", true);
        template.put("createdAt", Instant.now());
        template.put("updatedAt", Instant.now());
        when(persistence.listTemplates(user)).thenReturn(List.of(template));

        List<V2ExportDtos.TemplateResponse> templates = controller.listTemplates(principal);

        assertEquals(1, templates.size());
        assertEquals(id, templates.get(0).id());
        assertEquals("系统默认 TXT", templates.get(0).name());
    }

    private Map<String, Object> exportJob(UUID id, String status) {
        Map<String, Object> job = new HashMap<>();
        job.put("id", id);
        job.put("userId", user.getId());
        job.put("manuscriptId", manuscriptId);
        job.put("format", "txt");
        job.put("status", status);
        job.put("progress", 0);
        job.put("fileSizeBytes", 0L);
        job.put("createdAt", Instant.now());
        return job;
    }
}
