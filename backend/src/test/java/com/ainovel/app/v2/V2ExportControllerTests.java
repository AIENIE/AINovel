package com.ainovel.app.v2;

import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.story.model.Outline;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class V2ExportControllerTests {

    private ResourceAccessGuard accessGuard;
    private V2ExportPersistenceService exportService;
    private V2ExportController controller;
    private UserDetails principal;
    private User user;
    private Manuscript manuscript;
    private UUID manuscriptId;

    @BeforeEach
    void setUp() {
        accessGuard = mock(ResourceAccessGuard.class);
        exportService = mock(V2ExportPersistenceService.class);
        controller = new V2ExportController(accessGuard, exportService, new ObjectMapper());

        principal = mock(UserDetails.class);
        user = new User();
        user.setId(UUID.randomUUID());

        Story story = new Story();
        story.setId(UUID.randomUUID());

        Outline outline = new Outline();
        outline.setStory(story);
        outline.setContentJson("""
                {
                  "chapters": [
                    {"id":"ch1","title":"第一章","scenes":[{"id":"s1","title":"场景一"}]},
                    {"id":"ch2","title":"第二章","scenes":[{"id":"s2","title":"场景二"}]}
                  ]
                }
                """);

        manuscript = new Manuscript();
        manuscriptId = UUID.randomUUID();
        manuscript.setId(manuscriptId);
        manuscript.setTitle("测试导出");
        manuscript.setOutline(outline);
        manuscript.setSectionsJson("{\"s1\":\"hello one\",\"s2\":\"hello two\"}");

        when(accessGuard.currentUser(any())).thenReturn(user);
        when(accessGuard.requireOwnedManuscript(manuscriptId, user)).thenReturn(manuscript);
    }

    @Test
    void shouldLimitConcurrentJobsPerUser() {
        when(exportService.countActiveJobs(user.getId())).thenReturn(3);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                controller.createExportJob(principal, manuscriptId, Map.of("format", "txt"))
        );
        assertEquals(429, ex.getStatusCode().value());
        verify(exportService).cleanupExpiredJobs();
        verify(exportService).countActiveJobs(user.getId());
    }

    @Test
    void shouldGenerateDownloadableDocx() {
        UUID jobId = UUID.randomUUID();
        Map<String, Object> job = exportJob(jobId, "docx", Map.of("includeTitlePage", true), "all", "测试导出.docx");
        when(exportService.getJob(manuscriptId, jobId)).thenReturn(job);

        ResponseEntity<byte[]> response = controller.download(principal, manuscriptId, jobId);
        assertNotNull(response.getBody());
        assertTrue(response.getBody().length > 4);
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", response.getHeaders().getFirst("Content-Type"));
        assertEquals('P', response.getBody()[0]);
        assertEquals('K', response.getBody()[1]);
        verify(exportService).cleanupExpiredJobs();
        verify(exportService).getJob(manuscriptId, jobId);
        verify(exportService).updateJob(eq(manuscriptId), eq(jobId), any(Map.class));
    }

    @Test
    void shouldRespectChapterRangeAndSceneSelectionForTxt() {
        UUID jobId = UUID.randomUUID();
        Map<String, Object> job = exportJob(jobId, "txt", Map.of("selectedSceneIds", List.of("s2"), "includeMetadata", false), "2-2", "测试导出.txt");
        when(exportService.getJob(manuscriptId, jobId)).thenReturn(job);

        ResponseEntity<byte[]> response = controller.download(principal, manuscriptId, jobId);
        String text = new String(response.getBody(), StandardCharsets.UTF_8);
        assertTrue(text.contains("第二章"));
        assertTrue(text.contains("hello two"));
        assertFalse(text.contains("第一章"));
        assertFalse(text.contains("hello one"));
    }

    @Test
    void listTemplatesShouldDelegateToPersistenceService() {
        Map<String, Object> template = Map.of(
                "id", UUID.randomUUID(),
                "name", "系统默认 TXT",
                "format", "txt"
        );
        when(exportService.listTemplates(user)).thenReturn(List.of(template));

        List<Map<String, Object>> templates = controller.listTemplates(principal);

        assertEquals(1, templates.size());
        assertEquals("系统默认 TXT", templates.get(0).get("name"));
        verify(exportService).listTemplates(user);
    }

    private Map<String, Object> exportJob(UUID jobId, String format, Map<String, Object> config, String chapterRange, String fileName) {
        Map<String, Object> job = new java.util.HashMap<>();
        job.put("id", jobId);
        job.put("manuscriptId", manuscriptId);
        job.put("format", format);
        job.put("config", config);
        job.put("chapterRange", chapterRange);
        job.put("status", "queued");
        job.put("progress", 0);
        job.put("fileName", fileName);
        job.put("fileSizeBytes", 0L);
        job.put("contentType", "docx".equals(format)
                ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                : "text/plain; charset=UTF-8");
        job.put("expiresAt", Instant.now().plusSeconds(3600));
        job.put("createdAt", Instant.now().minusSeconds(10));
        job.put("filePath", null);
        job.put("errorMessage", null);
        job.put("contentBytes", null);
        job.put("startedAt", null);
        job.put("completedAt", null);
        return job;
    }
}
