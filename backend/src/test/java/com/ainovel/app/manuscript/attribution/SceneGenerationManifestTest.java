package com.ainovel.app.manuscript.attribution;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SceneGenerationManifestTest {

    @Test
    void keepsMetadataAndDropsPromptOrProse() {
        Map<String, Object> manifest = SceneGenerationManifest.sanitize(Map.of(
                "schemaVersion", 1,
                "model", "writer-model",
                "contextEntryIds", List.of("entry-1", "entry-2"),
                "generationParameters", Map.of("temperature", 0.7, "prompt", "hidden"),
                "prompt", "full prompt body",
                "contextText", "source prose"
        ));

        assertEquals("writer-model", manifest.get("model"));
        assertEquals(List.of("entry-1", "entry-2"), manifest.get("contextEntryIds"));
        assertFalse(manifest.containsKey("prompt"));
        assertFalse(manifest.containsKey("contextText"));
        assertEquals(Map.of("temperature", 0.7), manifest.get("generationParameters"));
    }
}
