package com.ainovel.app.material;

import com.ainovel.app.user.User;

@FunctionalInterface
public interface TextEmbeddingClient {
    float[] embed(User user, String text);
}
