package com.ainovel.app.v2;

import com.ainovel.app.story.model.Story;
import com.ainovel.app.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class V2AnalysisControllerTests {

    private V2AccessGuard accessGuard;
    private V2AnalysisPersistenceService persistenceService;
    private V2AnalysisController controller;
    private UserDetails principal;
    private User user;
    private Story story;
    private UUID storyId;

    @BeforeEach
    void setUp() {
        accessGuard = mock(V2AccessGuard.class);
        persistenceService = mock(V2AnalysisPersistenceService.class);
        controller = new V2AnalysisController(accessGuard, persistenceService);

        principal = mock(UserDetails.class);
        user = new User();
        user.setId(UUID.randomUUID());

        story = new Story();
        storyId = UUID.randomUUID();
        story.setId(storyId);

        when(accessGuard.currentUser(principal)).thenReturn(user);
        when(accessGuard.requireOwnedStory(storyId, user)).thenReturn(story);
    }

    @Test
    void triggerContinuityCheckShouldCreateIssueUsingResultReference() {
        Map<String, Object> payload = Map.of(
                "scope", "chapter",
                "scopeReference", "chapter-2",
                "text", "章节衔接前后矛盾"
        );
        UUID reportId = UUID.randomUUID();
        V2AnalysisDtos.AnalysisJobResponse response = new V2AnalysisDtos.AnalysisJobResponse(
                UUID.randomUUID(),
                storyId,
                user.getId(),
                "continuity_check",
                "chapter",
                "chapter-2",
                "completed",
                100,
                "分析完成",
                reportId,
                null,
                Instant.now(),
                Instant.now()
        );
        when(persistenceService.createAnalysisJob(user, story, payload, "continuity_check")).thenReturn(response);

        V2AnalysisDtos.AnalysisJobResponse result = controller.triggerContinuityCheck(principal, storyId, payload);

        assertEquals(reportId, result.resultReference());
        verify(persistenceService).createContinuityIssue(storyId, reportId, "章节衔接前后矛盾");
    }

    @Test
    void listReportsShouldExposeTypedServiceResponses() {
        V2AnalysisDtos.AnalysisReportResponse report = new V2AnalysisDtos.AnalysisReportResponse(
                UUID.randomUUID(),
                storyId,
                user.getId(),
                "full_manuscript",
                null,
                "completed",
                new V2AnalysisDtos.AnalysisSummaryResponse("overall", List.of("角色动机较清晰"), List.of("伏笔回收密度偏低")),
                "分析已完成，可查看建议与风险项。",
                80,
                78,
                82,
                76,
                79,
                81,
                1200,
                Instant.now(),
                Instant.now()
        );
        when(persistenceService.listReports(storyId)).thenReturn(List.of(report));

        List<V2AnalysisDtos.AnalysisReportResponse> reports = controller.listReports(principal, storyId);

        assertEquals(1, reports.size());
        assertEquals("overall", reports.get(0).analysis().focus());
    }
}
