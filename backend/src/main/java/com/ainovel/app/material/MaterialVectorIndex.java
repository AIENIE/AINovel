package com.ainovel.app.material;

import java.util.List;

public interface MaterialVectorIndex {
    void upsert(MaterialChunk chunk, float[] vector);

    List<VectorMatch> search(float[] vector, int limit);
}
