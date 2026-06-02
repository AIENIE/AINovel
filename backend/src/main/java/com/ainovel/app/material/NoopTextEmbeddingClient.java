package com.ainovel.app.material;

import com.ainovel.app.user.User;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(TextEmbeddingClient.class)
public class NoopTextEmbeddingClient implements TextEmbeddingClient {
    @Override
    public float[] embed(User user, String text) {
        return new float[0];
    }
}
