package com.ainovel.app.manuscript;

import com.ainovel.app.aioperation.*;
import com.ainovel.app.modelconfig.AiSelectionContext;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.user.User;
import com.ainovel.app.world.WorldBindingService;
import com.ainovel.app.worldcontext.WorldContextPreferences;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class SceneGenerationAdmission {
    private final ResourceAccessGuard access;
    private final WorldBindingService bindings;
    private final WorldContextPreferences preferences;
    private final SceneGenerationService generation;
    private final AiOperationService operations;
    private final ObjectMapper json;
    public SceneGenerationAdmission(ResourceAccessGuard access,WorldBindingService bindings,WorldContextPreferences preferences,
            SceneGenerationService generation,AiOperationService operations,ObjectMapper json) {
        this.access=access; this.bindings=bindings; this.preferences=preferences;
        this.generation=generation; this.operations=operations; this.json=json;
    }
    @Transactional
    public AiOperationDtos.Accepted submit(User user,UUID manuscriptId,UUID sceneId,String mode,String key) {
        var manuscript=access.requireOwnedManuscript(manuscriptId,user);
        var binding=bindings.lockCurrent(manuscript);
        var quote=AiSelectionContext.current();
        if(quote!=null&&quote.writingInput()!=null) {
            if(quote.worldInput()==null||!bindings.same(quote.worldInput().binding(),binding)
                    ||preferences.read(WorldContextPreferences.scope(manuscript,sceneId),binding).revision()!=quote.writingInput().selectionRevision())
                throw WorldContextPreferences.conflict("WORLD_CONTEXT_CHANGED");
            generation.requireBaseline(manuscript,quote.writingInput().frozen());
        }
        var parsed="crafted".equalsIgnoreCase(mode)?GenerationMode.CRAFTED:GenerationMode.FAST;
        var input=new SceneGenerationService.Admission(generation.languageSelection(manuscript),bindings.input(manuscript));
        var payload=new CoreAiOperationHandler.CorePayload("SCENE_GENERATION",manuscriptId,sceneId,parsed.name(),json.valueToTree(input),4);
        return operations.submit(user,CoreAiOperationHandler.TYPE,"MANUSCRIPT",manuscriptId,payload,5,"准备场景上下文",key);
    }
}
