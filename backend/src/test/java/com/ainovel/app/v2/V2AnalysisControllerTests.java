package com.ainovel.app.v2;

import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class V2AnalysisControllerTests {

    private ResourceAccessGuard accessGuard;
    private V2AnalysisPersistenceService persistenceService;
    private V2AnalysisController controller;
    private UserDetails principal;
    private User user;
    private Story story;
    private UUID storyId;

    @BeforeEach
    void setUp() {
        accessGuard = mock(ResourceAccessGuard.class);
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
    void retiredTriggersReturnNotFound() throws Exception {
        var http = MockMvcBuilders.standaloneSetup(controller).build();
        for (String operation : List.of("beta-reader", "continuity-check")) {
            http.perform(post("/v2/stories/" + storyId + "/analysis/" + operation))
                    .andExpect(status().isNotFound());
        }
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
