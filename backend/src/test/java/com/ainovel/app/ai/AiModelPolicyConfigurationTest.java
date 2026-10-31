package com.ainovel.app.ai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AiModelPolicyConfigurationTest {
    @Test void localModelConfigurationUsesCanonicalRenamedKey() {
        new ApplicationContextRunner().withBean(AiModelPolicy.class)
                .withPropertyValues("app.ai.text-model-key=deepseek-flash", "app.ai.text-model-display-name=DeepSeek Flash")
                .run(context -> {
                    var policy = context.getBean(AiModelPolicy.class);
                    assertEquals("deepseek-flash", policy.modelKey());
                    assertEquals("DeepSeek Flash", policy.displayName());
                });
    }
    @Test void otherProfilesKeepTheirExistingDefault() {
        assertEquals("deepseek-v4-flash", new AiModelPolicy().modelKey());
    }
}
