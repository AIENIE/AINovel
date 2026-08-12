package com.ainovel.app.manuscript.context;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CompiledSceneDraftContext(
        String content,
        SceneDraftContextManifest manifest,
        String recentSceneContext,
        List<Map<String, Object>> lorebookEntries,
        List<String> graphRelations,
        List<String> activeCharacters
) {
    public CompiledSceneDraftContext {
        content = content == null ? "" : content;
        recentSceneContext = recentSceneContext == null ? "" : recentSceneContext;
        lorebookEntries = lorebookEntries == null
                ? List.of()
                : lorebookEntries.stream()
                .map(entry -> Collections.unmodifiableMap(new LinkedHashMap<>(entry)))
                .toList();
        graphRelations = graphRelations == null ? List.of() : List.copyOf(graphRelations);
        activeCharacters = activeCharacters == null ? List.of() : List.copyOf(activeCharacters);
    }

    public String compilerVersion() {
        return manifest == null ? "" : manifest.compilerVersion();
    }

    public String contextHash() {
        return manifest == null ? "" : manifest.contextHash();
    }
}
