package com.ainovel.app.ai;

import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.common.ApiStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql=false,properties={"app.ai.validation.run-id=concurrent-budget-test","app.ai.validation.maximum-calls=200"})
@ActiveProfiles("local")
@Import({AiValidationCallBudget.class,AiValidationBudgetConcurrencyTest.Beans.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class AiValidationBudgetConcurrencyTest {
    @TestConfiguration static class Beans {@Bean ObjectMapper objectMapper(){return new ObjectMapper();}}
    @Autowired AiValidationCallBudget budget;
    @Autowired AiValidationBudgetRepository budgets;
    @Autowired AiValidationCallRepository calls;
    @Test void concurrentClaimsCannotExceedDurableLimitAndFailureDoesNotRefundCalls() throws Exception {
        var row=new AiValidationBudget();row.setId("concurrent-budget-test");row.setCallLimit(10);budgets.saveAndFlush(row);
        var request=new AiChatRequest(List.of(new AiChatRequest.Message("user","受控测试")),null,null);
        var ids=new ConcurrentLinkedQueue<UUID>();
        try(var pool=Executors.newFixedThreadPool(8)) {
            List<Callable<Boolean>> attempts=new ArrayList<>();
            for(int i=0;i<30;i++)attempts.add(()->{try{ids.add(budget.claim(request,"same-logical-request","test-model"));return true;}catch(ApiStatusException exhausted){return false;}});
            long accepted=0;for(var future:pool.invokeAll(attempts))if(future.get())accepted++;
            assertEquals(10,accepted);
        }
        ids.forEach(id->budget.complete(id,Map.of("errorType","TimeoutException"),false));
        assertEquals(10,budgets.findById(row.getId()).orElseThrow().getUsed());
        assertEquals(10,calls.count());
        assertThrows(ApiStatusException.class,()->budget.claim(request,"after-failure","test-model"));
    }
}
