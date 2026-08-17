package com.ainovel.app.manuscript.attribution;

import java.util.UUID;

public record SceneContentEditedEvent(UUID manuscriptId, UUID sceneId, String content) {}
