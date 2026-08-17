package com.ainovel.app.g2evaluation;

import com.ainovel.app.common.SafeLogThrowable;
import com.ainovel.app.common.BusinessException;
import com.ainovel.app.economy.EconomyService;
import com.ainovel.app.g2evaluation.model.G2EvaluationSample;
import com.ainovel.app.g2evaluation.model.G2EvaluationSampleStatus;
import com.ainovel.app.g2evaluation.repo.G2EvaluationSampleRepository;
import com.ainovel.app.manuscript.SceneGenerationService;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

@Service
public class G2EvaluationGenerationWorker {
    private static final Logger log = LoggerFactory.getLogger(G2EvaluationGenerationWorker.class);

    private final G2EvaluationSampleRepository sampleRepository;
    private final ManuscriptRepository manuscriptRepository;
    private final SceneGenerationService sceneGenerationService;
    private final EconomyService economyService;
    private final TransactionTemplate transactions;
    private final String leaseOwner = UUID.randomUUID().toString();
    private static final java.time.Duration LEASE_DURATION = java.time.Duration.ofMinutes(15);

    public G2EvaluationGenerationWorker(G2EvaluationSampleRepository sampleRepository,
                                        ManuscriptRepository manuscriptRepository,
                                        SceneGenerationService sceneGenerationService,
                                        EconomyService economyService,
                                        TransactionTemplate transactions) {
        this.sampleRepository = sampleRepository;
        this.manuscriptRepository = manuscriptRepository;
        this.sceneGenerationService = sceneGenerationService;
        this.economyService = economyService;
        this.transactions = transactions;
    }

    public void generate(UUID sampleId) {
        Claim claim = transactions.execute(status -> {
            G2EvaluationSample sample = sampleRepository.findById(sampleId).orElse(null);
            if (sample == null || sample.getStatus() != G2EvaluationSampleStatus.PENDING) return null;
            sample.setStatus(G2EvaluationSampleStatus.RUNNING);
            sample.setLeaseOwner(leaseOwner);
            sample.setLeaseExpiresAt(Instant.now().plus(LEASE_DURATION));
            sample.setAttemptCount(sample.getAttemptCount() + 1);
            sample.getAuthor().getUsername();
            return new Claim(sample.getManuscriptId(), sample.getSceneId(), sample.getAuthor(),
                    sample.getFastText(), sample.getCraftedText());
        });
        if (claim == null) return;
        try {
            Manuscript manuscript = manuscriptRepository.findWithStoryById(claim.manuscriptId())
                    .orElseThrow(() -> new BusinessException("盲测稿件不存在"));
            if (manuscript.getOutline() == null || manuscript.getOutline().getStory() == null
                    || manuscript.getOutline().getStory().getUser() == null
                    || !claim.author().getId().equals(manuscript.getOutline().getStory().getUser().getId())) {
                throw new BusinessException("盲测稿件不属于投稿作者");
            }
            if (claim.fastText() == null || claim.fastText().isBlank()) {
                String fast = sceneGenerationService.generateEvaluationCandidate(
                        manuscript, claim.sceneId(), sampleId, com.ainovel.app.manuscript.GenerationMode.FAST);
                persistCandidate(sampleId, true, fast);
            }
            if (claim.craftedText() == null || claim.craftedText().isBlank()) {
                String crafted = sceneGenerationService.generateEvaluationCandidate(
                        manuscript, claim.sceneId(), sampleId, com.ainovel.app.manuscript.GenerationMode.CRAFTED);
                persistCandidate(sampleId, false, crafted);
            }
            transactions.executeWithoutResult(status -> sampleRepository.findById(sampleId).ifPresent(sample -> {
                if (!leaseOwner.equals(sample.getLeaseOwner())) return;
                sample.setStatus(G2EvaluationSampleStatus.READY);
                clearLease(sample);
            }));
            log.info("G2 evaluation sample generated sampleId={}", sampleId);
        } catch (RuntimeException ex) {
            transactions.executeWithoutResult(status -> sampleRepository.findById(sampleId).ifPresent(sample -> {
                if (!leaseOwner.equals(sample.getLeaseOwner())) return;
                sample.setStatus(G2EvaluationSampleStatus.FAILED);
                sample.setFailureMessage(truncate(ex.getMessage(), 500));
                clearLease(sample);
            }));
            long refunded = economyService.refundFailedEvaluation(claim.author(), sampleId);
            if (refunded > 0) {
                transactions.executeWithoutResult(status -> sampleRepository.findById(sampleId)
                        .ifPresent(sample -> sample.setRefundedAt(Instant.now())));
            }
            log.warn("G2 evaluation sample failed sampleId={} refunded={} errorType={}",
                    sampleId, refunded, ex.getClass().getSimpleName(), SafeLogThrowable.stackOnly(ex));
        }
    }

    @Transactional
    public void recoverExpiredLeases() {
        Instant now = Instant.now();
        sampleRepository.findByStatus(G2EvaluationSampleStatus.RUNNING).stream()
                .filter(sample -> sample.getLeaseExpiresAt() != null && sample.getLeaseExpiresAt().isBefore(now))
                .forEach(sample -> {
                    sample.setStatus(G2EvaluationSampleStatus.PENDING);
                    clearLease(sample);
                });
    }

    private void persistCandidate(UUID sampleId, boolean fast, String text) {
        transactions.executeWithoutResult(status -> {
            G2EvaluationSample sample = sampleRepository.findById(sampleId)
                    .orElseThrow(() -> new BusinessException("盲测样本不存在"));
            if (!leaseOwner.equals(sample.getLeaseOwner()) || sample.getStatus() != G2EvaluationSampleStatus.RUNNING) {
                throw new IllegalStateException("G2 sample lease lost");
            }
            if (fast) sample.setFastText(text); else sample.setCraftedText(text);
            sample.setLeaseExpiresAt(Instant.now().plus(LEASE_DURATION));
        });
    }

    private void clearLease(G2EvaluationSample sample) {
        sample.setLeaseOwner(null);
        sample.setLeaseExpiresAt(null);
    }

    private record Claim(UUID manuscriptId, UUID sceneId, com.ainovel.app.user.User author,
                         String fastText, String craftedText) { }

    private String truncate(String message, int maxLength) {
        if (message == null) {
            return "生成失败";
        }
        return message.length() <= maxLength ? message : message.substring(0, maxLength);
    }
}
