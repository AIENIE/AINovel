package com.ainovel.app.quality;

import com.ainovel.app.common.JsonColumnCodec;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.quality.dto.SlopQualityRunDto;
import com.ainovel.app.quality.model.SlopQualityRun;
import com.ainovel.app.quality.repo.SlopQualityRunRepository;
import com.ainovel.app.story.model.Outline;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.story.repo.CharacterCardRepository;
import com.ainovel.app.style.StyleContextProvider;
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

class SlopQualityControllerTest {
    private V2AccessGuard accessGuard;
    private SlopQualityRunRepository runRepository;
    private CharacterCardRepository characterCardRepository;
    private SlopDiagnosticService diagnosticService;
    private StyleContextProvider styleContextProvider;
    private SlopQualityController controller;
    private UserDetails principal;
    private User user;
    private Manuscript manuscript;
    private UUID manuscriptId;
    private UUID sceneId;

    @BeforeEach
    void setUp() {
        accessGuard = mock(V2AccessGuard.class);
        runRepository = mock(SlopQualityRunRepository.class);
        characterCardRepository = mock(CharacterCardRepository.class);
        diagnosticService = mock(SlopDiagnosticService.class);
        styleContextProvider = mock(StyleContextProvider.class);
        controller = new SlopQualityController(accessGuard, runRepository, characterCardRepository, diagnosticService, styleContextProvider, new ObjectMapper(), new JsonColumnCodec(new ObjectMapper()));

        principal = mock(UserDetails.class);
        user = new User();
        user.setId(UUID.randomUUID());
        user.setRemoteUid(9000022L);

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
                {"chapters":[{"title":"第一章","order":1,"scenes":[{"id":"%s","title":"门外","summary":"发现线索","order":1}]}]}
                """.formatted(sceneId));

        manuscriptId = UUID.randomUUID();
        manuscript = new Manuscript();
        manuscript.setId(manuscriptId);
        manuscript.setOutline(outline);
        manuscript.setSectionsJson("{\"" + sceneId + "\":\"<p>空气仿佛凝固。</p>\"}");

        when(accessGuard.currentUser(any())).thenReturn(user);
        when(accessGuard.requireOwnedManuscript(manuscriptId, user)).thenReturn(manuscript);
        when(characterCardRepository.findByStory(story)).thenReturn(List.of());
        when(styleContextProvider.buildSlopContext(story)).thenReturn("Active style profile: 冷峻悬疑画像");
    }

    @Test
    void analyzeSceneShouldBuildManualSlopRequest() {
        SlopQualityRun run = run();
        when(diagnosticService.analyze(eq(user), any(SlopQualityRequest.class))).thenReturn(run);

        SlopQualityRunDto dto = controller.analyzeScene(principal, manuscriptId, sceneId);

        assertEquals(run.getId(), dto.id());
        assertEquals("E2", dto.evidenceLevel());
        assertEquals("high", dto.riskLabel());
        verify(diagnosticService).analyze(eq(user), org.mockito.ArgumentMatchers.<SlopQualityRequest>argThat(request ->
                request.manuscriptId().equals(manuscriptId)
                        && request.sceneId().equals(sceneId)
                        && request.candidateText().contains("空气仿佛凝固")
                        && request.storyTitle().equals("雨城疑案")
                        && request.styleContext().contains("冷峻悬疑画像")
        ));
    }

    private SlopQualityRun run() {
        SlopQualityRun run = new SlopQualityRun();
        run.setId(UUID.randomUUID());
        run.setStoryId(manuscript.getOutline().getStory().getId());
        run.setManuscriptId(manuscriptId);
        run.setSceneId(sceneId);
        run.setStatus(SlopQualityStatus.ACCEPTED_WITH_ISSUES);
        run.setMaxSeverity(SlopSeverity.HIGH);
        run.setOverallRiskScore(72);
        run.setSummary("模板化风险较高");
        run.setAnalysisMode("manual_scene");
        run.setRiskLabel("high");
        run.setEvidenceLevel("E2");
        run.setSafeClaim("该文本呈现 high 级模板化风险，不能证明作者使用 AI。");
        return run;
    }
}
