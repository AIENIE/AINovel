package com.ainovel.app.quality;

import com.ainovel.app.ai.AiUsageContext;
import com.ainovel.app.user.User;
import org.springframework.stereotype.Service;
import java.util.*;

/** A read-only check: even a model-confirmed problem cannot replace the input candidate. */
@Service
public class SlopQualityGate {
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.ainovel.app.ai.AiValidationCallBudget validationBudget;
    private final LocalSlopHeuristics heuristics;
    private final SlopJudgeClient judgeClient;
    private final SlopQualityRecorder recorder;
    public SlopQualityGate(LocalSlopHeuristics heuristics,SlopJudgeClient judgeClient,SlopQualityRecorder recorder) {
        this.heuristics=heuristics; this.judgeClient=judgeClient; this.recorder=recorder;
    }
    public SlopQualityResult evaluate(User user,SlopQualityRequest request) { return evaluate(user,request,null); }
    public SlopQualityResult evaluate(User user,SlopQualityRequest request,AiUsageContext usage) { return check(user,request,usage,false); }
    public SlopQualityResult evaluateLocal(User user,SlopQualityRequest request) { return check(user,request,null,true); }
    private SlopQualityResult check(User user,SlopQualityRequest request,AiUsageContext usage,boolean languageWorkflow) {
        var local=heuristics.evaluate(SlopHeuristicInput.from(request,request.candidateText()));
        var review=new SlopEvidence.ReviewData(); review.localSignals=local.issues(); review.localRisk=local.overallRiskScore();
        review.coverage="LOCAL_ONLY"; review.reason="仅记录本地线索，正文未进行语义检查。";
        int risk=0; List<SlopIssueDraft> issues=List.of(); SlopQualitySignals signals=SlopQualitySignals.empty();
        boolean localOnly=languageWorkflow || validationBudget!=null && validationBudget.localQualityOnly();
        if(local.requiresAiReview() && !localOnly) {
            try {
                var judge=judgeClient.judge(user,request,local,usage==null?null:usage.forOperation("quality-judge"));
                risk=judge.riskScore(); issues=judge.issues(); signals=judge.signals();
                if(judge.review()!=null) review=judge.review();
                else { review.coverage="COMPLETE"; review.reason=judge.actionableHint(); }
            } catch(RuntimeException ex) {
                if(ex instanceof com.ainovel.app.modelconfig.AiPaymentRequiredException || ex instanceof com.ainovel.app.ai.AiResultUncertainException) throw ex;
                review.coverage=ex instanceof SlopInputAdmission.InputLimit?"INPUT_LIMIT":"FAILED";
                review.reason=ex instanceof SlopInputAdmission.InputLimit?ex.getMessage():"语义检查失败，保留本地观察；未检查部分不代表通过。";
            }
        }
        var severity=SlopEvidence.severity(risk);
        var status=!review.coverage.equals("COMPLETE")?SlopQualityStatus.DEGRADED:risk>=70?SlopQualityStatus.ACCEPTED_WITH_ISSUES:SlopQualityStatus.ACCEPTED;
        signals=signals.withShadowHits(local.shadowHits());
        UUID id=recorder.record(new SlopQualityRecord(request,request.candidateText(),risk,severity,false,0,status,issues,review.reason,signals,review));
        return new SlopQualityResult(id,request.candidateText(),risk,severity,false,0,status,issues);
    }
}
