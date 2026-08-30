package com.ainovel.app.material;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "qdrant", name = "enabled", havingValue = "false")
public class NoopMaterialVectorIndex implements MaterialVectorIndex {
    @Override
    public void upsert(MaterialChunk chunk, float[] vector) {
    }

    @Override
    public void deleteMaterial(java.util.UUID materialId) {
    }

    @Override
    public List<VectorMatch> search(float[] vector, int limit, java.util.UUID ownerUserId) {
        return List.of();
    }
}
