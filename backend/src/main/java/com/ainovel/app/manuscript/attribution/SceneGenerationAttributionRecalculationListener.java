package com.ainovel.app.manuscript.attribution;

import com.ainovel.app.common.SafeLogThrowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class SceneGenerationAttributionRecalculationListener {
    private static final Logger log = LoggerFactory.getLogger(SceneGenerationAttributionRecalculationListener.class);

    private final SceneGenerationAttributionService attributionService;

    public SceneGenerationAttributionRecalculationListener(SceneGenerationAttributionService attributionService) {
        this.attributionService = attributionService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterSceneContentCommitted(SceneContentEditedEvent event) {
        try {
            attributionService.recomputeAfterCommit(event.manuscriptId(), event.sceneId(), event.content());
        } catch (RuntimeException ex) {
            log.error(
                    "scene_generation_attribution_post_commit_failed manuscriptId={} sceneId={} action=mark_pending_or_recompute queryHashRecoveryAvailable=true errorType={}",
                    event.manuscriptId(),
                    event.sceneId(),
                    ex.getClass().getSimpleName(),
                    SafeLogThrowable.stackOnly(ex)
            );
        }
    }
}
