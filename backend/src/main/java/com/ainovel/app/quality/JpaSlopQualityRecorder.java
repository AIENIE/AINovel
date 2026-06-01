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
}
