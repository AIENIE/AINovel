package com.ainovel.app.manuscript;

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

    private ManuscriptController controller(ManuscriptService service) {
        ManuscriptController controller = new ManuscriptController();
        ReflectionTestUtils.setField(controller, "manuscriptService", service);
        return controller;
    }
}
