package com.ainovel.app.manuscript.attribution;

import java.util.UUID;
import java.util.Set;

public record ManuscriptRollbackEvent(
        UUID manuscriptId,
        UUID targetVersionId,
        Set<UUID> changedSceneIds
) {
    public ManuscriptRollbackEvent {
        changedSceneIds = changedSceneIds == null ? Set.of() : Set.copyOf(changedSceneIds);
    }
}
