package com.ainovel.app.g2evaluation.repo;

import com.ainovel.app.g2evaluation.model.G2EvaluationExperiment;
import com.ainovel.app.g2evaluation.model.G2EvaluationInvite;
import com.ainovel.app.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface G2EvaluationInviteRepository extends JpaRepository<G2EvaluationInvite, UUID> {
    Optional<G2EvaluationInvite> findByExperimentAndReviewer(G2EvaluationExperiment experiment, User reviewer);
    long countByExperiment(G2EvaluationExperiment experiment);
}
