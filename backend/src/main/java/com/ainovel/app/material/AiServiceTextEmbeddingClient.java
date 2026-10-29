package com.ainovel.app.material;

import com.ainovel.app.integration.AiGatewayGrpcClient;
import com.ainovel.app.user.User;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AiServiceTextEmbeddingClient implements TextEmbeddingClient {
    private final AiGatewayGrpcClient aiGatewayGrpcClient;

    public AiServiceTextEmbeddingClient(AiGatewayGrpcClient aiGatewayGrpcClient) {
        this.aiGatewayGrpcClient = aiGatewayGrpcClient;
    }

    @Override
    public float[] embed(User user, String text) {
        if (user == null || user.getRemoteUid() == null || user.getRemoteUid() <= 0 || text == null || text.isBlank()) {
            throw new IllegalStateException("EMBEDDING_INPUT_INVALID");
        }
        AiGatewayGrpcClient.EmbeddingResult result = aiGatewayGrpcClient.embeddings(
                user.getRemoteUid(),
                "",
                List.of(text),
                true
        );
        if (result.vectors().isEmpty()) {
            throw new IllegalStateException("EMBEDDING_RESULT_EMPTY");
        }
        return result.vectors().get(0);
    }
}
