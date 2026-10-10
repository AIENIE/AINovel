package com.ainovel.app.ai;
import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.common.ApiStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiValidationCallBudgetTest {
    @Test void languageBudgetHasIndependentFortyCallAndOneHundredTwentyAttemptLimits() {
        var budgets=mock(AiValidationBudgetRepository.class); var calls=mock(AiValidationCallRepository.class);
        var environment=new MockEnvironment(); environment.setActiveProfiles("local");
        var budget=new AiValidationBudget(); budget.setId("language-budget"); budget.setCallLimit(40); budget.setProviderAttemptLimit(120);
        when(budgets.lock("language-budget")).thenReturn(Optional.of(budget)); when(calls.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
        var service=new AiValidationCallBudget(budgets,calls,new ObjectMapper(),environment);
        ReflectionTestUtils.setField(service,"runId","language-budget"); ReflectionTestUtils.setField(service,"maximumCalls",40);
        ReflectionTestUtils.setField(service,"maximumProviderAttempts",120); ReflectionTestUtils.setField(service,"providerAttemptsPerRpc",3);
        for(int i=0;i<40;i++) service.claimGateway("CHAT",List.of(),"rpc-"+i,"fixed-model");
        assertEquals(40,budget.getUsed()); assertEquals(120,budget.getReservedProviderAttempts());
        assertThrows(ApiStatusException.class,()->service.claimGateway("CHAT",List.of(),"41st","fixed-model"));
        verify(calls,times(40)).saveAndFlush(any());
    }
    @Test void providerReservationsCoverAllKindsAndSurviveRestartWithoutRefund() {
        var budgets=mock(AiValidationBudgetRepository.class);var calls=mock(AiValidationCallRepository.class);
        var env=new MockEnvironment();env.setActiveProfiles("local");
        var budget=new AiValidationBudget();budget.setId("provider-budget");budget.setCallLimit(10);budget.setProviderAttemptLimit(10);
        when(budgets.lock("provider-budget")).thenReturn(Optional.of(budget));
        when(calls.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
        for(String kind:List.of("CHAT","CHAT_STREAM","EMBEDDINGS")) {
            var service=new AiValidationCallBudget(budgets,calls,new ObjectMapper(),env);
            ReflectionTestUtils.setField(service,"runId","provider-budget");
            ReflectionTestUtils.setField(service,"maximumCalls",10);
            assertThrows(ApiStatusException.class,()->service.claimGateway(kind,List.of(),"unknown-bound","model"));
            ReflectionTestUtils.setField(service,"providerAttemptsPerRpc",3);
            service.claimGateway(kind,List.of(),"request-"+kind,"model");
        }
        assertEquals(9,budget.getReservedProviderAttempts());assertEquals(3,budget.getUsed());
        var restarted=new AiValidationCallBudget(budgets,calls,new ObjectMapper(),env);
        ReflectionTestUtils.setField(restarted,"runId","provider-budget");ReflectionTestUtils.setField(restarted,"maximumCalls",10);
        ReflectionTestUtils.setField(restarted,"providerAttemptsPerRpc",3);
        assertThrows(ApiStatusException.class,()->restarted.claimGateway("CHAT",List.of(),"too-many","model"));
        assertEquals(9,budget.getReservedProviderAttempts());verify(calls,times(3)).saveAndFlush(any());
    }
    @Test void explicitTwoHundredLimitSurvivesNewServiceInstanceAndCannotBeRaised() {
        var budgets=mock(AiValidationBudgetRepository.class);var calls=mock(AiValidationCallRepository.class);
        var env=new MockEnvironment();env.setActiveProfiles("local");
        var budget=new AiValidationBudget();budget.setId("h23-test");budget.setCallLimit(200);budget.setUsed(199);
        when(budgets.lock("h23-test")).thenReturn(Optional.of(budget));
        when(calls.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
        var request=new AiChatRequest(List.of(new AiChatRequest.Message("user","测试")),null,null);
        for(int restart=0;restart<2;restart++) {
            var service=new AiValidationCallBudget(budgets,calls,new ObjectMapper(),env);
            ReflectionTestUtils.setField(service,"runId","h23-test");ReflectionTestUtils.setField(service,"maximumCalls",200);
            if(restart==0)service.claim(request,"last-slot","model");
            else assertThrows(ApiStatusException.class,()->service.claim(request,"after-restart","model"));
        }
        assertEquals(200,budget.getUsed());verify(calls,times(1)).saveAndFlush(any());
        var invalid=new AiValidationCallBudget(budgets,calls,new ObjectMapper(),env);
        ReflectionTestUtils.setField(invalid,"runId","h23-test");ReflectionTestUtils.setField(invalid,"maximumCalls",201);
        budget.setCallLimit(201);
        assertThrows(ApiStatusException.class,()->invalid.claim(request,"raise-limit","model"));
    }
    @Test void eachActualRetryConsumesOneSlotAndSeventhAttemptIsRejected() {
        var budgets=mock(AiValidationBudgetRepository.class);var calls=mock(AiValidationCallRepository.class);
        var env=new MockEnvironment();env.setActiveProfiles("local");
        var service=new AiValidationCallBudget(budgets,calls,new ObjectMapper(),env);
        ReflectionTestUtils.setField(service,"runId","h2-test");
        var budget=new AiValidationBudget();budget.setId("h2-test");budget.setCallLimit(6);
        when(budgets.lock("h2-test")).thenReturn(Optional.of(budget));
        when(calls.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
        var request=new AiChatRequest(List.of(new AiChatRequest.Message("user","测试")),null,null);
        for(int i=0;i<6;i++)service.claim(request,"same-logical-request","test-model");
        assertEquals(6,budget.getUsed());
        assertThrows(ApiStatusException.class,()->service.claim(request,"new-request","test-model"));
        verify(calls,times(6)).saveAndFlush(any());
    }
    @Test void disabledBudgetDoesNotTouchDatabaseAndMissingBudgetCannotResetItself() {
        var budgets=mock(AiValidationBudgetRepository.class);var calls=mock(AiValidationCallRepository.class);
        var env=new MockEnvironment();env.setActiveProfiles("local");
        var service=new AiValidationCallBudget(budgets,calls,new ObjectMapper(),env);
        assertNull(service.claim(null,null,"model"));verifyNoInteractions(budgets,calls);
        ReflectionTestUtils.setField(service,"runId","unseeded");
        when(budgets.lock("unseeded")).thenReturn(Optional.empty());
        assertThrows(ApiStatusException.class,()->service.claim(null,null,"model"));
        verify(budgets,never()).save(any());
    }
}
