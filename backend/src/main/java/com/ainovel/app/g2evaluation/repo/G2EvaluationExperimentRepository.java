package com.ainovel.app.g2evaluation.repo;

import com.ainovel.app.g2evaluation.model.G2EvaluationExperiment;
import com.ainovel.app.g2evaluation.model.G2EvaluationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface G2EvaluationExperimentRepository extends JpaRepository<G2EvaluationExperiment, UUID> {
    List<G2EvaluationExperiment> findByStatusInOrderByCreatedAtDesc(Collection<G2EvaluationStatus> statuses);
    List<G2EvaluationExperiment> findAllByOrderByCreatedAtDesc();
}
