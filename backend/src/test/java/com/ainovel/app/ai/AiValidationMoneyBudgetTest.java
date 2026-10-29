package com.ainovel.app.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import fireflychat.ai.v1.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql=false,properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop",
        "app.ai.validation.run-id=money-test","app.ai.validation.monetary-enabled=true","app.ai.validation.maximum-cost-cny=30","app.ai.validation.maximum-calls=24"})
@ActiveProfiles("local")
@Import({AiValidationCallBudget.class,AiValidationMoneyBudgetTest.Beans.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class AiValidationMoneyBudgetTest {
    @TestConfiguration static class Beans { @Bean ObjectMapper json() { return new ObjectMapper(); } }
    @Autowired AiValidationCallBudget ledger;
    @Autowired AiValidationBudgetRepository budgets;
    @Autowired AiValidationCallRepository calls;
    static final String MODEL="gemini-3.8-flash-high", PRICE="a".repeat(64);
    @BeforeEach void seed() {
        for(String field:List.of("phaseStartCalls","phaseMaxCalls","phaseStartCostCny","phaseMaxCostCny"))org.springframework.test.util.ReflectionTestUtils.setField(ledger,field,"");
        calls.deleteAll(); budgets.deleteAll(); var b=new AiValidationBudget();b.setId("money-test");b.setCallLimit(24);b.setProviderAttemptLimit(24);
        b.setCurrency("CNY");b.setCostLimit(new BigDecimal("30"));budgets.saveAndFlush(b);
    }
    static ChatCompletionsRequest request(String id) { return ChatCompletionsRequest.newBuilder().setRequestId(id).setProjectKey("ainovel").setUserId(1).setModel(MODEL)
            .setMaxOutputTokens(4096).addMessages(ChatMessage.newBuilder().setRole("user").setContent("冻结的验收正文")).build(); }
    static PrepareCallBudgetResponse grant(ChatCompletionsRequest r,String cost) { return PrepareCallBudgetResponse.newBuilder().setBudgetId("budget-"+r.getRequestId())
            .setBudgetCredential("ephemeral-test-credential").setRequestSha256(AiValidationCallBudget.hash(r)).setMaxProviderAttempts(1)
            .setMaxPromptTokens(24000).setMaxCompletionTokens(4096).setMaxTotalTokens(28096).setExpiresAtEpochMs(System.currentTimeMillis()+60000)
            .setPriceQuote(BudgetPriceQuote.newBuilder().setCurrency("CNY").setPriceVersionId(7).setPriceSha256(PRICE).setMaxCost(cost)).build(); }
    static ChatCompletionsResponse response(String content) { return ChatCompletionsResponse.newBuilder().setContent(content).setModelKey(MODEL)
            .setUsageAuthoritative(true).setPromptTokens(100).setCompletionTokens(40)
            .setBudgetCharge(BudgetCharge.newBuilder().setCurrency("CNY").setPriceVersionId(7).setPriceSha256(PRICE).setTotalCost("0.200000").setCreditPoints(20)).build(); }
    class Wire implements AiValidationMoneyGateway.Transport {
        int prepares,sends,queries,capReads; boolean priceAvailable=true,capable=true,combinedTools,timeout,notSent,settled; ChatCompletionsRequest frozen;
        ChatCompletionsResponse output=response("候选");
        public ModelChatCapabilities capabilities(String model) { capReads++;return ModelChatCapabilities.newBuilder().setBudgetDeclared(capable).setBudgetAvailable(capable).setToolsDeclared(true).setSupportsTools(true).setBudgetToolsDeclared(combinedTools).setBudgetToolsAvailable(combinedTools).setMaxPromptTokens(24000).setMaxOutputTokens(8192).build(); }
        public PrepareCallBudgetResponse prepare(PrepareCallBudgetRequest r) {
            prepares++; assertEquals("CNY",r.getBillingCurrency()); assertEquals(1,r.getMaxProviderAttempts());frozen=r.getChat();
            var g=grant(frozen,"5.000000");return priceAvailable?g:g.toBuilder().clearPriceQuote().build();
        }
        public ChatCompletionsResponse send(ChatCompletionsRequest r) {
            sends++; assertEquals(frozen,r.toBuilder().clearBudgetCredential().build());
            if(timeout || notSent) throw new IllegalStateException("after-send timeout or gateway policy rejection"); return output;
        }
        public GetBudgetCallStatusResponse status(GetBudgetCallStatusRequest r) {
            queries++;assertEquals(frozen.getRequestId(),r.getRequestId());assertEquals(frozen.getUserId(),r.getUserId());
            var b=GetBudgetCallStatusResponse.newBuilder().setBudgetId("budget-"+r.getRequestId()).setRequestSha256(AiValidationCallBudget.hash(frozen));
            if(notSent) return b.setState(BudgetCallState.BUDGET_CALL_STATE_FAILED_NOT_SENT).build();
            if(settled) return b.setState(BudgetCallState.BUDGET_CALL_STATE_SUCCEEDED).setUsageAuthoritative(true).setChat(output).setCharge(output.getBudgetCharge()).build();
            return b.setState(BudgetCallState.BUDGET_CALL_STATE_RECONCILIATION_REQUIRED).build();
        }
    }
    AiValidationMoneyGateway gateway() { return new AiValidationMoneyGateway(ledger,4096,MODEL); }
    AiValidationBudget budget() { return budgets.findById("money-test").orElseThrow(); }
    void amount(String settled,String reserved) {
        assertEquals(0,new BigDecimal(settled).compareTo(budget().getSettledCost())); assertEquals(0,new BigDecimal(reserved).compareTo(budget().getReservedCost()));
        assertTrue(budget().getSettledCost().add(budget().getReservedCost()).compareTo(new BigDecimal("30"))<=0);
    }
    @Test void concurrentCurrencyBoundaryAndSameRequestReserveAtomically() throws Exception {
        try(var pool=Executors.newFixedThreadPool(8)) {
            var jobs=new ArrayList<Callable<Boolean>>();
            for(int i=0;i<30;i++) { final int n=i; jobs.add(()->{try { return ledger.reserveMoney(request("r"+n),grant(request("r"+n),"10")).fresh(); } catch(com.ainovel.app.common.ApiStatusException e) { return false; }}); }
            long accepted=0;for(var result:pool.invokeAll(jobs)) if(result.get()) accepted++;
            assertEquals(3,accepted);assertEquals(3,budget().getUsed());amount("0","30");
            var first=calls.findAll().getFirst();ledger.moneyUnknown(first.getId());amount("0","30");
            ledger.settleMoney(first.getId(),response(""));amount("0.2","20");
            assertThrows(com.ainovel.app.common.ApiStatusException.class,()->ledger.reserveMoney(request("too-much"),grant(request("too-much"),"10")));
        }
        seed();
        try(var pool=Executors.newFixedThreadPool(8)) {
            var jobs=new ArrayList<Callable<Boolean>>();for(int i=0;i<12;i++) jobs.add(()->ledger.reserveMoney(request("same"),grant(request("same"),"5")).fresh());
            long fresh=0;for(var r:pool.invokeAll(jobs)) if(r.get()) fresh++;assertEquals(1,fresh);assertEquals(1,budget().getUsed());amount("0","5");
        }
    }
    @Test void concurrentReconciliationSettlesAndReleasesOnlyOnce() throws Exception {
        var call=ledger.reserveMoney(request("reconcile"),grant(request("reconcile"),"5")).call();
        try(var pool=Executors.newFixedThreadPool(8)) {
            var jobs=new ArrayList<Callable<Void>>();
            for(int i=0;i<24;i++) jobs.add(()->{ledger.settleMoney(call.getId(),response(""));return null;});
            for(var result:pool.invokeAll(jobs)) result.get();
        }
        amount("0.2","0");assertEquals(1,budget().getUsed());
        ledger.moneyUnknown(call.getId());ledger.moneyNotSent(call.getId());amount("0.2","0");
    }
    @Test void emptyOrUnparseableResponsesStillSettleAndCannotExceedCallLimit() {
        var wire=new Wire();wire.output=response("");
        for(int i=0;i<24;i++) assertEquals("",gateway().call(request("empty-"+i),wire).getContent());
        assertEquals(24,wire.sends);assertEquals(24,budget().getUsed());amount("4.8","0");
        assertThrows(AiRequestNotSentException.class,()->gateway().call(request("twenty-five"),wire));assertEquals(24,wire.sends);
        wire.output=response("not-json");assertEquals("",gateway().call(request("empty-0"),wire).getContent());assertEquals(24,wire.sends);
    }
    @Test void timeoutRestartAndPriceChangesOnlyQueryOriginalRequestAndRetainUnknownCost() {
        var wire=new Wire();wire.timeout=true;
        assertThrows(AiResultUncertainException.class,()->gateway().call(request("unknown"),wire));amount("0","5");
        wire.priceAvailable=false;wire.capable=false;
        assertThrows(AiResultUncertainException.class,()->gateway().call(request("unknown"),wire));assertEquals(1,wire.sends);assertEquals(1,wire.prepares);amount("0","5");
        var saved=calls.findAll().getFirst();assertFalse(saved.getRequestJson().contains("ephemeral-test-credential"));
        assertEquals(request("unknown"),ledger.request(saved));assertTrue(ledger.grant(saved).getBudgetCredential().isEmpty());
        wire.settled=true;wire.output=response("");assertEquals("",gateway().call(request("unknown"),wire).getContent());amount("0.2","0");
        assertEquals(1,budget().getUsed());assertEquals(1,wire.sends);
    }
    @Test void unavailablePriceAndCapabilityNeverSendAndProvenNotSentKeepsCount() {
        var noPrice=new Wire();noPrice.priceAvailable=false;assertThrows(AiRequestNotSentException.class,()->gateway().call(request("price"),noPrice));assertEquals(0,noPrice.sends);amount("0","0");
        var noCap=new Wire();noCap.capable=false;assertThrows(AiRequestNotSentException.class,()->gateway().call(request("capability"),noCap));assertEquals(0,noCap.prepares);
        var rejected=new Wire();rejected.notSent=true;assertThrows(AiRequestNotSentException.class,()->gateway().call(request("policy-change"),rejected));amount("0","0");
        assertEquals(1,budget().getUsed());assertThrows(AiRequestNotSentException.class,()->gateway().call(request("policy-change"),rejected));assertEquals(1,rejected.sends);
        assertThrows(com.ainovel.app.common.ApiStatusException.class,()->ledger.claimGateway("CHAT",List.of(),"bypass",MODEL));
    }
    @Test void nonAuthoritativeOrWrongPriceCannotReleaseTheReservation() {
        var wire=new Wire();wire.output=response("候选").toBuilder().clearUsageAuthoritative().build();
        assertThrows(AiResultUncertainException.class,()->gateway().call(request("no-usage"),wire));amount("0","5");
        wire.output=response("候选").toBuilder().setBudgetCharge(response("").getBudgetCharge().toBuilder().setPriceVersionId(8)).build();wire.settled=true;
        assertThrows(AiResultUncertainException.class,()->gateway().call(request("no-usage"),wire));amount("0","5");assertEquals(1,wire.sends);
    }
    @Test void nativeToolsRequireExplicitCombinedRouteAndKeepOriginalEnvelopeOnReplay() {
        var request=request("native").toBuilder().setToolsJson(AiNativeProtocolTest.TOOLS).build();
        var wire=new Wire();
        assertEquals("AI_NATIVE_BUDGET_TOOLS_UNAVAILABLE",assertThrows(AiRequestNotSentException.class,()->gateway().call(request,wire)).getMessage());
        assertEquals(0,wire.prepares);assertEquals(0,wire.sends);assertEquals(0,budget().getUsed());
        wire.combinedTools=true;
        String envelope="{\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":"+AiNativeProtocolTest.CALLS+",\"provider_state\":\"private-opaque-state\"},\"finish_reason\":\"tool_calls\"}";
        wire.output=response("").toBuilder().setNativeResponseJson(envelope).setFinishReason("tool_calls").setReasoningTokens(5).build();
        var first=gateway().call(request,wire);assertEquals(envelope,first.getNativeResponseJson());
        wire.combinedTools=false;wire.output=response("different");
        var replay=gateway().call(request,wire);assertEquals(first,replay);assertEquals(1,wire.sends);assertEquals(1,wire.prepares);
        amount("0.2","0");assertEquals(1,budget().getUsed());
    }
    @Test void runPreparedGrantIsUsedByBothLedgersWithoutASecondPrepareOrChangedRequest() {
        var request=request("shared-grant").toBuilder().setToolsJson(AiNativeProtocolTest.TOOLS).build();
        var wire=new Wire();wire.combinedTools=true;wire.frozen=request;
        var prepared=grant(request,"5");
        var response=gateway().callPrepared(request,prepared,wire);
        assertEquals(0,wire.prepares);assertEquals(1,wire.sends);
        var stored=calls.findAll().getFirst();assertEquals(prepared.getBudgetId(),stored.getBudgetId());
        assertEquals(request,ledger.request(stored));assertEquals(prepared.toBuilder().clearBudgetCredential().build(),ledger.grant(stored));
        assertEquals(response,gateway().callPrepared(request,prepared,wire));assertEquals(1,wire.sends);
        var different=request("different");
        assertEquals("AI_VALIDATION_QUOTE_INVALID",assertThrows(AiRequestNotSentException.class,()->gateway().callPrepared(different,prepared,wire)).getMessage());
        assertEquals(1,wire.sends);assertEquals(1,budget().getUsed());
    }
    @Test void phaseCallAndMoneyCapsAreSharedAcrossRequestsAndDoNotRewriteTheMainLedger() {
        phase("0","2","0","5");
        var first=ledger.reserveMoney(request("phase-1"),grant(request("phase-1"),"2"));
        ledger.reserveMoney(request("phase-2"),grant(request("phase-2"),"2"));
        assertEquals("AI_VALIDATION_PHASE_BUDGET_EXHAUSTED",assertThrows(com.ainovel.app.common.ApiStatusException.class,()->ledger.reserveMoney(request("phase-3"),grant(request("phase-3"),"0.1"))).getMessage());
        assertFalse(ledger.reserveMoney(request("phase-1"),grant(request("phase-1"),"2")).fresh());
        assertEquals(24,budget().getCallLimit());assertEquals(new BigDecimal("30.000000"),budget().getCostLimit());amount("0","4");
        phase("0","24","0","5");
        assertThrows(com.ainovel.app.common.ApiStatusException.class,()->ledger.reserveMoney(request("phase-cost"),grant(request("phase-cost"),"2")));
        ledger.settleMoney(first.call().getId(),response("saved"));
        assertTrue(ledger.reserveMoney(request("phase-cost"),grant(request("phase-cost"),"2")).fresh());amount("0.2","4");
    }
    @Test void incompleteOrStalePhaseBaselineStopsBeforeCountingAnAttempt() {
        phase("0","24","","5");
        assertEquals("AI_VALIDATION_PHASE_BASELINE_INVALID",assertThrows(com.ainovel.app.common.ApiStatusException.class,()->ledger.reserveMoney(request("phase-invalid"),grant(request("phase-invalid"),"1"))).getMessage());
        phase("38","24","1.187911","5");
        assertThrows(com.ainovel.app.common.ApiStatusException.class,()->ledger.reserveMoney(request("wrong-baseline"),grant(request("wrong-baseline"),"1")));
        assertEquals(0,budget().getUsed());amount("0","0");
    }
    void phase(String start,String calls,String cost,String maximum){
        org.springframework.test.util.ReflectionTestUtils.setField(ledger,"phaseStartCalls",start);
        org.springframework.test.util.ReflectionTestUtils.setField(ledger,"phaseMaxCalls",calls);
        org.springframework.test.util.ReflectionTestUtils.setField(ledger,"phaseStartCostCny",cost);
        org.springframework.test.util.ReflectionTestUtils.setField(ledger,"phaseMaxCostCny",maximum);
    }
}
