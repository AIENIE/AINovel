package com.ainovel.app.quality;

import com.ainovel.app.ai.AiUsageContext;
import com.ainovel.app.user.User;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class SlopQualityGate {
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.ainovel.app.ai.AiValidationCallBudget validationBudget;
    private final LocalSlopHeuristics heuristics;
    private final SlopJudgeClient judgeClient;
    private final SlopQualityRecorder recorder;

    public SlopQualityGate(LocalSlopHeuristics heuristics,
                           SlopJudgeClient judgeClient,
                           SlopQualityRecorder recorder) {
        this.heuristics = heuristics;
        this.judgeClient = judgeClient;
        this.recorder = recorder;
    }

    public SlopQualityResult evaluate(User user, SlopQualityRequest request) {
        return evaluate(user, request, null);
    }

    public SlopQualityResult evaluate(User user, SlopQualityRequest request, AiUsageContext usageContext) {
        return evaluate(user, request, usageContext, false);
    }

    public SlopQualityResult evaluateLocal(User user, SlopQualityRequest request) {
        return evaluate(user, request, null, true);
    }

    private SlopQualityResult evaluate(User user, SlopQualityRequest request, AiUsageContext usageContext, boolean languageWorkflow) {
        SlopHeuristicResult heuristicResult = heuristics.evaluate(SlopHeuristicInput.from(request, request.candidateText()));
        String acceptedText = request.candidateText();
        boolean revised = false;
        int revisionCount = 0;
        int riskScore = heuristicResult.overallRiskScore();
        SlopSeverity maxSeverity = heuristicResult.maxSeverity();
        List<SlopIssueDraft> issues = new ArrayList<>(heuristicResult.issues());
        List<SlopShadowHit> acceptedShadowHits = heuristicResult.shadowHits();
        SlopQualitySignals signals = SlopQualitySignals.fromIssues(riskScore, maxSeverity, issues);
        String summary = "本地规则低风险，直接接受。";

        boolean localOnly = languageWorkflow || validationBudget != null && validationBudget.localQualityOnly();
        if (localOnly) summary = "仅检查本地文本规则；人物知情、剧情和设定仍需作者核对。未调用付费诊断或修订。";
        if (heuristicResult.requiresAiReview() && !localOnly) {
            SlopJudgeResult judgeResult = safeJudge(user, request, heuristicResult,
                    usageContext == null ? null : usageContext.forOperation("quality-judge"));
            riskScore = Math.max(riskScore, judgeResult.riskScore());
            maxSeverity = maxSeverity(maxSeverity, judgeResult.issues());
            issues = new ArrayList<>(mergeIssues(issues, judgeResult.issues()));
            signals = judgeResult.signals().withDefaults(riskScore, maxSeverity, issues);
            summary = judgeResult.actionableHint() == null || judgeResult.actionableHint().isBlank()
                    ? "AI 诊断建议保守修订。"
                    : judgeResult.actionableHint();

        }

        SlopQualityStatus status = statusFor(revised, riskScore, maxSeverity);
        signals = signals.withShadowHits(acceptedShadowHits);
        UUID runId = recorder.record(new SlopQualityRecord(
                request,
                acceptedText,
                riskScore,
                maxSeverity,
                revised,
                revisionCount,
                status,
                List.copyOf(issues),
                summary,
                signals
        ));
        return new SlopQualityResult(runId, acceptedText, riskScore, maxSeverity, revised, revisionCount, status, List.copyOf(issues));
    }

    private SlopJudgeResult safeJudge(User user,
                                      SlopQualityRequest request,
                                      SlopHeuristicResult heuristicResult,
                                      AiUsageContext usageContext) {
        try {
            return judgeClient.judge(user, request, heuristicResult, usageContext);
        } catch (RuntimeException ex) {
            if (ex instanceof com.ainovel.app.modelconfig.AiPaymentRequiredException) throw ex;
            if (ex instanceof com.ainovel.app.ai.AiResultUncertainException) throw ex;
            return new SlopJudgeResult(
                    heuristicResult.overallRiskScore(),
                    false,
                    heuristicResult.issues(),
                    "AI 诊断失败，按本地规则结果处理。"
            );
        }
    }

    private List<SlopIssueDraft> mergeIssues(List<SlopIssueDraft> left, List<SlopIssueDraft> right) {
        List<SlopIssueDraft> merged = new ArrayList<>(left);
        if (right != null) {
            merged.addAll(right);
        }
        return merged;
    }

    private SlopSeverity maxSeverity(SlopSeverity current, List<SlopIssueDraft> issues) {
        SlopSeverity max = current;
        if (issues == null) {
            return max;
        }
        for (SlopIssueDraft issue : issues) {
            if (issue.severity().ordinal() > max.ordinal()) {
                max = issue.severity();
            }
        }
        return max;
    }

    private SlopQualityStatus statusFor(boolean revised, int riskScore, SlopSeverity maxSeverity) {
        if (revised) {
            return SlopQualityStatus.REVISED;
        }
        if (riskScore >= 70 || maxSeverity == SlopSeverity.HIGH || maxSeverity == SlopSeverity.BLOCKING) {
            return SlopQualityStatus.ACCEPTED_WITH_ISSUES;
        }
        return SlopQualityStatus.ACCEPTED;
    }
}
