package com.ainovel.app.ai;

import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.ai.dto.AiResponseMetadata;
import com.ainovel.app.economy.EconomyService;
import com.ainovel.app.integration.AiGatewayGrpcClient;
import com.ainovel.app.user.User;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiResultRecoveryTest {
    final AiGatewayGrpcClient gateway = mock(AiGatewayGrpcClient.class);
    final EconomyService economy = mock(EconomyService.class);
    final AiAdmissionGuard admission = mock(AiAdmissionGuard.class);
    final AiService service = new AiService(gateway,economy,admission,2);
    final User user = new User();
    final AiChatRequest request = new AiChatRequest(List.of(new AiChatRequest.Message("user","测试")),null,null);
    AiResultRecoveryTest() { org.springframework.test.util.ReflectionTestUtils.setField(service,"modelPolicy",new AiModelPolicy()); user.setId(UUID.randomUUID()); user.setRemoteUid(123L); when(admission.requestHash(any())).thenReturn("hash"); }
    @Test void recordedResultReplaysWithoutModelInvocation() {
        var metadata=AiResponseMetadata.reported("length",2L,true);
        when(economy.reserveAiUsage(any(),anyLong(),anyString(),anyString(),eq("stable"),eq("hash")))
                .thenReturn(new EconomyService.AiReservationStart(true,"durable response",1,2,0,1,metadata));
        when(economy.currentBalance(user)).thenReturn(new EconomyService.BalanceSnapshot(9,0,9));
        var response=service.chatWithIdempotency(user,request,"stable");
        assertEquals("durable response",response.content());assertEquals(metadata,response.metadata());
        verifyNoInteractions(gateway);
    }
    @Test void transportFailureRetainsReservationAndRequiresReconciliation() {
        when(economy.reserveAiUsage(any(),anyLong(),anyString(),anyString(),eq("stable"),eq("hash"))).thenReturn(EconomyService.AiReservationStart.reserved());
        when(gateway.chatCompletions(eq("stable"),anyLong(),anyString(),anyList())).thenThrow(new IllegalStateException("unknown delivery"));
        assertThrows(AiResultUncertainException.class,() -> service.chatWithIdempotency(user,request,"stable"));
        verify(economy).markAiResultUncertain(user,"stable"); verify(economy,never()).releaseAiReservation(any(),anyString());
    }
    @Test void resultIsRecordedBeforeSettlementFailureAndNeverReleased() {
        var metadata=AiResponseMetadata.reported("length",2L,true);
        when(economy.reserveAiUsage(any(),anyLong(),anyString(),anyString(),eq("stable"),eq("hash"))).thenReturn(EconomyService.AiReservationStart.reserved());
        when(gateway.chatCompletions(eq("stable"),anyLong(),anyString(),anyList())).thenReturn(new AiGatewayGrpcClient.ChatResult("result","model",1,2,0,metadata));
        when(economy.settleAiUsage(any(),eq("stable"),eq("result"),anyLong(),anyLong(),anyLong())).thenThrow(new IllegalStateException("database unavailable"));
        assertThrows(AiResultUncertainException.class,() -> service.chatWithIdempotency(user,request,"stable"));
        var order = inOrder(economy);
        order.verify(economy).reserveAiUsage(any(),anyLong(),anyString(),anyString(),eq("stable"),eq("hash"));
        order.verify(economy).recordAiResult(user,"stable","result",1,2,0,null,metadata);
        order.verify(economy).settleAiUsage(user,"stable","result",1,2,0);
        verify(economy,never()).releaseAiReservation(any(),anyString());
    }
    @Test void nativeResultReadyReplayPreservesPrivateContinuationWithoutInvokingGateway() throws Exception {
        var configuration=mock(com.ainovel.app.modelconfig.AiModelConfigurationService.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"modelConfiguration",configuration);
        var selected=new com.ainovel.app.modelconfig.AiModelSelection("writing_agent.controller","option","model","Model","OFF",null,false,true,"FIXED",1L,null,null,1);
        String envelope="{\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":"+AiNativeProtocolTest.CALLS+",\"provider_state\":\"original-state\"},\"finish_reason\":\"tool_calls\"}";
        var metadata=AiResponseMetadata.reported("tool_calls",5L,true);
        when(configuration.write(any())).thenAnswer(i->new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(i.getArgument(0)));
        when(economy.reserveAiUsage(eq(user),eq(1L),eq("AI_USAGE"),eq("native-replay"),eq("native-replay"),anyString(),eq(selected)))
                .thenReturn(new EconomyService.AiReservationStart(true,"",10,20,0,1,metadata,envelope));
        when(economy.currentBalance(user)).thenReturn(new EconomyService.BalanceSnapshot(9,0,9));
        try(var ignored=com.ainovel.app.modelconfig.AiSelectionContext.install(new com.ainovel.app.modelconfig.AiSelectionContext.Snapshot("frozen-run",Map.of(selected.featureKey(),selected)))) {
            var response=service.chatWithIdempotency(user,request.forFeature(selected.featureKey()).withNativeTools(AiNativeProtocolTest.TOOLS,2048),"native-replay");
            assertEquals(envelope,response.nativeResponseJson());assertEquals("original-state",response.nativeResult().message().providerState());assertEquals(metadata,response.metadata());
        }
        verifyNoInteractions(gateway);
    }
}
