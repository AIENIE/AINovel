package com.ainovel.app.worldcontext;

import com.ainovel.app.assistant.*;
import com.ainovel.app.story.OutlineAiService;
import com.ainovel.app.world.WorldBindingService;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.ainovel.app.worldcontext.WorldContextDtos.*;
import static com.ainovel.app.quality.language.LanguageProjection.hash;

@Service
public class WorldContextPreviewService {
    private final WorldContextPreferences preferences;
    private final WorldBindingService bindings;
    private final WorldContextSelector selector;
    private final OutlineAiService outlineAi;
    private final WritingAssistantService assistant;
    private final ObjectMapper json;
    private final com.ainovel.app.manuscript.SceneGenerationService generation;
    public WorldContextPreviewService(WorldContextPreferences preferences, WorldBindingService bindings,
            WorldContextSelector selector, OutlineAiService outlineAi, WritingAssistantService assistant,
            ObjectMapper json, com.ainovel.app.manuscript.SceneGenerationService generation) {
        this.preferences=preferences; this.bindings=bindings; this.selector=selector; this.outlineAi=outlineAi;
        this.assistant=assistant; this.json=json; this.generation=generation;
    }
    public record Preview(String purpose,String contextHash,Preferences preferences,Selection selection,String compiledContext,List<AssistantDtos.Source> firstSources,List<AssistantDtos.Source> availableSources) {}
    @Transactional(readOnly=true)
    public Preview outlinePreview(com.ainovel.app.user.User user, UUID id, OutlineAiService.Request body) {
        var frozen=outlineAi.prepare(user,id,body);var o=preferences.outline(user,id);
        return new Preview(body.action(),outlineAi.fingerprint(frozen),preferences.read(WorldContextPreferences.scope(o),bindings.resolve(o.getStory(),o,null)),frozen.worldContext(),frozen.prompt().messages().stream().map(m->m.content()).collect(java.util.stream.Collectors.joining("\n\n")),List.of(),List.of());
    }
    @Transactional(readOnly=true)
    public Preview scenePreview(com.ainovel.app.user.User user, UUID id, UUID sceneId, JsonNode body) {
        var m=preferences.manuscript(user,id,sceneId);String purpose=body==null?"SCENE":body.path("purpose").asText("SCENE");
        var binding=bindings.resolve(m);var pref=preferences.read(WorldContextPreferences.scope(m,sceneId),binding);
        if("ASSISTANT".equals(purpose)) {
            var payload=(ObjectNode)body.deepCopy();payload.remove("purpose");
            var request=json.convertValue(payload,AssistantDtos.TurnRequest.class);var frozen=assistant.preview(user,id,sceneId,request);
            boolean author="AUTHOR_QA".equals(frozen.intent());var context=frozen.context();
            var world=context.h2()&&!author?selector.select(binding,pref,"",0,true):context.worldContext();
            var pool=context.h2()&&!author?context.editPool():author?context.authorPool():context.editPool();
            return new Preview(purpose,hash(json.valueToTree(frozen).toString()),pref,world,"",world!=null&&!world.issues().isEmpty()?context.base():assistant.firstReads(frozen).sources(),pool);
        }
        if(!"SCENE".equals(purpose))throw WorldContextSelector.error("WORLD_CONTEXT_PURPOSE_INVALID");
        var mode=body!=null&&"crafted".equals(body.path("mode").asText())?com.ainovel.app.manuscript.GenerationMode.CRAFTED:com.ainovel.app.manuscript.GenerationMode.FAST;
        var frozen=generation.prepare(m,sceneId,mode,bindings.freeze(m));var compiled=frozen.compiled();
        var world=compiled.isolated()?selector.select(binding,pref,"",0,true):compiled.manifest().worldContext();
        return new Preview(purpose,hash(json.valueToTree(frozen).toString()),pref,world,frozen.prompt().messages().stream().map(message->message.content()).collect(java.util.stream.Collectors.joining("\n\n")),List.of(),List.of());
    }
}
