package com.ainovel.app.material;

import java.util.List;

public interface MaterialVectorIndex {
    void upsert(MaterialChunk chunk, float[] vector);

    void deleteMaterial(java.util.UUID materialId);

    List<VectorMatch> search(float[] vector, int limit, java.util.UUID ownerUserId);
}
