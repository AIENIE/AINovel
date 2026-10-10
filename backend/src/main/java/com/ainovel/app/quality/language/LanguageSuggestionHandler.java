package com.ainovel.app.quality.language;
import com.ainovel.app.ai.*;
import com.ainovel.app.aioperation.*;
import com.ainovel.app.economy.EconomyService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class LanguageSuggestionHandler implements AiOperationHandler {
    private final LanguageQualityService quality;
    private final AiService ai;
    private final EconomyService economy;
    private final int maxMessage,maxTotal,inputTokens;
    public LanguageSuggestionHandler(LanguageQualityService quality,AiService ai,EconomyService economy,
            @Value("${app.ai.admission.max-message-chars:20000}") int maxMessage,
            @Value("${app.ai.admission.max-total-chars:100000}") int maxTotal,
            @Value("${app.language.input-token-budget:24000}") int inputTokens) {
        this.quality=quality; this.ai=ai; this.economy=economy; this.maxMessage=maxMessage; this.maxTotal=maxTotal; this.inputTokens=inputTokens;
    }
    @Override public String type() { return "LANGUAGE_SUGGESTION"; }
    private UUID id(AiOperationExecution e) throws Exception { return UUID.fromString(e.objectMapper().readTree(e.payloadJson()).path("patchId").asText()); }
    static AiUsageContext usage(UUID patch,String phase) { return usage(patch,phase,LanguageStandard.VERSION); }
    static AiUsageContext usage(UUID patch,String phase,String version) { return new AiUsageContext("LANGUAGE_PATCH",patch.toString(),phase+":"+version); }
    @Override public void recoverExpiredLease(AiOperationExecution e) throws Exception {
        UUID id=id(e); LanguageQualityService.PatchInput input;
        try { input=quality.patchInput(e.user(),id); }
        catch(AiResultUncertainException unavailable) { return; } // The operation service retains RECOVERY_REQUIRED.
        var d=input.data();
        if(!d.status.equals("GENERATING")) return; // NEEDS_CONTEXT never dispatched a reviewer.
        economy.markAiResultUncertain(e.user(),usage(id,d.candidateRaw==null?"candidate":"review",input.standardVersion()).idempotencyKey());
    }
    @Override public Object execute(AiOperationExecution e) throws Exception {
        UUID id=id(e); var input=quality.patchInput(e.user(),id); var d=input.data();
        if(!d.status.equals("GENERATING")) return AiOperationExecutionContext.complete(()->Map.of("patchId",id));
        try {
            if(d.candidateRaw==null) {
                e.progress().step("生成一份局部候选",0,2);
                var request=LanguageAnalysis.request(input.standardVersion(),LanguageAnalysis.REVISE,e.objectMapper().writeValueAsString(Map.of(
                        "original",d.original,"context",d.beforeContext,"issue",input.issue())));
                requireFits(request);
                String raw=ai.chat(e.user(),request,usage(id,"candidate",input.standardVersion())).content();
                AiOperationExecutionContext.apply(()->{ quality.candidate(e.user(),id,raw); return null; });
                d=quality.patchInput(e.user(),id).data();
            }
            if(!d.status.equals("GENERATING")) return AiOperationExecutionContext.complete(()->Map.of("patchId",id));
            e.progress().step("复核原意保留与语言效果",1,2);
            var request=LanguageAnalysis.request(input.standardVersion(),LanguageAnalysis.REVIEW,e.objectMapper().writeValueAsString(Map.of(
                    "original",d.original,"replacement",d.replacement,"context",d.beforeContext,"issue",input.issue())));
            requireFits(request);
            String raw=ai.chat(e.user(),request,usage(id,"review",input.standardVersion())).content();
            return AiOperationExecutionContext.complete(()->quality.reviewed(e.user(),id,raw,null));
        } catch(AiResultUncertainException uncertain) { throw uncertain; }
        catch(RuntimeException ex) {
            if(Thread.currentThread().isInterrupted()) throw ex;
            return AiOperationExecutionContext.complete(()->quality.reviewed(e.user(),id,null,"REVIEW_INCOMPLETE"));
        }
    }
    private void requireFits(com.ainovel.app.ai.dto.AiChatRequest request) {
        if(!LanguageAnalysis.fits(request,maxMessage,maxTotal,inputTokens)) throw new IllegalArgumentException("LANGUAGE_INPUT_BUDGET_EXCEEDED");
    }
}
