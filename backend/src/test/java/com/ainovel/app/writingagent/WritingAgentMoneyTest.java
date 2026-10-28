package com.ainovel.app.writingagent;

import com.ainovel.app.ai.*;
import com.ainovel.app.modelconfig.AiModelSelection;
import fireflychat.ai.v1.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WritingAgentMoneyTest {
    final UUID run=UUID.randomUUID();final String key="stable-step";
    final WritingAgentStore store=mock(WritingAgentStore.class);
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final AiValidationMoneyGateway.Transport wire=mock(AiValidationMoneyGateway.Transport.class);
    final WritingAgentMoney money=new WritingAgentMoney(store);
    final AiModelSelection selected=new AiModelSelection("writing_agent.controller","option","model","Model","OFF",null,false,true,"FIXED",1L,null,null,1);
    final ChatCompletionsRequest request=ChatCompletionsRequest.newBuilder().setRequestId("original-request").setProjectKey("ainovel").setUserId(7).setModel("model").setMaxOutputTokens(4096)
            .addMessages(ChatMessage.newBuilder().setRole("user").setContent("original input")).build();
    WritingAgentMoneyTest(){ReflectionTestUtils.setField(store,"jdbc",jdbc);lenient().when(store.isolated(any())).thenAnswer(i->((Supplier<?>)i.getArgument(0)).get());}
    PrepareCallBudgetResponse grant(){return PrepareCallBudgetResponse.newBuilder().setBudgetId("original-budget").setRequestSha256(AiValidationCallBudget.hash(request)).setMaxPromptTokens(10000).setMaxCompletionTokens(4096).setMaxProviderAttempts(1)
            .setBudgetCredential("first-send-only").setExpiresAtEpochMs(System.currentTimeMillis()+60000)
            .setPriceQuote(BudgetPriceQuote.newBuilder().setCurrency("CNY").setPriceVersionId(1).setPriceSha256("a".repeat(64)).setMaxCost("2.000000")).build();}
    WritingAgentStore.Step step(String result){return new WritingAgentStore.Step(UUID.randomUUID(),run,0,key,"MODEL",selected.featureKey(),"request-hash","{}",null,b64(request.toByteArray()),b64(grant().toBuilder().clearBudgetCredential().build().toByteArray()),result,"RECONCILIATION_REQUIRED",new BigDecimal("2"),null,1,null);}
    GetBudgetCallStatusResponse.Builder status(){return GetBudgetCallStatusResponse.newBuilder().setBudgetId("original-budget").setRequestSha256(AiValidationCallBudget.hash(request));}
    @Test void unknownOriginalStepQueriesOnlyItsOwnIdentityAndNeverReinfers() {
        when(store.step(run,key)).thenReturn(step(null));
        when(wire.status(any())).thenAnswer(i->{
            GetBudgetCallStatusRequest q=i.getArgument(0);assertEquals("original-request",q.getRequestId());assertEquals("original-budget",q.getBudgetId());assertEquals(7,q.getUserId());assertEquals("ainovel",q.getProjectKey());
            return status().setState(BudgetCallState.BUDGET_CALL_STATE_RECONCILIATION_REQUIRED).build();
        });
        assertThrows(AiResultUncertainException.class,()->money.owner(run,key,selected).call(request,wire,null));
        verify(wire,never()).prepare(any());verify(wire,never()).send(any());verifyNoInteractions(jdbc);
    }
    @Test void changedInputAndMismatchingStatusKeepTheOriginalHold() {
        when(store.step(run,key)).thenReturn(step(null));
        assertThrows(AiRequestNotSentException.class,()->money.owner(run,key,selected).call(request.toBuilder().setModel("other").build(),wire,null));
        verifyNoInteractions(wire);
        when(wire.status(any())).thenReturn(status().setBudgetId("another-budget").setState(BudgetCallState.BUDGET_CALL_STATE_FAILED_NOT_SENT).build());
        assertThrows(AiResultUncertainException.class,()->money.recoverIfPresent(run,key,wire));
        verifyNoInteractions(jdbc);verify(wire,never()).send(any());
    }
    @Test void completedNativeReceiptReplaysExactlyWithoutTransportOrNewReservation() {
        String raw="{\"message\":{\"role\":\"assistant\",\"content\":null,\"provider_state\":\"opaque-original\"}}";
        var response=ChatCompletionsResponse.newBuilder().setContent("").setModelKey("model").setNativeResponseJson(raw).setFinishReason("tool_calls").setUsageAuthoritative(true).setReasoningTokens(5).build();
        when(store.step(run,key)).thenReturn(step(b64(response.toByteArray())));
        assertEquals(response,money.owner(run,key,selected).call(request,wire,null));
        verifyNoInteractions(wire,jdbc);
    }
    @Test void fullRunEnvelopeStopsBeforeProviderSend() {
        var fresh=new WritingAgentStore.Step(UUID.randomUUID(),run,0,key,"MODEL",selected.featureKey(),"hash","{}",null,null,null,null,null,BigDecimal.ZERO,null,0,null);
        when(store.step(run,key)).thenReturn(fresh);
        when(wire.prepare(any())).thenReturn(grant());
        when(store.run(run)).thenReturn(new WritingAgentStore.Run(run,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"key","hash","RUNNING",1,null,"{}","{}",new BigDecimal("1"),100,null,Instant.now()));
        when(store.steps(run)).thenReturn(List.of(fresh));
        assertEquals("WRITING_AGENT_BUDGET_EXHAUSTED",assertThrows(AiRequestNotSentException.class,()->money.owner(run,key,selected).call(request,wire,null)).getMessage());
        verify(wire,never()).send(any());verifyNoInteractions(jdbc);
    }
    @Test void acceptanceLedgerProvenNotSentFailureDoesNotBecomeAnUnknownProviderCall() {
        var fresh=new WritingAgentStore.Step(UUID.randomUUID(),run,0,key,"MODEL",selected.featureKey(),"hash","{}",null,null,null,null,null,BigDecimal.ZERO,null,0,null);
        when(store.step(run,key)).thenReturn(fresh);
        when(wire.prepare(any())).thenReturn(grant());
        when(store.run(run)).thenReturn(new WritingAgentStore.Run(run,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"key","hash","RUNNING",1,null,"{}","{}",new BigDecimal("5"),100,null,Instant.now()));
        when(store.steps(run)).thenReturn(List.of(fresh));
        assertEquals("AI_VALIDATION_PHASE_BUDGET_EXHAUSTED",assertThrows(AiRequestNotSentException.class,()->money.owner(run,key,selected).call(request,wire,g->{throw new AiRequestNotSentException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,"AI_VALIDATION_PHASE_BUDGET_EXHAUSTED");})).getMessage());
        verify(wire,never()).send(any());verify(wire,never()).status(any());
    }
    static String b64(byte[] bytes){return Base64.getEncoder().encodeToString(bytes);}
}
