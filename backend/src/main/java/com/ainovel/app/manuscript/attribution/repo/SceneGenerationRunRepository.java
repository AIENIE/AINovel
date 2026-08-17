package com.ainovel.app.manuscript.attribution.repo;

import com.ainovel.app.manuscript.attribution.SceneGenerationRunStatus;
import com.ainovel.app.manuscript.attribution.model.SceneGenerationRun;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface SceneGenerationRunRepository extends JpaRepository<SceneGenerationRun, UUID> {
    List<SceneGenerationRun> findByManuscriptIdOrderByCreatedAtDesc(UUID manuscriptId);

    List<SceneGenerationRun> findByManuscriptIdAndSceneIdOrderByCreatedAtDesc(UUID manuscriptId, UUID sceneId);

    List<SceneGenerationRun> findByManuscriptIdAndSceneIdOrderByCreatedAtDesc(
            UUID manuscriptId,
            UUID sceneId,
            Pageable pageable
    );

    List<SceneGenerationRun> findByManuscriptIdAndSceneIdAndStatusOrderByCreatedAtDesc(
            UUID manuscriptId,
            UUID sceneId,
            SceneGenerationRunStatus status
    );

    List<SceneGenerationRun> findByManuscriptIdAndSceneIdAndStatusInOrderByCreatedAtDesc(
            UUID manuscriptId,
            UUID sceneId,
            Collection<SceneGenerationRunStatus> statuses
    );

    List<SceneGenerationRun> findByManuscriptIdAndStatusOrderByCreatedAtDesc(
            UUID manuscriptId,
            SceneGenerationRunStatus status
    );

    List<SceneGenerationRun> findByManuscriptIdAndStatusInOrderByCreatedAtDesc(
            UUID manuscriptId,
            Collection<SceneGenerationRunStatus> statuses
    );

    Optional<SceneGenerationRun> findByIdAndManuscriptIdAndSceneId(UUID id, UUID manuscriptId, UUID sceneId);

    Optional<SceneGenerationRun> findByManuscriptIdAndGenerationVersionId(UUID manuscriptId, UUID generationVersionId);
}
