package com.ainovel.app.manuscript;

import com.ainovel.app.ai.AiAdmissionGuard;
import com.ainovel.app.ai.AiValidationCallBudget;
import com.ainovel.app.manuscript.context.SceneDraftContextCompiler;
import com.ainovel.app.material.evidence.SceneReferenceSelectionService;
import com.ainovel.app.narrative.NarrativeContextService;
import com.ainovel.app.narrative.NarrativeGenerationCandidateStore;
import com.ainovel.app.quality.language.LanguageFeature;
import com.ainovel.app.quality.language.LanguageStandard;
import com.ainovel.app.story.repo.CharacterCardRepository;
import com.ainovel.app.world.WorldBindingService;
import com.ainovel.app.worldcontext.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Explicit collaborators for focused generation tests, using the real context compiler. */
public final class SceneGenerationTestSupport {
    private SceneGenerationTestSupport() {}
    public static void install(SceneGenerationService service, ObjectMapper json, CharacterCardRepository cards) {
        ManuscriptContentTestSupport.injectLegacy(service);
        var language = mock(LanguageFeature.class);
        when(language.selection(any())).thenReturn(new LanguageStandard.Selection(LanguageStandard.VERSION, false, false));
        ReflectionTestUtils.setField(service, "languageFeature", language);
        ReflectionTestUtils.setField(service, "referenceSelection", mock(SceneReferenceSelectionService.class));
        ReflectionTestUtils.setField(service, "validationBudget", mock(AiValidationCallBudget.class));
        ReflectionTestUtils.setField(service, "admissionGuard", mock(AiAdmissionGuard.class));
        ReflectionTestUtils.setField(service, "candidateStore", mock(NarrativeGenerationCandidateStore.class));
        var world = mock(WorldBindingService.class);
        when(world.input(any())).thenReturn(new WorldBindingService.Frozen(null));
        var compiler = new SceneDraftContextCompiler(json, world, cards,
                mock(com.ainovel.app.style.repo.StyleProfileRepository.class),
                mock(com.ainovel.app.style.repo.CharacterVoiceRepository.class),
                mock(com.ainovel.app.v2.V2ContextPersistenceService.class));
        configureCompiler(compiler, world);
        ReflectionTestUtils.setField(service, "sceneDraftContextCompiler", compiler);
    }
    public static void configureCompiler(SceneDraftContextCompiler compiler, WorldBindingService world) {
        ManuscriptContentTestSupport.injectLegacy(compiler);
        ReflectionTestUtils.setField(compiler, "isolation", mock(NarrativeContextService.class));
        var preferences = mock(WorldContextPreferences.class);
        when(preferences.read(any(), any())).thenReturn(WorldContextDtos.Preferences.defaults(null));
        ReflectionTestUtils.setField(compiler, "worldPreferences", preferences);
        var selector = mock(WorldContextSelector.class);
        when(selector.select(any(), any(), anyString(), anyInt(), anyBoolean())).thenAnswer(call -> {
            WorldBindingService.Resolved binding = call.getArgument(0);
            String content = binding == null ? "" : world.render(binding);
            return new WorldContextDtos.Selection("test", binding, 0, "query", "context", call.getArgument(3),
                    SceneDraftContextCompiler.estimateTokens(content), content, List.of(), List.of(), List.of());
        });
        ReflectionTestUtils.setField(compiler, "worldSelector", selector);
    }
}
