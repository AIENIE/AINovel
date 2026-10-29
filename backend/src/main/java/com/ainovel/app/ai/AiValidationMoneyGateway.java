package com.ainovel.app.ai;

import com.ainovel.app.common.ApiStatusException;
import fireflychat.ai.v1.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Explicit local acceptance path. Once reserved, recovery is status-only and never another inference. */
@Component
public class AiValidationMoneyGateway {
    public interface Transport {
        ModelChatCapabilities capabilities(String model);
        PrepareCallBudgetResponse prepare(PrepareCallBudgetRequest request);
        ChatCompletionsResponse send(ChatCompletionsRequest request);
        GetBudgetCallStatusResponse status(GetBudgetCallStatusRequest request);
    }
    private final AiValidationCallBudget ledger;
    private final int outputTokens;
    private final String modelKey;
    public AiValidationMoneyGateway(AiValidationCallBudget ledger,
            @Value("${app.ai.validation.output-token-limit:4096}") int outputTokens,
            @Value("${app.ai.validation.model-key:}") String modelKey) {
        this.ledger=ledger; this.outputTokens=outputTokens; this.modelKey=modelKey;
    }
    public ChatCompletionsResponse call(ChatCompletionsRequest original,Transport transport) {
        return call(original,null,transport);
    }
    /** A run and the acceptance ledger share one exact grant; preparation is never repeated here. */
    public ChatCompletionsResponse callPrepared(ChatCompletionsRequest original,PrepareCallBudgetResponse grant,Transport transport) {
        if(grant==null)throw notSent("AI_VALIDATION_QUOTE_INVALID");
        return call(original,grant,transport);
    }
    private ChatCompletionsResponse call(ChatCompletionsRequest original,PrepareCallBudgetResponse prepared,Transport transport) {
        var request=original.hasMaxOutputTokens()?original:original.toBuilder().setMaxOutputTokens(outputTokens).build();
        AiValidationCallBudget.Claim claim;
        boolean reservedOrPrior=false;
        try {
            var prior=ledger.existingMoney(request.getRequestId());
            if(prior.isPresent()) { reservedOrPrior=true; ledger.requireSameRequest(prior.get(),request); return recover(prior.get(),transport); }
            if(modelKey.isBlank() || !modelKey.equals(request.getModel()) || !request.getImg().isBlank()
                    || request.hasReasoningPrefix() || request.hasBudgetCredential()
                    || !request.hasToolsJson() && request.getMessagesList().stream().anyMatch(m->m.hasToolCallsJson() || m.hasToolCallId() || m.hasReasoningContent() || m.hasProviderState())
                    || request.getMaxOutputTokens()<1) throw notSent("AI_VALIDATION_UNARY_TEXT_ONLY");
            var caps=transport.capabilities(request.getModel());
            if(request.hasToolsJson() && (!caps.getBudgetToolsDeclared() || !caps.getBudgetToolsAvailable()))
                throw notSent("AI_NATIVE_BUDGET_TOOLS_UNAVAILABLE");
            if(!caps.getBudgetDeclared() || !caps.getBudgetAvailable() || caps.getMaxPromptTokens()<1 || caps.getMaxOutputTokens()<request.getMaxOutputTokens())
                throw notSent("AI_VALIDATION_CAPABILITY_UNAVAILABLE");
            var grant=prepared==null?transport.prepare(PrepareCallBudgetRequest.newBuilder().setChat(request).setMaxOutputTokens(request.getMaxOutputTokens())
                    .setMaxProviderAttempts(1).setBillingCurrency("CNY").build()):prepared;
            if(grant.getExpiresAtEpochMs()<=System.currentTimeMillis() || grant.getMaxPromptTokens()<1
                    || grant.getMaxCompletionTokens()!=request.getMaxOutputTokens()
                    || !grant.getRequestSha256().equals(AiValidationCallBudget.hash(request))) throw notSent("AI_VALIDATION_QUOTE_INVALID");
            claim=ledger.reserveMoney(request,grant);
            reservedOrPrior=true;
            if(!claim.fresh()) return recover(claim.call(),transport);
            try {
                var result=transport.send(request.toBuilder().setBudgetCredential(grant.getBudgetCredential()).build());
                ledger.settleMoney(claim.call().getId(),result);
                return result;
            } catch(RuntimeException sentOrUnknown) {
                ledger.moneyUnknown(claim.call().getId());
                return recover(claim.call(),transport);
            }
        } catch(AiResultUncertainException | AiRequestNotSentException e) { throw e; }
        catch(ApiStatusException e) { if(reservedOrPrior) throw new AiResultUncertainException(e); throw new AiRequestNotSentException(e.getStatus(),e.getMessage()); }
        catch(RuntimeException failure) { if(reservedOrPrior) throw new AiResultUncertainException(failure); throw notSent("AI_VALIDATION_PREPARE_UNAVAILABLE"); }
    }
    public ChatCompletionsResponse recover(AiValidationCall call,Transport transport) {
        if("SETTLED".equals(call.getSettlementState())) return ledger.result(call);
        if("NOT_SENT".equals(call.getSettlementState())) throw notSent("AI_VALIDATION_CONFIRMED_NOT_SENT");
        try {
            var original=ledger.request(call);
            var result=transport.status(GetBudgetCallStatusRequest.newBuilder().setBudgetId(call.getBudgetId()).setProjectKey(original.getProjectKey())
                    .setUserId(original.getUserId()).setRequestId(original.getRequestId()).setOperation(BudgetOperation.BUDGET_OPERATION_CHAT).build());
            if(!call.getBudgetId().equals(result.getBudgetId()) || !call.getRequestHash().equals(result.getRequestSha256())) throw new IllegalStateException("AI_VALIDATION_STATUS_MISMATCH");
            if(result.getState()==BudgetCallState.BUDGET_CALL_STATE_FAILED_NOT_SENT) {
                ledger.moneyNotSent(call.getId()); throw notSent("AI_VALIDATION_CONFIRMED_NOT_SENT");
            }
            if(result.getState()==BudgetCallState.BUDGET_CALL_STATE_SUCCEEDED && result.getUsageAuthoritative() && result.hasChat() && result.hasCharge()
                    && result.getChat().hasBudgetCharge() && result.getCharge().equals(result.getChat().getBudgetCharge())) {
                ledger.settleMoney(call.getId(),result.getChat()); return result.getChat();
            }
        } catch(AiRequestNotSentException e) { throw e; }
        catch(RuntimeException unknown) {
            ledger.moneyUnknown(call.getId()); throw new AiResultUncertainException(unknown);
        }
        ledger.moneyUnknown(call.getId()); throw new AiResultUncertainException(new IllegalStateException("AI_VALIDATION_RECONCILIATION_REQUIRED"));
    }
    public void reconcilePending(Transport transport) {
        for(var call:ledger.pendingMoney()) {
            try { recover(call,transport); }
            catch(AiResultUncertainException | AiRequestNotSentException pendingOrNotSent) { /* Retain the durable state; no inference. */ }
        }
    }
    private static AiRequestNotSentException notSent(String code) { return new AiRequestNotSentException(HttpStatus.PRECONDITION_FAILED,code); }
}
