package com.ainovel.app.material.evidence;

import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.integration.AiGatewayGrpcClient;
import com.ainovel.app.user.User;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class MaterialNativeClient {
    private final AiGatewayGrpcClient gateway;
    private final String run;

    public MaterialNativeClient(
            AiGatewayGrpcClient gateway,
            @Value("${app.material-evidence.evaluation-run:}") String run) {
        this.gateway = gateway;
        this.run = run;
    }

    public record Profile(String id, String model, String collection) {}

    public String evaluationRun() {
        return run;
    }

    /**
     * Only this authoritative preflight refusal proves that this request did not leave the gateway.
     */
    public static boolean budgetBusy(Throwable failure) {
        return failure instanceof io.grpc.StatusRuntimeException status
                && status.getStatus().getCode() == io.grpc.Status.Code.FAILED_PRECONDITION
                && "RETRIEVAL_BUDGET_IN_FLIGHT".equals(status.getStatus().getDescription());
    }

    static int budgetWaitSeconds(int waits) {
        return switch (waits) {
            case 0 -> 5;
            case 1 -> 15;
            default -> 30;
        };
    }

    public static void requireOriginalUser(User user, Long originalUid) {
        if (originalUid == null || originalUid <= 0 || uid(user) != originalUid)
            throw new IllegalStateException("GATEWAY_REQUEST_IDENTITY_CHANGED");
    }

    public static Profile profile(String id) {
        return switch (id) {
            case "qwen-standard-1024-cp-v1" ->
                    new Profile(
                            id,
                            "qwen3.7-text-embedding",
                            "ainovel_qwen_standard_1024_cp900_120_document_v1");
            case "qwen-flash-1024-cp-v1" ->
                    new Profile(
                            id,
                            "qwen3.7-text-embedding-flash",
                            "ainovel_qwen_flash_1024_cp900_120_document_v1");
            default -> throw new IllegalArgumentException("INDEX_PROFILE_INVALID");
        };
    }

    public List<float[]> embed(
            User user, String request, Profile profile, List<String> input, String type) {
        return embed(user, request, profile, input, type, run);
    }

    public List<float[]> embed(
            User user,
            String request,
            Profile profile,
            List<String> input,
            String type,
            String originalRun) {
        var response =
                gateway.nativeEmbeddings(
                        request, uid(user), profile.model(), input, type, originalRun);
        if (!response.getModelKey().equals(profile.model())
                || response.getDimensions() != 1024
                || response.getEmbeddingsCount() != input.size())
            throw new IllegalStateException("EMBEDDING_CONTRACT_INVALID");
        List<float[]> result = new ArrayList<>();
        for (var vector : response.getEmbeddingsList()) {
            if (vector.getVectorCount() != 1024)
                throw new IllegalStateException("EMBEDDING_DIMENSIONS_INVALID");
            float[] v = new float[1024];
            for (int i = 0; i < v.length; i++) {
                v[i] = vector.getVector(i);
                if (!Float.isFinite(v[i]))
                    throw new IllegalStateException("EMBEDDING_VALUE_INVALID");
            }
            result.add(v);
        }
        return result;
    }

    public fireflychat.ai.v1.RerankResponse rerank(
            User user, String request, String query, List<String> docs, String mode, int top) {
        String instruction =
                mode.equals("fact")
                        ? "按对问题的逐字证据相关性排序。否定、条件、时点和同名实体必须匹配。不能把相似主题当作事实证据。"
                        : "按场景氛围、表现方法和灵感启发的相关性排序。";
        return gateway.rerank(request, uid(user), query, docs, instruction, top, run);
    }

    public AiGatewayGrpcClient.ChatResult structured(
            User user,
            String request,
            List<AiChatRequest.Message> messages,
            String schema,
            int max) {
        return structured(user, request, messages, schema, max, run);
    }

    public AiGatewayGrpcClient.ChatResult structured(
            User user,
            String request,
            List<AiChatRequest.Message> messages,
            String schema,
            int max,
            String originalRun) {
        return gateway.structured(request, uid(user), messages, schema, max, originalRun);
    }

    private static long uid(User user) {
        if (user == null || user.getRemoteUid() == null || user.getRemoteUid() <= 0)
            throw new IllegalStateException("GATEWAY_USER_REQUIRED");
        return user.getRemoteUid();
    }
}
