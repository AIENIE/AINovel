package com.ainovel.app.quality;

import com.ainovel.app.user.User;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class SlopQualityGate {
    private final LocalSlopHeuristics heuristics;
    private final SlopJudgeClient judgeClient;
    private final ConservativeRevisionService revisionService;
    private final SlopQualityRecorder recorder;

    public SlopQualityGate(LocalSlopHeuristics heuristics,
                           SlopJudgeClient judgeClient,
                           ConservativeRevisionService revisionService,
                           SlopQualityRecorder recorder) {
        this.heuristics = heuristics;
        this.judgeClient = judgeClient;
        this.revisionService = revisionService;
        this.recorder = recorder;
    }

    public SlopQualityResult evaluateAndRepair(User user, SlopQualityRequest request) {
        SlopHeuristicResult heuristicResult = heuristics.evaluate(request.candidateText());
        String acceptedText = request.candidateText();
        boolean revised = false;
        int revisionCount = 0;
        int riskScore = heuristicResult.overallRiskScore();
        SlopSeverity maxSeverity = heuristicResult.maxSeverity();
        List<SlopIssueDraft> issues = new ArrayList<>(heuristicResult.issues());
        SlopQualitySignals signals = SlopQualitySignals.fromIssues(riskScore, maxSeverity, issues);
        String summary = "本地规则低风险，直接接受。";

        if (heuristicResult.requiresAiReview()) {
            SlopJudgeResult judgeResult = safeJudge(user, request, heuristicResult);
            riskScore = Math.max(riskScore, judgeResult.riskScore());
            maxSeverity = maxSeverity(maxSeverity, judgeResult.issues());
            issues = new ArrayList<>(mergeIssues(issues, judgeResult.issues()));
            signals = judgeResult.signals().withDefaults(riskScore, maxSeverity, issues);
            summary = judgeResult.actionableHint() == null || judgeResult.actionableHint().isBlank()
                    ? "AI 诊断建议保守修订。"
                    : judgeResult.actionableHint();

            if (judgeResult.revisionRecommended() && riskScore >= 55) {
                String revisedText = safeRevise(user, request, judgeResult);
                if (revisedText != null && !revisedText.isBlank()) {
                    SlopHeuristicResult revisedHeuristic = heuristics.evaluate(revisedText);
                    if (isSaferRevision(heuristicResult, revisedHeuristic)) {
                        acceptedText = revisedText.trim();
                        revised = true;
                        revisionCount = 1;
                        riskScore = Math.min(riskScore, revisedHeuristic.overallRiskScore());
                        maxSeverity = revisedHeuristic.maxSeverity();
                        issues = new ArrayList<>(revisedHeuristic.issues());
                        signals = SlopQualitySignals.fromIssues(riskScore, maxSeverity, issues);
                        summary = "已执行一次保守修订。";
                    } else {
                        summary = "修订版风险未降低，保留原始候选。";
                    }
                }
            }
        }

        SlopQualityStatus status = statusFor(revised, riskScore, maxSeverity);
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

    private boolean isSaferRevision(SlopHeuristicResult original, SlopHeuristicResult revised) {
        boolean notWorse = revised.overallRiskScore() <= original.overallRiskScore()
                && revised.maxSeverity().ordinal() <= original.maxSeverity().ordinal();
        boolean improved = revised.overallRiskScore() < original.overallRiskScore()
                || revised.maxSeverity().ordinal() < original.maxSeverity().ordinal()
                || revised.issues().size() < original.issues().size();
        return notWorse && improved;
    }

    private SlopJudgeResult safeJudge(User user, SlopQualityRequest request, SlopHeuristicResult heuristicResult) {
        try {
            return judgeClient.judge(user, request, heuristicResult);
        } catch (RuntimeException ex) {
            return new SlopJudgeResult(
                    heuristicResult.overallRiskScore(),
                    false,
                    heuristicResult.issues(),
                    "AI 诊断失败，按本地规则结果处理。"
            );
        }
    }

    private String safeRevise(User user, SlopQualityRequest request, SlopJudgeResult judgeResult) {
        try {
            return revisionService.revise(user, request, judgeResult);
        } catch (RuntimeException ex) {
            return null;
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
