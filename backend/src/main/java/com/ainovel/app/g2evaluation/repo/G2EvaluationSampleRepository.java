package com.ainovel.app.g2evaluation.repo;

import com.ainovel.app.g2evaluation.model.G2EvaluationExperiment;
import com.ainovel.app.g2evaluation.model.G2EvaluationSample;
import com.ainovel.app.g2evaluation.model.G2EvaluationSampleStatus;
import com.ainovel.app.user.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface G2EvaluationSampleRepository extends JpaRepository<G2EvaluationSample, UUID> {
    boolean existsByExperimentAndAuthorAndManuscriptIdAndSceneId(
            G2EvaluationExperiment experiment, User author, UUID manuscriptId, UUID sceneId);
    long countByExperimentAndStatus(G2EvaluationExperiment experiment, G2EvaluationSampleStatus status);
    List<G2EvaluationSample> findByExperimentOrderByCreatedAtDesc(G2EvaluationExperiment experiment);

    @Query("""
            select sample from G2EvaluationSample sample
            where sample.experiment = :experiment
              and sample.status = com.ainovel.app.g2evaluation.model.G2EvaluationSampleStatus.READY
              and sample.author.id <> :reviewerId
              and not exists (
                  select vote.id from G2EvaluationVote vote
                  where vote.sample = sample and vote.reviewer.id = :reviewerId
              )
            order by sample.createdAt asc
            """)
    List<G2EvaluationSample> findNextReviewableSample(
            @Param("experiment") G2EvaluationExperiment experiment,
            @Param("reviewerId") UUID reviewerId,
            Pageable pageable
    );

    Optional<G2EvaluationSample> findByIdAndExperiment(UUID id, G2EvaluationExperiment experiment);
}
