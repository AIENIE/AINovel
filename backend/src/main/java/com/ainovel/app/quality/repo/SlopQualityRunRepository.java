package com.ainovel.app.quality.repo;

import com.ainovel.app.quality.model.SlopQualityRun;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SlopQualityRunRepository extends JpaRepository<SlopQualityRun, UUID> {
    @EntityGraph(attributePaths = "issues")
    List<SlopQualityRun> findTop20ByManuscriptIdOrderByCreatedAtDesc(UUID manuscriptId);

    @EntityGraph(attributePaths = "issues")
    List<SlopQualityRun> findTop20ByManuscriptIdAndSceneIdOrderByCreatedAtDesc(UUID manuscriptId, UUID sceneId);
}
