package com.ainovel.app.writingagent;

import com.ainovel.app.assistant.AssistantContext;
import com.ainovel.app.assistant.AssistantDocumentProjection;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.ainovel.app.writingagent.WritingAgentDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WritingAgentQuoteTest {
    @ParameterizedTest
    @ValueSource(ints={2,6})
    void eachQuotedDomainRespectsTheFrozenSharedModelLimit(int maximum) {
        var json=new ObjectMapper().findAndRegisterModules();var store=mock(WritingAgentStore.class);
        when(store.mapper()).thenReturn(json);
        var service=mock(WritingAgentService.class,CALLS_REAL_METHODS);ReflectionTestUtils.setField(service,"store",store);
        var manuscript=UUID.randomUUID();var scene=UUID.randomUUID();var branch=UUID.randomUUID();var user=new User();
        var limits=new Limits(maximum,12,12,3,32);
        var world=new WorldAgentTools.FrozenWorld(WorldAgentTools.VERSION,null,0,false,List.of(),List.of(),List.of(),"world-fingerprint");
        var context=new AssistantContext.Snapshot(false,null,List.of(),List.of(),List.of(),1,500);
        var frozen=new Frozen(PROTOCOL,manuscript,branch,scene,1,"source-hash","完善动作","EDIT","fast",AssistantDocumentProjection.project("<p>原稿</p>",null),
                context,world,List.of(),"",List.of(),"planning",true,true,new BigDecimal("3.000000"),100,limits,null);
        var request=new Request(branch,1L,"<p>原稿</p>",null,"完善动作","EDIT","fast",true,true,new BigDecimal("3.000000"),100L,limits,null,null);
        doReturn(frozen).when(service).prepare(eq(user),eq(manuscript),eq(scene),any());

        var plan=service.quote(user,manuscript,scene,json.valueToTree(request));
        var calls=plan.steps().stream().collect(Collectors.toMap(step->step.featureKey(),step->step.calls()));

        assertEquals(Map.of("writing_agent.control","UP_TO_"+maximum,"scene.fast.draft","UP_TO_1",
                "writing_agent.refine","UP_TO_"+Math.min(3,maximum),"language.diagnose","UP_TO_"+Math.min(4,maximum),
                "plot.diagnose","UP_TO_"+Math.min(4,maximum)),calls);
        assertEquals(maximum,plan.writingInput().frozen().path("limits").path("modelCalls").asInt());
        assertEquals(PROTOCOL,plan.writingInput().frozen().path("protocol").asText());
        assertEquals(12,frozen.limits().controlCalls());
    }
}
