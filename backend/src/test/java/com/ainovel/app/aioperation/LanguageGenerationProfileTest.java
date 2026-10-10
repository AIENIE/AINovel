package com.ainovel.app.aioperation;

import com.ainovel.app.ai.AiResultUncertainException;
import com.ainovel.app.manuscript.*;
import com.ainovel.app.quality.*;
import com.ainovel.app.quality.language.LanguageStandard;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.story.*;
import com.ainovel.app.user.User;
import com.ainovel.app.world.WorldService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LanguageGenerationProfileTest {
    final ManuscriptService manuscripts=mock(ManuscriptService.class);
    final CoreAiOperationHandler handler=new CoreAiOperationHandler(mock(StoryService.class),mock(OutlineService.class),mock(WorldService.class),
            manuscripts,mock(ResourceAccessGuard.class),mock(SlopDiagnosticService.class),mock(SlopDriftService.class),mock(PlotQualityService.class));
    final ObjectMapper json=new ObjectMapper();
    AiOperationExecution execution(CoreAiOperationHandler.CorePayload payload) throws Exception {
        return new AiOperationExecution(UUID.randomUUID(),new User(),json.writeValueAsString(payload),json,(a,b,c)->{});
    }
    @Test void executionUsesFrozenSelectionRatherThanCurrentGlobalVersion() throws Exception {
        var selection=new LanguageStandard.Selection("zh-naturalness-v2",true,false);
        var m=UUID.randomUUID();var scene=UUID.randomUUID();
        handler.execute(execution(new CoreAiOperationHandler.CorePayload("SCENE_GENERATION",m,scene,"FAST",json.valueToTree(selection))));
        verify(manuscripts).generateForScene(m,scene,GenerationMode.FAST,selection);
    }
    @Test void oldUnknownPayloadCannotSilentlyStartCurrentProfile() throws Exception {
        assertThrows(AiResultUncertainException.class,()->handler.execute(execution(
                new CoreAiOperationHandler.CorePayload("SCENE_GENERATION",UUID.randomUUID(),UUID.randomUUID(),"FAST",null))));
        verifyNoInteractions(manuscripts);
    }
}
