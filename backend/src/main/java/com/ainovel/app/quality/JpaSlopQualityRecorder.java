package com.ainovel.app.quality;

import com.ainovel.app.quality.model.SlopQualityIssue;
import com.ainovel.app.quality.model.SlopQualityRun;
import com.ainovel.app.quality.repo.SlopQualityRunRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class JpaSlopQualityRecorder implements SlopQualityRecorder {
    private final SlopQualityRunRepository runRepository;

    public JpaSlopQualityRecorder(SlopQualityRunRepository runRepository) {
        this.runRepository = runRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID record(SlopQualityRecord record) {
        SlopQualityRun run = new SlopQualityRun();
        run.setStoryId(record.request().storyId());
        run.setManuscriptId(record.request().manuscriptId());
        run.setSceneId(record.request().sceneId());
        run.setStatus(record.status());
        run.setMaxSeverity(record.maxSeverity());
        run.setOverallRiskScore(record.overallRiskScore());
        run.setRevised(record.revised());
        run.setRevisionCount(record.revisionCount());
        run.setCandidateTextHash(hash(record.request().candidateText()));
        run.setAcceptedTextHash(hash(record.acceptedText()));
        run.setSourceTextHash(hash(record.request().candidateText()));
        run.setAnalysisMode("generation_gate");
        run.setRiskLabel(label(record.overallRiskScore()));
        run.setEvidenceLevel(defaultEvidenceLevel(record.maxSeverity(), record.overallRiskScore()));
        run.setSafeClaim("该文本呈现%s级模板化/slop风险；这不能证明作者使用AI。".formatted(run.getRiskLabel()));
        run.setModuleScoresJson("{}");
        run.setAlternativeExplanationsJson("[\"传统网文俗套\",\"人工低水平写作\",\"平台公式化\",\"作者个人文风\"]");
        run.setRevisionPrioritiesJson("[]");
        run.setRewriteTasksJson("[]");
        run.setSummary(truncate(record.summary(), 800));
        for (SlopIssueDraft draft : record.issues()) {
            SlopQualityIssue issue = new SlopQualityIssue();
            issue.setRun(run);
            issue.setDimension(draft.dimension());
            issue.setSeverity(draft.severity());
            issue.setRiskScore(draft.riskScore());
            issue.setEvidence(truncate(draft.evidence(), 600));
            issue.setWhyItMatters(truncate(draft.whyItMatters(), 800));
            issue.setMinimalFix(truncate(draft.minimalFix(), 800));
            issue.setCharStart(draft.charStart());
            issue.setCharEnd(draft.charEnd());
            issue.setQuote(truncate(draft.quote(), 800));
            issue.setModule(truncate(draft.module(), 80));
            issue.setPatternId(truncate(draft.patternId(), 80));
            issue.setIssueType(truncate(draft.issueType(), 80));
            issue.setEvidenceLevel(truncate(draft.evidenceLevel(), 8));
            issue.setAlternativeExplanationsJson(draft.alternativeExplanationsJson());
            issue.setRepairHint(truncate(draft.repairHint(), 800));
            run.getIssues().add(issue);
        }
        return runRepository.save(run).getId();
    }

    private String hash(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception ex) {
            return null;
        }
    }

    private String truncate(String value, int limit) {
        if (value == null) {
            return null;
        }
        return value.length() <= limit ? value : value.substring(0, limit);
    }

    private String label(int riskScore) {
        if (riskScore >= 85) {
            return "critical";
        }
        if (riskScore >= 70) {
            return "high";
        }
        if (riskScore >= 40) {
            return "medium";
        }
        return "low";
    }

    private String defaultEvidenceLevel(SlopSeverity severity, int riskScore) {
        if (severity == SlopSeverity.BLOCKING || severity == SlopSeverity.HIGH || riskScore >= 70) {
            return "E2";
        }
        return "E1";
    }
}
