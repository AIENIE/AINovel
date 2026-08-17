package com.ainovel.app.manuscript.attribution;

import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.user.User;

import java.util.Map;
import java.util.UUID;

public interface SceneGenerationSnapshotStore {
    void ensureGenerationBaseline(Manuscript manuscript, User user);

    UUID createGenerationSnapshot(
            Manuscript manuscript,
            User user,
            UUID sceneId,
            Map<String, Object> manifest
    );
}
