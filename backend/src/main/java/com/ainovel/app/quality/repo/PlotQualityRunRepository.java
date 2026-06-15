package com.ainovel.app.quality.repo;

import com.ainovel.app.quality.model.PlotQualityRun;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlotQualityRunRepository extends JpaRepository<PlotQualityRun, UUID> {
    @EntityGraph(attributePaths = "issues")
    List<PlotQualityRun> findTop20ByManuscriptIdOrderByCreatedAtDesc(UUID manuscriptId);

    @EntityGraph(attributePaths = "issues")
    List<PlotQualityRun> findTop20ByManuscriptIdAndSceneIdOrderByCreatedAtDesc(UUID manuscriptId, UUID sceneId);

    @EntityGraph(attributePaths = "issues")
    List<PlotQualityRun> findTop200ByManuscriptIdOrderByCreatedAtDesc(UUID manuscriptId);

    List<PlotQualityRun> findTop100ByOrderByCreatedAtDesc();

    long countByOverallRiskScoreGreaterThanEqual(int riskScore);
}
