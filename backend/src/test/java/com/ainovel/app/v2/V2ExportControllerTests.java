package com.ainovel.app.v2;

import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.story.model.Outline;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.user.User;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class V2ExportControllerTests {

    private V2AccessGuard accessGuard;
    private V2ExportController controller;
    private UserDetails principal;
    private User user;
    private Manuscript manuscript;
    private UUID manuscriptId;

    @BeforeEach
    void setUp() {
        accessGuard = mock(V2AccessGuard.class);
        controller = new V2ExportController(accessGuard);

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
        controller.createExportJob(principal, manuscriptId, Map.of("format", "txt"));
        controller.createExportJob(principal, manuscriptId, Map.of("format", "txt"));
        controller.createExportJob(principal, manuscriptId, Map.of("format", "txt"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                controller.createExportJob(principal, manuscriptId, Map.of("format", "txt"))
        );
        assertEquals(429, ex.getStatusCode().value());
    }

    @Test
    void shouldGenerateDownloadableDocx() {
        Map<String, Object> job = controller.createExportJob(principal, manuscriptId, Map.of("format", "docx"));
        job.put("createdAt", Instant.now().minusSeconds(10));

        ResponseEntity<byte[]> response = controller.download(principal, manuscriptId, UUID.fromString(job.get("id").toString()));
        assertNotNull(response.getBody());
        assertTrue(response.getBody().length > 4);
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", response.getHeaders().getFirst("Content-Type"));
        assertEquals('P', response.getBody()[0]);
        assertEquals('K', response.getBody()[1]);
    }

    @Test
    void shouldRespectChapterRangeAndSceneSelectionForTxt() {
        Map<String, Object> job = controller.createExportJob(principal, manuscriptId, Map.of(
                "format", "txt",
                "chapterRange", "2-2",
                "config", Map.of("selectedSceneIds", List.of("s2"), "includeMetadata", false)
        ));
        job.put("createdAt", Instant.now().minusSeconds(10));

        ResponseEntity<byte[]> response = controller.download(principal, manuscriptId, UUID.fromString(job.get("id").toString()));
        String text = new String(response.getBody(), StandardCharsets.UTF_8);
        assertTrue(text.contains("第二章"));
        assertTrue(text.contains("hello two"));
        assertFalse(text.contains("第一章"));
        assertFalse(text.contains("hello one"));
    }
}
