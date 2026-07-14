package com.ainovel.app.g2evaluation;

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

import java.time.Instant;
import java.util.UUID;

@Service
public class G2EvaluationGenerationWorker {
    private static final Logger log = LoggerFactory.getLogger(G2EvaluationGenerationWorker.class);

    private final G2EvaluationSampleRepository sampleRepository;
    private final ManuscriptRepository manuscriptRepository;
    private final SceneGenerationService sceneGenerationService;
    private final EconomyService economyService;

    public G2EvaluationGenerationWorker(G2EvaluationSampleRepository sampleRepository,
                                        ManuscriptRepository manuscriptRepository,
                                        SceneGenerationService sceneGenerationService,
                                        EconomyService economyService) {
        this.sampleRepository = sampleRepository;
        this.manuscriptRepository = manuscriptRepository;
        this.sceneGenerationService = sceneGenerationService;
        this.economyService = economyService;
    }

    @Transactional
    public void generate(UUID sampleId) {
        G2EvaluationSample sample = sampleRepository.findById(sampleId)
                .orElseThrow(() -> new BusinessException("盲测样本不存在"));
        if (sample.getStatus() != G2EvaluationSampleStatus.PENDING) {
            return;
        }
        sample.setStatus(G2EvaluationSampleStatus.RUNNING);
        sampleRepository.save(sample);
        try {
            Manuscript manuscript = manuscriptRepository.findWithStoryById(sample.getManuscriptId())
                    .orElseThrow(() -> new BusinessException("盲测稿件不存在"));
            if (manuscript.getOutline() == null || manuscript.getOutline().getStory() == null
                    || manuscript.getOutline().getStory().getUser() == null
                    || !sample.getAuthor().getId().equals(manuscript.getOutline().getStory().getUser().getId())) {
                throw new BusinessException("盲测稿件不属于投稿作者");
            }
            SceneGenerationService.EvaluationPair pair = sceneGenerationService.generateEvaluationPair(
                    manuscript, sample.getSceneId(), sample.getId());
            sample.setFastText(pair.fastText());
            sample.setCraftedText(pair.craftedText());
            sample.setStatus(G2EvaluationSampleStatus.READY);
            sampleRepository.save(sample);
            log.info("G2 evaluation sample generated sampleId={} experimentId={}", sample.getId(), sample.getExperiment().getId());
        } catch (RuntimeException ex) {
            sample.setStatus(G2EvaluationSampleStatus.FAILED);
            sample.setFailureMessage(truncate(ex.getMessage(), 500));
            long refunded = economyService.refundFailedEvaluation(sample.getAuthor(), sample.getId());
            if (refunded > 0) {
                sample.setRefundedAt(Instant.now());
            }
            sampleRepository.save(sample);
            log.warn("G2 evaluation sample failed sampleId={} refunded={} reason={}",
                    sample.getId(), refunded, ex.getMessage());
        }
    }

    private String truncate(String message, int maxLength) {
        if (message == null) {
            return "生成失败";
        }
        return message.length() <= maxLength ? message : message.substring(0, maxLength);
    }
}
