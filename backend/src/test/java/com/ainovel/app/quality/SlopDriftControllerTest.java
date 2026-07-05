package com.ainovel.app.quality;

import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.quality.dto.SlopDriftRunDto;
import com.ainovel.app.quality.model.SlopDriftRun;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.story.model.Outline;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlopDriftControllerTest {
    private ResourceAccessGuard accessGuard;
    private SlopDriftService service;
    private SlopDriftController controller;
    private UserDetails principal;
    private User user;
    private Manuscript manuscript;
    private UUID manuscriptId;
    private UUID storyId;

    @BeforeEach
    void setUp() {
        accessGuard = mock(ResourceAccessGuard.class);
        service = mock(SlopDriftService.class);
        controller = new SlopDriftController(accessGuard, service);

        principal = mock(UserDetails.class);
        user = new User();
        user.setId(UUID.randomUUID());
        user.setRemoteUid(9000032L);

        storyId = UUID.randomUUID();
        Story story = new Story();
        story.setId(storyId);
        story.setUser(user);

        Outline outline = new Outline();
        outline.setId(UUID.randomUUID());
        outline.setStory(story);

        manuscriptId = UUID.randomUUID();
        manuscript = new Manuscript();
        manuscript.setId(manuscriptId);
        manuscript.setOutline(outline);

        when(accessGuard.currentUser(any())).thenReturn(user);
        when(accessGuard.requireOwnedManuscript(manuscriptId, user)).thenReturn(manuscript);
    }

    @Test
    void analyzeShouldRequireOwnedManuscriptAndReturnDto() {
        SlopDriftRun run = run();
        when(service.analyze(user, manuscript)).thenReturn(run);

        SlopDriftRunDto dto = controller.analyze(principal, manuscriptId);

        assertEquals(run.getId(), dto.id());
        assertEquals("high", dto.riskLabel());
        assertEquals("COMPLETED", dto.status());
        verify(accessGuard).requireOwnedManuscript(manuscriptId, user);
        verify(service).analyze(user, manuscript);
    }

    @Test
    void listShouldReturnRecentRunsForOwnedManuscript() {
        SlopDriftRun run = run();
        when(service.listRuns(manuscriptId)).thenReturn(List.of(run));

        List<SlopDriftRunDto> dtos = controller.list(principal, manuscriptId);

        assertEquals(1, dtos.size());
        assertEquals(run.getId(), dtos.get(0).id());
        verify(accessGuard).requireOwnedManuscript(manuscriptId, user);
        verify(service).listRuns(manuscriptId);
    }

    private SlopDriftRun run() {
        SlopDriftRun run = new SlopDriftRun();
        run.setId(UUID.randomUUID());
        run.setStoryId(storyId);
        run.setManuscriptId(manuscriptId);
        run.setStatus(SlopDriftStatus.COMPLETED);
        run.setOverallRiskScore(76);
        run.setRiskLabel("high");
        run.setSafeClaim("该稿件呈现叙事机制断层风险；这不能证明作者使用 AI。");
        run.setSummary("中后段模板化和事件传送带风险升高。");
        run.setTotalCharacters(25000);
        run.setWindowCount(3);
        run.setWindowSummariesJson("[]");
        run.setMetricCurvesJson("{}");
        run.setDriftPointsJson("[]");
        run.setEvidenceItemsJson("[]");
        run.setAlternativeExplanationsJson("[\"赶稿\"]");
        run.setRewriteTasksJson("[{\"task_id\":\"D1\"}]");
        return run;
    }
}
