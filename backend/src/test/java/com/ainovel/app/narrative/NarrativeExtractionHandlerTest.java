package com.ainovel.app.narrative;

import com.ainovel.app.ai.*;
import com.ainovel.app.ai.dto.*;
import com.ainovel.app.aioperation.*;
import com.ainovel.app.economy.EconomyService;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NarrativeExtractionHandlerTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"narrative-state-p11-v3","narrative-state-p11-v4"})
    void frozenPromptVersionSurvivesUpgrade(String version) throws Exception {
        var service=mock(NarrativeService.class);var ai=mock(AiService.class);
        var user=new User();var id=UUID.randomUUID();var op=UUID.randomUUID();
        when(service.input(user,id,op)).thenReturn(new NarrativeDtos.ExtractionInput(id,UUID.randomUUID(),UUID.randomUUID(),List.of(),List.of(),List.of(),true,version));
        when(ai.chat(eq(user),any(),any())).thenReturn(new AiChatResponse("assistant","{\"candidates\":[]}",new AiUsageDto(10,20,0,0,1),5));
        new NarrativeExtractionHandler(service,ai,mock(EconomyService.class)).execute(new AiOperationExecution(op,user,"{\"extractionId\":\""+id+"\"}",new ObjectMapper(),mock(AiOperationProgressUpdater.class)));
        verify(ai).chat(eq(user),argThat(r->r.messages().get(0).content().contains("版本 "+version+"。")
                && r.messages().get(0).content().contains("acquisitionBasis")==version.endsWith("v4")),any());
    }
    @Test void oneCallUsesStableBillingContextAndReplayDoesNotCallModel() throws Exception {
        NarrativeService service = mock(NarrativeService.class); AiService ai = mock(AiService.class);
        User user = new User(); UUID id = UUID.randomUUID(), operation = UUID.randomUUID();
        var input = new NarrativeDtos.ExtractionInput(id, UUID.randomUUID(), UUID.randomUUID(),
                List.of(new NarrativeDtos.Block("b1", "他准备偷钥匙，并未动手。")), List.of(), List.of());
        when(service.input(user, id, operation)).thenReturn(input);
        AiUsageDto usage = new AiUsageDto(10, 20, 0, 0, 1);
        when(ai.chat(eq(user), any(), any())).thenReturn(new AiChatResponse("assistant", "bad format", usage, 5));
        var execution = new AiOperationExecution(operation, user, "{\"extractionId\":\"" + id + "\"}", new ObjectMapper(), mock(AiOperationProgressUpdater.class));
        var handler = new NarrativeExtractionHandler(service, ai, mock(EconomyService.class));
        handler.execute(execution);
        verify(ai).chat(eq(user), argThat(request -> request.messages().size() == 2 && request.messages().get(1).content().contains("并未动手")), eq(new AiUsageContext("NARRATIVE_EXTRACTION", id.toString(), "p11-v1")));
        verify(service).storeResult(eq(user), eq(id), eq(operation), eq("bad format"), eq(usage), anyString());
        when(service.hasResult(user, id, operation)).thenReturn(true);
        handler.execute(execution);
        verifyNoMoreInteractions(ai);
    }
    @Test void upstreamFailureNeverStoresBusinessResult() {
        NarrativeService service = mock(NarrativeService.class); AiService ai = mock(AiService.class);
        User user = new User(); UUID id = UUID.randomUUID(), operation = UUID.randomUUID();
        when(service.input(user,id,operation)).thenReturn(new NarrativeDtos.ExtractionInput(id,UUID.randomUUID(),UUID.randomUUID(),List.of(),List.of(),List.of()));
        when(ai.chat(eq(user), any(), any())).thenThrow(new IllegalStateException("timeout"));
        var execution = new AiOperationExecution(operation, user, "{\"extractionId\":\"" + id + "\"}", new ObjectMapper(), mock(AiOperationProgressUpdater.class));
        assertThrows(IllegalStateException.class, () -> new NarrativeExtractionHandler(service, ai, mock(EconomyService.class)).execute(execution));
        verify(service, never()).storeResult(any(), any(), any(), any(), any(), any());
        verify(ai, times(1)).chat(eq(user), any(), any());
    }
    @Test void expiredLeaseRetainsUncertainReservationWithoutCallingModel() throws Exception {
        var economy = mock(EconomyService.class);
        var ai = mock(AiService.class);
        var handler = new NarrativeExtractionHandler(mock(NarrativeService.class), ai, economy);
        UUID extraction = UUID.randomUUID();
        User user = new User();
        var execution = new AiOperationExecution(UUID.randomUUID(), user, "{\"extractionId\":\"" + extraction + "\"}",
                new ObjectMapper(), mock(AiOperationProgressUpdater.class));
        handler.recoverExpiredLease(execution);
        verify(economy).markAiResultUncertain(user, new AiUsageContext("NARRATIVE_EXTRACTION", extraction.toString(), "p11-v1").idempotencyKey());
        verifyNoInteractions(ai);
    }
}
