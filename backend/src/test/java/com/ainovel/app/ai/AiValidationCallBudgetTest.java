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
