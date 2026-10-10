package com.ainovel.app.integration;

import com.ainovel.app.ai.*;
import com.ainovel.app.common.ApiStatusException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AiGatewayValidationBudgetTest {
    @Test void allInferenceTransportsFailClosedBeforeCreatingAChannel() {
        var channels=mock(GrpcChannelFactory.class);
        var budget=mock(AiValidationCallBudget.class);
        var client=new AiGatewayGrpcClient(new ExternalServiceProperties(),channels);
        ReflectionTestUtils.setField(client,"validationBudget",budget);
        when(budget.claimGateway(anyString(),any(),anyString(),anyString()))
                .thenThrow(new ApiStatusException(HttpStatus.TOO_MANY_REQUESTS,"AI_VALIDATION_BUDGET_EXHAUSTED"));
        assertThrows(AiRequestNotSentException.class,()->client.chatCompletions("request",1,"test",List.of()));
        assertThrows(AiRequestNotSentException.class,()->client.chatCompletionsStream("request",1,"test",List.of(),null));
        assertThrows(AiRequestNotSentException.class,()->client.embeddings(1,"test",List.of("text"),true));
        verify(budget).claimGateway(eq("CHAT"),any(),eq("request"),eq("test"));
        verify(budget).claimGateway(eq("CHAT_STREAM"),any(),eq("request"),eq("test"));
        verify(budget).claimGateway(eq("EMBEDDINGS"),any(),anyString(),eq("test"));
        verifyNoInteractions(channels);
        verify(budget,never()).complete(any(),any(),anyBoolean());
    }
}
