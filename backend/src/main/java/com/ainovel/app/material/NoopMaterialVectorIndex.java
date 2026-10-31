package com.ainovel.app.material;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "qdrant", name = "enabled", havingValue = "false")
public class NoopMaterialVectorIndex implements MaterialVectorIndex {
    @Override
    public void deleteMaterial(java.util.UUID materialId) {
    }

}
