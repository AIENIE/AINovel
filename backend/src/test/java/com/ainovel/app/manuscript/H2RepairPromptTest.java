package com.ainovel.app.manuscript;
import com.ainovel.app.quality.*;
import com.ainovel.app.ai.*;
import com.ainovel.app.ai.dto.*;
import com.ainovel.app.manuscript.*;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.story.model.*;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class H2RepairPromptTest {
    @Test void judgeAndConservativeRepairKeepWholeControlledContextWithoutRawMetadata() {
        var ai=mock(AiService.class);
        when(ai.chat(any(),any())).thenReturn(new AiChatResponse("assistant","{\"risk_score\":10,\"issues\":[]}",null,0));
        var owner=new User();var story=new Story();story.setTitle("FORBIDDEN_TITLE");story.setSynopsis("FORBIDDEN_SYNOPSIS");
        var outline=new Outline();outline.setStory(story);var manuscript=new Manuscript();manuscript.setOutline(outline);
        var scene=new SceneGenerationContext(UUID.randomUUID(),"FORBIDDEN_CHAPTER","FORBIDDEN_SUMMARY",1,"FORBIDDEN_SCENE","FORBIDDEN_PLAN",1,List.of(),List.of());
        String controlled="合同".repeat(1100)+"END_KNOWLEDGE_BOUNDARY";
        var request=new ScenePlotQualitySupport().buildIsolatedQualityRequest(manuscript,scene,controlled,"候选正文");
        var heuristics=new LocalSlopHeuristics().evaluate(SlopHeuristicInput.from(request,request.candidateText()));
        new AiSlopJudgeClient(ai,new ObjectMapper()).judge(owner,request,heuristics);
        new AiConservativeRevisionService(ai).revise(owner,request,new SlopJudgeResult(10,false,List.of(),"保留"));
        var captured=ArgumentCaptor.forClass(AiChatRequest.class);verify(ai,times(2)).chat(any(),captured.capture());
        captured.getAllValues().forEach(r->{String sent=r.messages().toString();assertTrue(sent.contains(controlled));assertFalse(sent.contains("FORBIDDEN_"));});
    }
}
