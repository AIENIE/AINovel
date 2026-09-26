package com.ainovel.app.ai;

import com.ainovel.app.ai.dto.AiChatRequest;
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
    AiResultRecoveryTest() { user.setId(UUID.randomUUID()); user.setRemoteUid(123L); when(admission.requestHash(any())).thenReturn("hash"); }
    @Test void recordedResultReplaysWithoutModelInvocation() {
        when(economy.reserveAiUsage(any(),anyLong(),anyString(),anyString(),eq("stable"),eq("hash")))
                .thenReturn(new EconomyService.AiReservationStart(true,"durable response",1,2,0,1));
        when(economy.currentBalance(user)).thenReturn(new EconomyService.BalanceSnapshot(9,0,9));
        assertEquals("durable response",service.chatWithIdempotency(user,request,"stable").content());
        verifyNoInteractions(gateway);
    }
    @Test void transportFailureRetainsReservationAndRequiresReconciliation() {
        when(economy.reserveAiUsage(any(),anyLong(),anyString(),anyString(),eq("stable"),eq("hash"))).thenReturn(EconomyService.AiReservationStart.reserved());
        when(gateway.chatCompletions(eq("stable"),anyLong(),anyString(),anyList())).thenThrow(new IllegalStateException("unknown delivery"));
        assertThrows(AiResultUncertainException.class,() -> service.chatWithIdempotency(user,request,"stable"));
        verify(economy).markAiResultUncertain(user,"stable"); verify(economy,never()).releaseAiReservation(any(),anyString());
    }
    @Test void resultIsRecordedBeforeSettlementFailureAndNeverReleased() {
        when(economy.reserveAiUsage(any(),anyLong(),anyString(),anyString(),eq("stable"),eq("hash"))).thenReturn(EconomyService.AiReservationStart.reserved());
        when(gateway.chatCompletions(eq("stable"),anyLong(),anyString(),anyList())).thenReturn(new AiGatewayGrpcClient.ChatResult("result","model",1,2,0));
        when(economy.settleAiUsage(any(),eq("stable"),eq("result"),anyLong(),anyLong(),anyLong())).thenThrow(new IllegalStateException("database unavailable"));
        assertThrows(AiResultUncertainException.class,() -> service.chatWithIdempotency(user,request,"stable"));
        var order = inOrder(economy);
        order.verify(economy).reserveAiUsage(any(),anyLong(),anyString(),anyString(),eq("stable"),eq("hash"));
        order.verify(economy).recordAiResult(user,"stable","result",1,2,0);
        order.verify(economy).settleAiUsage(user,"stable","result",1,2,0);
        verify(economy,never()).releaseAiReservation(any(),anyString());
    }
}
