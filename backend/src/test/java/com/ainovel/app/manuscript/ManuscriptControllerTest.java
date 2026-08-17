package com.ainovel.app.manuscript;

import com.ainovel.app.manuscript.dto.SceneGenerationFeedbackPatchRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ManuscriptControllerTest {

    @Test
    void mapsCraftedModeToCraftedGeneration() {
        ManuscriptService service = mock(ManuscriptService.class);
        ManuscriptController controller = controller(service);
        UUID manuscriptId = UUID.randomUUID();
        UUID sceneId = UUID.randomUUID();

        controller.generateSceneForManuscript(manuscriptId, sceneId, "crafted");

        verify(service).generateForScene(manuscriptId, sceneId, GenerationMode.CRAFTED);
    }

    @Test
    void keepsFastModeForUnknownValues() {
        ManuscriptService service = mock(ManuscriptService.class);
        ManuscriptController controller = controller(service);
        UUID manuscriptId = UUID.randomUUID();
        UUID sceneId = UUID.randomUUID();

        controller.generateSceneForManuscript(manuscriptId, sceneId, "unknown");

        verify(service).generateForScene(manuscriptId, sceneId, GenerationMode.FAST);
    }

    @Test
    void generationAttributionEndpointsDelegateWithScopedIds() {
        ManuscriptService service = mock(ManuscriptService.class);
        ManuscriptController controller = controller(service);
        UUID manuscriptId = UUID.randomUUID();
        UUID sceneId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        SceneGenerationFeedbackPatchRequest feedback = new SceneGenerationFeedbackPatchRequest(
                java.util.List.of("PACING"),
                "保留这版",
                true
        );

        controller.listSceneGenerationRuns(manuscriptId, sceneId, 10);
        controller.patchSceneGenerationFeedback(manuscriptId, sceneId, runId, feedback);

        verify(service).listSceneGenerationRuns(manuscriptId, sceneId, 10);
        verify(service).patchSceneGenerationFeedback(manuscriptId, sceneId, runId, feedback);
    }

    private ManuscriptController controller(ManuscriptService service) {
        ManuscriptController controller = new ManuscriptController();
        ReflectionTestUtils.setField(controller, "manuscriptService", service);
        return controller;
    }
}
