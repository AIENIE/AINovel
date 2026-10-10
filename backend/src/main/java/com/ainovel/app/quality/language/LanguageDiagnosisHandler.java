package com.ainovel.app.quality.language;
import com.ainovel.app.ai.*;
import com.ainovel.app.aioperation.*;
import com.ainovel.app.economy.EconomyService;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class LanguageDiagnosisHandler implements AiOperationHandler {
    private final LanguageQualityService quality;
    private final AiService ai;
    private final EconomyService economy;
    public LanguageDiagnosisHandler(LanguageQualityService quality,AiService ai,EconomyService economy) { this.quality=quality; this.ai=ai; this.economy=economy; }
    @Override public String type() { return "LANGUAGE_DIAGNOSIS"; }
    private UUID id(AiOperationExecution e) throws Exception { return UUID.fromString(e.objectMapper().readTree(e.payloadJson()).path("reportId").asText()); }
    static AiUsageContext usage(UUID report,int index) { return usage(report,index,LanguageStandard.VERSION); }
    static AiUsageContext usage(UUID report,int index,String version) { return new AiUsageContext("LANGUAGE_CHECK",report.toString(),"chunk-"+index+":"+version); }
    @Override public void recoverExpiredLease(AiOperationExecution e) throws Exception {
        UUID id=id(e); LanguageQualityService.CheckInput input;
        try { input=quality.input(e.user(),id); }
        catch(AiResultUncertainException unavailable) { return; } // Do not prevent the lease from entering recovery.
        for(var c:input.data().coverage) if(c.state().equals("PENDING")) economy.markAiResultUncertain(e.user(),usage(id,c.index(),input.data().source.standardVersion()).idempotencyKey());
    }
    @Override public Object execute(AiOperationExecution e) throws Exception {
        UUID id=id(e); var input=quality.input(e.user(),id);
        for(var c:input.data().coverage) {
            if(!c.state().equals("PENDING")) continue;
            e.progress().step("检查语言范围 "+(c.index()+1)+"/"+input.data().coverage.size(),c.index(),input.data().coverage.size());
            LanguageAnalysis.Parsed parsed=null; String failure=null;
            try {
                String raw=ai.chat(e.user(),LanguageAnalysis.request(input.data().source.standardVersion(),LanguageAnalysis.DIAGNOSE,
                        LanguageAnalysis.input(input.data().projection,c,e.objectMapper())),usage(id,c.index(),input.data().source.standardVersion())).content();
                parsed=LanguageAnalysis.parse(raw,input.data().projection,c,e.objectMapper());
            } catch(AiResultUncertainException uncertain) { throw uncertain; }
            catch(RuntimeException ex) {
                if(Thread.currentThread().isInterrupted()) throw ex;
                failure=ex instanceof IllegalArgumentException?"结果解析或结构校验失败":ex instanceof com.ainovel.app.common.ApiStatusException
                        && "AI_VALIDATION_BUDGET_EXHAUSTED".equals(ex.getMessage())?"预算不足，停止检查":"调用失败，停止检查";
            }
            var result=parsed; var error=failure;
            AiOperationExecutionContext.apply(()->{ quality.checked(e.user(),id,c.index(),result,error); return null; });
            if(failure!=null) break;
        }
        return AiOperationExecutionContext.complete(()->quality.finish(e.user(),id));
    }
}
