package com.ainovel.app.ai;
import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.common.ApiStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.*;
import java.math.BigDecimal;
import fireflychat.ai.v1.*;

/** Local acceptance only. Claims survive caller rollback and count each actual gateway attempt. */
@Service
public class AiValidationCallBudget {
    private final AiValidationBudgetRepository budgets;
    private final AiValidationCallRepository calls;
    private final ObjectMapper json;
    private final Environment environment;
    @Value("${app.ai.validation.run-id:}") private String runId="";
    @Value("${app.ai.validation.local-quality-only:false}") private boolean localQualityOnly;
    @Value("${app.ai.validation.maximum-calls:6}") private int maximumCalls=6;
    @Value("${app.ai.validation.maximum-provider-attempts:0}") private int maximumProviderAttempts;
    // Only set after verifying the deployed gateway and client retry bounds.
    @Value("${app.ai.validation.provider-attempts-per-rpc:0}") private int providerAttemptsPerRpc;
    @Value("${app.ai.validation.monetary-enabled:false}") private boolean monetaryEnabled;
    @Value("${app.ai.validation.maximum-cost-cny:0}") private BigDecimal maximumCostCny=BigDecimal.ZERO;
    public boolean monetaryEnabled() { return monetaryEnabled; }
    public boolean localQualityOnly() {
        return localQualityOnly && !runId.isBlank() && Arrays.asList(environment.getActiveProfiles()).contains("local");
    }
    public AiValidationCallBudget(AiValidationBudgetRepository budgets,AiValidationCallRepository calls,ObjectMapper json,Environment environment){
        this.budgets=budgets;this.calls=calls;this.json=json;this.environment=environment;
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public UUID claim(AiChatRequest request,String requestId,String model){
        return claimGateway("CHAT", request == null ? null : request.messages(), requestId, model);
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public UUID claimGateway(String kind,Object input,String requestId,String model){
        if(monetaryEnabled) throw new ApiStatusException(HttpStatus.PRECONDITION_FAILED,"AI_VALIDATION_BUDGET_PATH_REQUIRED");
        if(runId.isBlank())return null;
        if(!Arrays.asList(environment.getActiveProfiles()).contains("local") || !runId.matches("[a-zA-Z0-9_-]{1,80}"))
            throw new IllegalStateException("AI_VALIDATION_LOCAL_ONLY");
        // The dedicated run is seeded before validation starts; never create a replacement budget on failure.
        var budget=budgets.lock(runId).orElseThrow(()->new ApiStatusException(HttpStatus.PRECONDITION_REQUIRED,"AI_VALIDATION_BUDGET_MISSING"));
        int reserved = 0;
        if (budget.getProviderAttemptLimit() != null) {
            if (providerAttemptsPerRpc < 1 || providerAttemptsPerRpc > 10 || budget.getProviderAttemptLimit() < 1
                    || budget.getProviderAttemptLimit() > (maximumProviderAttempts > 0 ? maximumProviderAttempts : maximumCalls))
                throw new ApiStatusException(HttpStatus.PRECONDITION_REQUIRED,"AI_PROVIDER_ATTEMPT_BOUND_UNVERIFIED");
            reserved = providerAttemptsPerRpc;
            if ((long) budget.getReservedProviderAttempts() + reserved > budget.getProviderAttemptLimit())
                throw new ApiStatusException(HttpStatus.TOO_MANY_REQUESTS,"AI_VALIDATION_BUDGET_EXHAUSTED");
        }
        if(maximumCalls<1 || maximumCalls>200 || budget.getCallLimit()>maximumCalls || budget.getCallLimit()<1 || budget.getUsed()>=budget.getCallLimit())
            throw new ApiStatusException(HttpStatus.TOO_MANY_REQUESTS,"AI_VALIDATION_BUDGET_EXHAUSTED");
        budget.setUsed(budget.getUsed()+1);
        budget.setReservedProviderAttempts(budget.getReservedProviderAttempts()+reserved);
        var c=new AiValidationCall();c.setRunId(runId);c.setAttempt(budget.getUsed());c.setRequestId(requestId);c.setModel(model);
        c.setOperationKind(kind);c.setReservedProviderAttempts(reserved);
        c.setRequestJson(write(input));c.setStatus("STARTED");c.setCreatedAt(Instant.now());
        return calls.saveAndFlush(c).getId();
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void complete(UUID id,Object result,boolean succeeded){
        if(id==null)return;
        calls.findById(id).ifPresent(c->{c.setStatus(succeeded?"COMPLETED":"FAILED");c.setResultJson(write(result));});
    }
    public record MoneyRequest(String requestBase64,String grantBase64) {}
    public record MoneyResult(String responseBase64) {}
    public record Claim(AiValidationCall call,boolean fresh) {}
    @Transactional(readOnly=true)
    public Optional<AiValidationCall> existingMoney(String requestId) {
        requireMoneyScope(); return calls.findFirstByRunIdAndRequestIdOrderByAttemptAsc(runId,requestId);
    }
    @Transactional(readOnly=true)
    public List<AiValidationCall> pendingMoney() {
        requireMoneyScope(); return calls.findByRunIdAndSettlementStateIn(runId,List.of("RESERVED","UNKNOWN"));
    }
    /** Quote preparation performs no inference. Counts and maximum currency costs are reserved together. */
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public Claim reserveMoney(ChatCompletionsRequest request,PrepareCallBudgetResponse grant) {
        requireMoneyScope();
        var budget=budgets.lock(runId).orElseThrow(()->denied("AI_VALIDATION_BUDGET_MISSING"));
        var prior=calls.findFirstByRunIdAndRequestIdOrderByAttemptAsc(runId,request.getRequestId());
        if(prior.isPresent()) { requireSameRequest(prior.get(),request); return new Claim(prior.get(),false); }
        if(!"CNY".equals(budget.getCurrency()) || budget.getCostLimit()==null || budget.getCostLimit().signum()<=0
                || budget.getCostLimit().compareTo(maximumCostCny)>0 || budget.getSettledCost().signum()<0 || budget.getReservedCost().signum()<0)
            throw denied("AI_VALIDATION_MONEY_BUDGET_INVALID");
        if(!grant.hasPriceQuote() || !"CNY".equals(grant.getPriceQuote().getCurrency()) || grant.getPriceQuote().getPriceVersionId()<=0
                || !grant.getPriceQuote().getPriceSha256().matches("[0-9a-fA-F]{64}") || grant.getMaxProviderAttempts()!=1
                || grant.getBudgetId().isBlank() || grant.getBudgetCredential().isBlank() || !hash(request).equals(grant.getRequestSha256()))
            throw denied("AI_VALIDATION_PRICE_UNAVAILABLE");
        var quote=grant.getPriceQuote(); BigDecimal ceiling=money(quote.getMaxCost());
        if(maximumCalls<1 || maximumCalls>200 || budget.getCallLimit()<1 || budget.getCallLimit()>maximumCalls
                || budget.getUsed()>=budget.getCallLimit() || budget.getProviderAttemptLimit()==null
                || budget.getProviderAttemptLimit()>maximumCalls || budget.getReservedProviderAttempts()>=budget.getProviderAttemptLimit()
                || budget.getSettledCost().add(budget.getReservedCost()).add(ceiling).compareTo(budget.getCostLimit())>0)
            throw new ApiStatusException(HttpStatus.TOO_MANY_REQUESTS,"AI_VALIDATION_BUDGET_EXHAUSTED");
        budget.setUsed(budget.getUsed()+1); budget.setReservedProviderAttempts(budget.getReservedProviderAttempts()+1);
        budget.setReservedCost(budget.getReservedCost().add(ceiling));
        var call=new AiValidationCall(); call.setRunId(runId); call.setAttempt(budget.getUsed()); call.setRequestId(request.getRequestId()); call.setModel(request.getModel());
        call.setOperationKind("CHAT"); call.setReservedProviderAttempts(1); call.setBudgetId(grant.getBudgetId()); call.setRequestHash(grant.getRequestSha256());
        call.setCurrency("CNY"); call.setPriceVersionId(quote.getPriceVersionId()); call.setPriceSha256(quote.getPriceSha256()); call.setMaxCost(ceiling);
        call.setSettlementState("RESERVED"); call.setStatus("STARTED"); call.setCreatedAt(Instant.now());
        // The credential is needed only for the first send, never for recovery. Persist no credential in the ledger.
        call.setRequestJson(write(new MoneyRequest(Base64.getEncoder().encodeToString(request.toByteArray()),
                Base64.getEncoder().encodeToString(grant.toBuilder().clearBudgetCredential().build().toByteArray()))));
        return new Claim(calls.saveAndFlush(call),true);
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void settleMoney(UUID id,ChatCompletionsResponse result) {
        var budget=budgets.lock(calls.runId(id).orElseThrow()).orElseThrow(); var call=calls.lock(id).orElseThrow();
        if("SETTLED".equals(call.getSettlementState())) return;
        if(!Set.of("RESERVED","UNKNOWN").contains(call.getSettlementState())) throw denied("AI_VALIDATION_SETTLEMENT_STATE_INVALID");
        if(!result.hasUsageAuthoritative() || !result.getUsageAuthoritative() || !result.hasBudgetCharge()) throw denied("AI_VALIDATION_USAGE_UNKNOWN");
        var charge=result.getBudgetCharge(); var ceiling=grant(call);
        BigDecimal cost=money(charge.getTotalCost());
        if(!"CNY".equals(charge.getCurrency()) || !Objects.equals(call.getPriceVersionId(),charge.getPriceVersionId())
                || !call.getPriceSha256().equals(charge.getPriceSha256()) || cost.compareTo(call.getMaxCost())>0
                || result.getPromptTokens()<0 || result.getCompletionTokens()<0 || result.getCacheTokens()<0
                || result.getCacheTokens()>result.getPromptTokens() || result.getPromptTokens()>ceiling.getMaxPromptTokens()
                || result.getCompletionTokens()>ceiling.getMaxCompletionTokens()) throw denied("AI_VALIDATION_SETTLEMENT_MISMATCH");
        budget.setReservedCost(budget.getReservedCost().subtract(call.getMaxCost())); budget.setSettledCost(budget.getSettledCost().add(cost));
        call.setSettledCost(cost); call.setSettlementState("SETTLED"); call.setStatus("COMPLETED");
        call.setResultJson(write(new MoneyResult(Base64.getEncoder().encodeToString(result.toByteArray()))));
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void moneyNotSent(UUID id) {
        var budget=budgets.lock(calls.runId(id).orElseThrow()).orElseThrow(); var call=calls.lock(id).orElseThrow();
        if("NOT_SENT".equals(call.getSettlementState()) || "SETTLED".equals(call.getSettlementState())) return;
        budget.setReservedCost(budget.getReservedCost().subtract(call.getMaxCost()));
        call.setSettledCost(BigDecimal.ZERO); call.setSettlementState("NOT_SENT"); call.setStatus("FAILED_NOT_SENT");
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void moneyUnknown(UUID id) {
        var call=calls.lock(id).orElseThrow();
        if(Set.of("RESERVED","UNKNOWN").contains(call.getSettlementState())) { call.setSettlementState("UNKNOWN"); call.setStatus("RECONCILIATION_REQUIRED"); }
    }
    public void requireSameRequest(AiValidationCall call,ChatCompletionsRequest request) {
        if(!"CNY".equals(call.getCurrency()) || !Objects.equals(call.getRequestHash(),hash(request))) throw denied("AI_VALIDATION_REQUEST_CHANGED");
    }
    public ChatCompletionsRequest request(AiValidationCall call) {
        try { return ChatCompletionsRequest.parseFrom(Base64.getDecoder().decode(json.readValue(call.getRequestJson(),MoneyRequest.class).requestBase64())); }
        catch(Exception e) { throw new IllegalStateException("AI_VALIDATION_SNAPSHOT_INVALID",e); }
    }
    public PrepareCallBudgetResponse grant(AiValidationCall call) {
        try { return PrepareCallBudgetResponse.parseFrom(Base64.getDecoder().decode(json.readValue(call.getRequestJson(),MoneyRequest.class).grantBase64())); }
        catch(Exception e) { throw new IllegalStateException("AI_VALIDATION_SNAPSHOT_INVALID",e); }
    }
    public ChatCompletionsResponse result(AiValidationCall call) {
        try { return ChatCompletionsResponse.parseFrom(Base64.getDecoder().decode(json.readValue(call.getResultJson(),MoneyResult.class).responseBase64())); }
        catch(Exception e) { throw new IllegalStateException("AI_VALIDATION_RESULT_INVALID",e); }
    }
    private void requireMoneyScope() {
        if(!monetaryEnabled || !Arrays.asList(environment.getActiveProfiles()).contains("local") || !runId.matches("[a-zA-Z0-9_-]{1,80}") || maximumCostCny.signum()<=0)
            throw denied("AI_VALIDATION_LOCAL_MONEY_SCOPE_REQUIRED");
    }
    public static String hash(ChatCompletionsRequest request) {
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(request.toByteArray())); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
    private static BigDecimal money(String value) {
        if(value==null || !value.matches("(?:0|[1-9][0-9]*)(?:\\.[0-9]{1,6})?")) throw denied("AI_VALIDATION_PRICE_UNAVAILABLE");
        var amount=new BigDecimal(value); if(amount.precision()-amount.scale()>13) throw denied("AI_VALIDATION_PRICE_UNAVAILABLE"); return amount;
    }
    private static ApiStatusException denied(String message) { return new ApiStatusException(HttpStatus.PRECONDITION_REQUIRED,message); }
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
}
