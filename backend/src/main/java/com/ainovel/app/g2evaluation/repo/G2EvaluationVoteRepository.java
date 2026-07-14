package com.ainovel.app.g2evaluation.repo;

import com.ainovel.app.g2evaluation.model.G2EvaluationExperiment;
import com.ainovel.app.g2evaluation.model.G2EvaluationSample;
import com.ainovel.app.g2evaluation.model.G2EvaluationVote;
import com.ainovel.app.g2evaluation.model.G2EvaluationVoteChoice;
import com.ainovel.app.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface G2EvaluationVoteRepository extends JpaRepository<G2EvaluationVote, UUID> {
    boolean existsBySampleAndReviewer(G2EvaluationSample sample, User reviewer);
    long countByExperiment(G2EvaluationExperiment experiment);
    long countByExperimentAndChoice(G2EvaluationExperiment experiment, G2EvaluationVoteChoice choice);

    @Query("select count(distinct vote.reviewer.id) from G2EvaluationVote vote where vote.experiment = :experiment")
    long countDistinctReviewers(@Param("experiment") G2EvaluationExperiment experiment);
}
