package com.ainovel.app.integration;

import com.ainovel.app.ai.*;
import com.ainovel.app.ai.dto.*;
import com.ainovel.app.modelconfig.AiModelSelection;
import fireflychat.ai.v1.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiNativeGatewayMappingTest {
    private final String tools="[{\"type\":\"function\",\"function\":{\"name\":\"world_search\",\"parameters\":{\"type\":\"object\"}}}]";
    private final AiModelSelection selected=new AiModelSelection("writing_agent.controller","option","model","Model","OFF",null,true,true,"FIXED",1L,null,null,1);
    @Test void nativeMappingPreservesContinuationAndToolOnlyResponseWhileTextOwnerAlsoUsesBudget() {
        var channels=mock(GrpcChannelFactory.class);
        var client=spy(new AiGatewayGrpcClient(new ExternalServiceProperties(),channels));
        doReturn(ModelChatCapabilities.newBuilder().setBudgetToolsDeclared(true).setBudgetToolsAvailable(true).build()).when(client).chatCapabilities("model");
        String calls="[{\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"world_search\",\"arguments\":\"{}\"}}]";
        var original=new AiChatRequest(List.of(new AiChatRequest.Message("user","保留空格  "),
                new AiChatRequest.Message("assistant","  ",null,calls," private reasoning ","opaque"),
                new AiChatRequest.Message("tool","{\"found\":true}","call_1",null,null,null)),null,null).withNativeTools(tools,4096);
        String envelope="{\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":"+calls+",\"provider_state\":\"next-opaque\"},\"finish_reason\":\"tool_calls\"}";
        var sent=new AtomicReference<ChatCompletionsRequest>();
        try(var ignored=AiNativeBudgetContext.open((request,transport,validation)->{
            assertNull(validation);sent.set(request);
            return ChatCompletionsResponse.newBuilder().setContent("").setModelKey("model").setPromptTokens(10).setCompletionTokens(20)
                    .setNativeResponseJson(envelope).setFinishReason("tool_calls").setUsageAuthoritative(true).build();
        })) {
            var result=client.complete("request",1,selected,original,null);
            assertEquals(envelope,result.nativeResponseJson());assertEquals("",result.content());
            assertEquals(AiResponseMetadata.FinishReason.TOOL_CALLS,result.metadata().finishReason());
            assertEquals("保留空格  ",sent.get().getMessages(0).getContent());
            assertEquals("  ",sent.get().getMessages(1).getContent());assertEquals(calls,sent.get().getMessages(1).getToolCallsJson());
            assertEquals(" private reasoning ",sent.get().getMessages(1).getReasoningContent());assertEquals("opaque",sent.get().getMessages(1).getProviderState());
            assertEquals("call_1",sent.get().getMessages(2).getToolCallId());assertEquals(tools,sent.get().getToolsJson());assertEquals(4096,sent.get().getMaxOutputTokens());
            try(var selection=com.ainovel.app.modelconfig.AiSelectionContext.install(new com.ainovel.app.modelconfig.AiSelectionContext.Snapshot("run",Map.of(selected.featureKey(),selected),"scope",Map.of(selected.featureKey(),2048)))) {
                client.complete("worker",1,selected,List.of(new AiChatRequest.Message("user","worker text")),null);
            }
            assertEquals("worker",sent.get().getRequestId());assertFalse(sent.get().hasToolsJson());
            assertEquals(2048,sent.get().getMaxOutputTokens());
        }
        verifyNoInteractions(channels);
    }
    @Test void independentToolsAndBudgetBooleansDoNotAuthorizeTheirCombination() {
        var client=spy(new AiGatewayGrpcClient(new ExternalServiceProperties(),mock(GrpcChannelFactory.class)));
        doReturn(ModelChatCapabilities.newBuilder().setBudgetDeclared(true).setBudgetAvailable(true).setToolsDeclared(true).setSupportsTools(true).build()).when(client).chatCapabilities("model");
        var request=new AiChatRequest(List.of(new AiChatRequest.Message("user","text")),null,null).withNativeTools(tools,4096);
        try(var ignored=AiNativeBudgetContext.open((r,t,v)->{fail("must reject before run preparation");return null;})) {
            assertEquals("AI_NATIVE_BUDGET_TOOLS_UNAVAILABLE",assertThrows(AiRequestNotSentException.class,()->client.complete("blocked",1,selected,request,null)).getMessage());
        }
    }
}
