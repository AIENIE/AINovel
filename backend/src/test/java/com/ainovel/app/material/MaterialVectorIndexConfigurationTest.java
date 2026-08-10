package com.ainovel.app.material;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialVectorIndexConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withUserConfiguration(QdrantMaterialVectorIndex.class, NoopMaterialVectorIndex.class);

    @Test
    void qdrantDisabledProvidesOnlyNoopIndex() {
        runner.withPropertyValues("qdrant.enabled=false").run(context -> {
            assertThat(context).hasSingleBean(MaterialVectorIndex.class);
            assertThat(context).hasSingleBean(NoopMaterialVectorIndex.class);
            assertThat(context).doesNotHaveBean(QdrantMaterialVectorIndex.class);
        });
    }

    @Test
    void qdrantEnabledProvidesOnlyQdrantIndex() {
        runner.withPropertyValues("qdrant.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(MaterialVectorIndex.class);
            assertThat(context).hasSingleBean(QdrantMaterialVectorIndex.class);
            assertThat(context).doesNotHaveBean(NoopMaterialVectorIndex.class);
        });
    }

    @Test
    void missingFlagKeepsCurrentQdrantDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(MaterialVectorIndex.class);
            assertThat(context).hasSingleBean(QdrantMaterialVectorIndex.class);
        });
    }
}
