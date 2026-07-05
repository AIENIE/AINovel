package com.ainovel.app.quality;

import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.quality.dto.PlotQualityRunDto;
import com.ainovel.app.quality.model.PlotQualityRun;
import com.ainovel.app.quality.repo.PlotQualityRunRepository;
import com.ainovel.app.story.model.Outline;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.story.repo.CharacterCardRepository;
import com.ainovel.app.user.User;
import com.ainovel.app.v2.V2AccessGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlotQualityControllerTest {
    private V2AccessGuard accessGuard;
    private ManuscriptRepository manuscriptRepository;
    private CharacterCardRepository characterCardRepository;
    private PlotQualityRunRepository runRepository;
    private PlotQualityService service;
    private PlotQualityController controller;
    private UserDetails principal;
    private User user;
    private Manuscript manuscript;
    private UUID manuscriptId;
    private UUID sceneId;

    @BeforeEach
    void setUp() {
        accessGuard = mock(V2AccessGuard.class);
        manuscriptRepository = mock(ManuscriptRepository.class);
        characterCardRepository = mock(CharacterCardRepository.class);
        runRepository = mock(PlotQualityRunRepository.class);
        service = mock(PlotQualityService.class);
        controller = new PlotQualityController(accessGuard, manuscriptRepository, characterCardRepository, runRepository, service, new ObjectMapper());

        principal = mock(UserDetails.class);
        user = new User();
        user.setId(UUID.randomUUID());
        user.setRemoteUid(9000012L);

        Story story = new Story();
        story.setId(UUID.randomUUID());
        story.setUser(user);
        story.setTitle("雨城疑案");
        story.setGenre("悬疑");
        story.setTone("冷峻");

        sceneId = UUID.randomUUID();
        Outline outline = new Outline();
        outline.setStory(story);
        outline.setContentJson("""
                {"planning":{"corePromise":"追查真相"},"chapters":[{"title":"第一章","summary":"雨夜线索","order":1,"scenes":[{"id":"%s","title":"门外","summary":"主角发现异常","order":1}]}]}
                """.formatted(sceneId));

        manuscriptId = UUID.randomUUID();
        manuscript = new Manuscript();
        manuscript.setId(manuscriptId);
        manuscript.setOutline(outline);
        manuscript.setSectionsJson("{\"" + sceneId + "\":\"<p>林烬突然放弃追查。</p>\"}");

        when(accessGuard.currentUser(any())).thenReturn(user);
        when(accessGuard.requireOwnedManuscript(manuscriptId, user)).thenReturn(manuscript);
        when(characterCardRepository.findByStory(story)).thenReturn(List.of());
        when(manuscriptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void analyzeSceneShouldReturnRunDto() {
        PlotQualityRun run = run();
        when(service.analyze(any(), any())).thenReturn(run);

        PlotQualityRunDto dto = controller.analyzeScene(principal, manuscriptId, sceneId);

        assertEquals(run.getId(), dto.id());
        assertEquals(sceneId, dto.sceneId());
        verify(service).analyze(eq(user), any(PlotQualityRequest.class));
    }

    @Test
    void applyRevisionShouldPersistMutatedManuscript() {
        PlotQualityRun run = run();
        UUID runId = run.getId();
        when(service.applyRevision(user, manuscript, runId)).thenReturn(run);

        PlotQualityRunDto dto = controller.applyRevision(principal, manuscriptId, runId);

        assertEquals(runId, dto.id());
        verify(manuscriptRepository).save(manuscript);
    }

    private PlotQualityRun run() {
        PlotQualityRun run = new PlotQualityRun();
        run.setId(UUID.randomUUID());
        run.setStoryId(manuscript.getOutline().getStory().getId());
        run.setManuscriptId(manuscriptId);
        run.setSceneId(sceneId);
        run.setChapterTitle("第一章");
        run.setSceneTitle("门外");
        run.setChapterOrder(1);
        run.setSceneOrder(1);
        run.setStatus(PlotQualityStatus.ACCEPTED_WITH_ISSUES);
        run.setMaxSeverity(PlotQualitySeverity.HIGH);
        run.setOverallRiskScore(78);
        run.setSummary("动机跳变");
        run.setRewritePlanJson("[\"补动机\"]");
        run.setSurgicalFixesJson("[\"补一拍权衡\"]");
        return run;
    }
}
