package com.ainovel.app.quality;
import com.ainovel.app.ai.*;
import com.ainovel.app.ai.dto.*;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiSlopJudgeClientTest {
    static SlopQualityRequest request(String text) { return new SlopQualityRequest(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"故事","类型","文风","章节","场景","目标","冻结的前文","角色声音","风格上下文",text); }
    @Test void sendsFullTextPastAllLegacyBoundariesAndExcludedLocalRiskDoesNotDominate() throws Exception {
        var ai=mock(AiService.class); var json=new ObjectMapper();
        String text="她说：当然可以。"+java.util.stream.IntStream.range(0,7200).mapToObj(i->String.valueOf((char)(0x4e00+i))).collect(java.util.stream.Collectors.joining())+"周明没有交出钥匙。";
        when(ai.chat(any(),any())).thenReturn(new AiChatResponse("assistant","""
            {"complete":true,"evidence_items":[{"judgment":"DISMISSED","kind":"OUTPUT_ARTIFACT","quote":"当然可以","risk_score":88,"risk_explanation":"正常对白"}]}
            """,null,0));
        var local=new LocalSlopHeuristics().evaluate(text);
        var result=new AiSlopJudgeClient(ai,json,com.ainovel.app.quality.SlopInputAdmissionTestSupport.create()).judge(new User(),request(text),local);
        assertEquals(0,result.riskScore()); assertTrue(result.issues().isEmpty());
        var capture=ArgumentCaptor.forClass(AiChatRequest.class); verify(ai).chat(any(),capture.capture());
        assertEquals(text,json.readTree(capture.getValue().messages().get(1).content()).path("text").asText());
        assertEquals("COMPLETE",result.review().coverage);
    }
    @Test void oversizedFullyBuiltRequestNeverCallsAi() {
        var ai=mock(AiService.class); var request=request("雨".repeat(22000));
        assertThrows(SlopInputAdmission.InputLimit.class,()->new AiSlopJudgeClient(ai,new ObjectMapper(),com.ainovel.app.quality.SlopInputAdmissionTestSupport.create()).judge(new User(),request,new LocalSlopHeuristics().evaluate(request.candidateText())));
        verifyNoInteractions(ai);
    }
}
