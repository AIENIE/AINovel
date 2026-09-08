package com.ainovel.app.narrative;

import com.ainovel.app.ai.*;
import com.ainovel.app.ai.dto.*;
import com.ainovel.app.aioperation.*;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NarrativeExtractionHandlerTest {
    @Test void oneCallUsesStableBillingContextAndReplayDoesNotCallModel() throws Exception {
        NarrativeService service = mock(NarrativeService.class); AiService ai = mock(AiService.class);
        User user = new User(); UUID id = UUID.randomUUID(), operation = UUID.randomUUID();
        var input = new NarrativeDtos.ExtractionInput(id, UUID.randomUUID(), UUID.randomUUID(),
                List.of(new NarrativeDtos.Block("b1", "他准备偷钥匙，并未动手。")), List.of(), List.of());
        when(service.input(user, id, operation)).thenReturn(input);
        AiUsageDto usage = new AiUsageDto(10, 20, 0, 0, 1);
        when(ai.chat(eq(user), any(), any())).thenReturn(new AiChatResponse("assistant", "bad format", usage, 5));
        var execution = new AiOperationExecution(operation, user, "{\"extractionId\":\"" + id + "\"}", new ObjectMapper(), mock(AiOperationProgressUpdater.class));
        var handler = new NarrativeExtractionHandler(service, ai);
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
        when(ai.chat(eq(user), any(), any())).thenThrow(new IllegalStateException("timeout"));
        var execution = new AiOperationExecution(operation, user, "{\"extractionId\":\"" + id + "\"}", new ObjectMapper(), mock(AiOperationProgressUpdater.class));
        assertThrows(IllegalStateException.class, () -> new NarrativeExtractionHandler(service, ai).execute(execution));
        verify(service, never()).storeResult(any(), any(), any(), any(), any(), any());
        verify(ai, times(1)).chat(eq(user), any(), any());
    }
}
