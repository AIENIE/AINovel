package com.ainovel.app.manuscript;

import com.ainovel.app.ai.*;
import com.ainovel.app.ai.dto.*;
import com.ainovel.app.common.ApiStatusException;
import com.ainovel.app.manuscript.context.*;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.narrative.*;
import com.ainovel.app.prompt.AssembledPrompt;
import com.ainovel.app.story.model.*;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class H2LengthRecoveryTest {
    @Test void retainsBothDraftsAndNeverMakesAThirdCall() throws Exception {
        var json=new ObjectMapper();var service=new SceneGenerationService();
        com.ainovel.app.manuscript.ManuscriptContentTestSupport.injectLegacy(service);
        var ai=mock(AiService.class);var builder=mock(SceneGenerationPromptBuilder.class);
        var compiler=mock(SceneDraftContextCompiler.class);var archive=mock(NarrativeGenerationCandidateStore.class);
        ReflectionTestUtils.setField(service,"aiService",ai);ReflectionTestUtils.setField(service,"sceneGenerationPromptBuilder",builder);
        ReflectionTestUtils.setField(service,"sceneDraftContextCompiler",compiler);ReflectionTestUtils.setField(service,"candidateStore",archive);ReflectionTestUtils.setField(service,"objectMapper",json);
        var user=new User();var story=new Story();story.setUser(user);var outline=new Outline();outline.setStory(story);
        UUID scene=UUID.randomUUID(),mid=UUID.randomUUID(),branch=UUID.randomUUID();
        outline.setContentJson(json.writeValueAsString(Map.of("chapters",List.of(Map.of("id",UUID.randomUUID(),"order",1,"title","章","scenes",List.of(Map.of("id",scene,"order",1,"title","场","planning",Map.of("minHan",600,"maxHan",900))))))));
        var manuscript=new Manuscript();manuscript.setId(mid);manuscript.setOutline(outline);manuscript.setSectionsJson("{}");
        var stamp=new NarrativeContextDtos.Stamp(mid,branch,1,2,3,4,"order");
        var manifest=new SceneDraftContextManifest("scene-isolation-h2-v2","hash","SHA-256",3500,30,null,null,mid,scene,null,1,1,"",List.of(),List.of(),stamp);
        var compiled=new CompiledSceneDraftContext("frozen",manifest,"",List.of(),List.of(),List.of(),"");
        when(compiler.compile(any(),any(),any())).thenReturn(compiled);
        when(builder.build(any(),any(),any(),any(),any(),any(),anyInt(),anyInt(),anyInt(),anyInt(),any(),any())).thenReturn(new AssembledPrompt(List.of(new AiChatRequest.Message("user","frozen")),128000));
        when(ai.chat(any(),any())).thenReturn(new AiChatResponse("assistant","初稿仍太短",null,0),new AiChatResponse("assistant","修正稿仍太短",null,0));
        assertThrows(ApiStatusException.class,()->service.generateSceneSection(manuscript,scene,Map.of(),GenerationMode.FAST));
        verify(ai,times(2)).chat(any(),any());
        verify(archive).retainLengthRejected(eq(stamp),eq(scene),eq("<p>初稿仍太短</p>"),argThat(m->m.attemptCount()==1 && m.contextManifest()==manifest));
        verify(archive).retainLengthRejected(eq(stamp),eq(scene),eq("<p>修正稿仍太短</p>"),argThat(m->m.attemptCount()==2 && m.contextManifest()==manifest));
        assertEquals("{}",manuscript.getSectionsJson());
    }
}
