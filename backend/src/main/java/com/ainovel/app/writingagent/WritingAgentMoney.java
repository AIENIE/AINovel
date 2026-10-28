package com.ainovel.app.writingagent;

import com.ainovel.app.ai.*;
import com.ainovel.app.modelconfig.AiModelSelection;
import fireflychat.ai.v1.*;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import static com.ainovel.app.modelconfig.AiModelConfigurationService.bytes;

/** Every model step owns a separate durable CNY grant inside the run's shared envelope. */
@Component
public class WritingAgentMoney {
    private final WritingAgentStore store;
    public WritingAgentMoney(WritingAgentStore store){this.store=store;}
    public AiNativeBudgetContext.Owner owner(UUID runId,String stepKey,AiModelSelection selected){
        return (request,transport,validation)->call(runId,stepKey,selected,request,transport,validation);
    }
    private ChatCompletionsResponse call(UUID runId,String key,AiModelSelection selected,ChatCompletionsRequest request,
            AiValidationMoneyGateway.Transport transport,Function<PrepareCallBudgetResponse,ChatCompletionsResponse> validation) {
        var old=store.step(runId,key);
        if(old.gatewayRequest()!=null){requireRequest(old,request);return recover(old,transport);}
        final PrepareCallBudgetResponse grant;
        try {
            grant=transport.prepare(PrepareCallBudgetRequest.newBuilder().setChat(request).setBillingCurrency("CNY")
                    .setMaxOutputTokens(request.getMaxOutputTokens()).setMaxProviderAttempts(1).build());
            if(!grant.hasPriceQuote()||!"CNY".equals(grant.getPriceQuote().getCurrency())||grant.getExpiresAtEpochMs()<=System.currentTimeMillis()
                    ||!grant.getRequestSha256().equals(hash(request))||grant.getMaxCompletionTokens()!=request.getMaxOutputTokens()
                    ||grant.getMaxPromptTokens()<1||grant.getMaxProviderAttempts()!=1)throw new IllegalStateException("WRITING_AGENT_BUDGET_INVALID");
        }catch(RuntimeException notSent){throw new AiRequestNotSentException(org.springframework.http.HttpStatus.PRECONDITION_FAILED,"WRITING_AGENT_BUDGET_UNAVAILABLE");}
        BigDecimal maximum=amount(grant.getPriceQuote().getMaxCost());long creditMaximum=selected.cost(grant.getMaxPromptTokens(),grant.getMaxCompletionTokens());
        boolean fresh=store.isolated(()->{
            store.lock(runId);var run=store.run(runId);var prior=store.step(runId,key);
            if(prior.gatewayRequest()!=null){requireRequest(prior,request);return false;}
            if(!"RUNNING".equals(run.status()))throw new AiRequestNotSentException(org.springframework.http.HttpStatus.CONFLICT,"WRITING_AGENT_PAUSED");
            var all=store.steps(runId);BigDecimal committed=BigDecimal.ZERO;long credits=0;
            for(var s:all){committed=committed.add(s.settledCny()==null?s.reservedCny():s.settledCny());credits=Math.addExact(credits,s.settledCredits()==null?s.reservedCredits():s.settledCredits());}
            if(committed.add(maximum).compareTo(run.maxCny())>0||Math.addExact(credits,creditMaximum)>run.maxCredits())
                throw new AiRequestNotSentException(org.springframework.http.HttpStatus.PRECONDITION_REQUIRED,"WRITING_AGENT_BUDGET_EXHAUSTED");
            store.jdbc().update("update writing_agent_steps set gateway_request_base64=?,budget_json=?,settlement_state='RESERVED',reserved_cny=?,reserved_project_credits=? where run_id=? and call_key=?",
                    encode(request.toByteArray()),encode(grant.toBuilder().clearBudgetCredential().build().toByteArray()),maximum,creditMaximum,bytes(runId),key);return true;
        });
        if(!fresh)return recover(store.step(runId,key),transport);
        try {
            var result=validation==null?transport.send(request.toBuilder().setBudgetCredential(grant.getBudgetCredential()).build()):validation.apply(grant);
            settled(store.step(runId,key),result);return result;
        }catch(AiRequestNotSentException notSent){
            store.isolated(()->{store.jdbc().update("update writing_agent_steps set settlement_state='NOT_SENT',reserved_cny=0,reserved_project_credits=0 where run_id=? and call_key=? and gateway_result_base64 is null",bytes(runId),key);return null;});
            throw notSent;
        }catch(RuntimeException uncertain){
            store.isolated(()->{store.jdbc().update("update writing_agent_steps set settlement_state='RECONCILIATION_REQUIRED' where run_id=? and call_key=? and settlement_state<>'SETTLED'",bytes(runId),key);return null;});
            return recover(store.step(runId,key),transport);
        }
    }
    public ChatCompletionsResponse recoverIfPresent(UUID runId,String key,AiValidationMoneyGateway.Transport transport){
        var step=store.step(runId,key);return step==null||step.gatewayRequest()==null?null:recover(step,transport);
    }
    private ChatCompletionsResponse recover(WritingAgentStore.Step step,AiValidationMoneyGateway.Transport transport){
        if(step.gatewayResult()!=null){try{return ChatCompletionsResponse.parseFrom(decode(step.gatewayResult()));}catch(Exception invalid){throw new IllegalStateException("WRITING_AGENT_RECEIPT_INVALID");}}
        try {
            var request=ChatCompletionsRequest.parseFrom(decode(step.gatewayRequest()));var grant=PrepareCallBudgetResponse.parseFrom(decode(step.budget()));
            var status=transport.status(GetBudgetCallStatusRequest.newBuilder().setBudgetId(grant.getBudgetId()).setRequestId(request.getRequestId())
                    .setProjectKey(request.getProjectKey()).setUserId(request.getUserId()).setOperation(BudgetOperation.BUDGET_OPERATION_CHAT).build());
            if(!grant.getBudgetId().equals(status.getBudgetId())||!hash(request).equals(status.getRequestSha256()))throw new IllegalStateException("WRITING_AGENT_RECEIPT_MISMATCH");
            if(status.getState()==BudgetCallState.BUDGET_CALL_STATE_FAILED_NOT_SENT){
                store.isolated(()->{store.jdbc().update("update writing_agent_steps set settlement_state='NOT_SENT',reserved_cny=0,reserved_project_credits=0 where id=?",bytes(step.id()));return null;});
                throw new AiRequestNotSentException(org.springframework.http.HttpStatus.PRECONDITION_FAILED,"WRITING_AGENT_CONFIRMED_NOT_SENT");
            }
            if(status.getState()==BudgetCallState.BUDGET_CALL_STATE_SUCCEEDED&&status.getUsageAuthoritative()&&status.hasChat()&&status.hasCharge()
                    &&status.getChat().hasBudgetCharge()&&status.getCharge().equals(status.getChat().getBudgetCharge())){
                settled(step,status.getChat());return status.getChat();
            }
        }catch(AiRequestNotSentException known){throw known;}
        catch(Exception uncertain){throw new AiResultUncertainException(uncertain);}
        throw new AiResultUncertainException();
    }
    private void settled(WritingAgentStore.Step step,ChatCompletionsResponse response){
        if(!response.hasUsageAuthoritative()||!response.getUsageAuthoritative()||!response.hasBudgetCharge()
                ||response.getPromptTokens()<0||response.getCompletionTokens()<0||response.getCacheTokens()<0||response.getCacheTokens()>response.getPromptTokens())throw new AiResultUncertainException();
        final PrepareCallBudgetResponse grant;try{grant=PrepareCallBudgetResponse.parseFrom(decode(step.budget()));}catch(Exception invalid){throw new IllegalStateException("WRITING_AGENT_GRANT_INVALID");}
        var charge=response.getBudgetCharge();var price=grant.getPriceQuote();BigDecimal cost=amount(charge.getTotalCost());
        if(!"CNY".equals(charge.getCurrency())||charge.getPriceVersionId()!=price.getPriceVersionId()||!charge.getPriceSha256().equals(price.getPriceSha256())
                ||cost.compareTo(step.reservedCny())>0||response.getPromptTokens()>grant.getMaxPromptTokens()||response.getCompletionTokens()>grant.getMaxCompletionTokens())throw new AiResultUncertainException();
        store.isolated(()->{store.jdbc().update("update writing_agent_steps set gateway_result_base64=?,settlement_state='SETTLED',settled_cny=? where id=? and gateway_result_base64 is null",encode(response.toByteArray()),cost,bytes(step.id()));return null;});
    }
    public void retail(UUID run,String key,long credits){store.isolated(()->{store.lock(run);var s=store.step(run,key);if(credits<0||credits>s.reservedCredits()||s.settledCredits()!=null&&s.settledCredits()!=credits)throw new IllegalStateException("WRITING_AGENT_RETAIL_MISMATCH");store.jdbc().update("update writing_agent_steps set settled_project_credits=? where run_id=? and call_key=?",credits,bytes(run),key);return null;});}
    private static void requireRequest(WritingAgentStore.Step prior,ChatCompletionsRequest request){if(!prior.gatewayRequest().equals(encode(request.toByteArray())))throw new AiRequestNotSentException(org.springframework.http.HttpStatus.CONFLICT,"WRITING_AGENT_REQUEST_CHANGED");}
    private static String encode(byte[] bytes){return Base64.getEncoder().encodeToString(bytes);}
    private static byte[] decode(String value){return Base64.getDecoder().decode(value);}
    private static BigDecimal amount(String s){if(s==null||!s.matches("(?:0|[1-9][0-9]*)(?:\\.[0-9]{1,6})?"))throw new IllegalStateException("WRITING_AGENT_PRICE_INVALID");return new BigDecimal(s);}
    private static String hash(ChatCompletionsRequest request){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(request.toByteArray()));}catch(Exception e){throw new IllegalStateException(e);}}
}
