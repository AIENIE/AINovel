package com.ainovel.app.material;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnMissingBean(MaterialVectorIndex.class)
public class NoopMaterialVectorIndex implements MaterialVectorIndex {
    @Override
    public void upsert(MaterialChunk chunk, float[] vector) {
    }

    @Override
    public List<VectorMatch> search(float[] vector, int limit) {
        return List.of();
    }
}
