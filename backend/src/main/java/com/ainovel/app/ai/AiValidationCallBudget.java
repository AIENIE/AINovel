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
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
}
