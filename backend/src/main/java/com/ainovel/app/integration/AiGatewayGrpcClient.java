package com.ainovel.app.integration;

import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.ai.dto.AiModelDto;
import fireflychat.ai.v1.AiGatewayServiceGrpc;
import fireflychat.ai.v1.ChatCompletionsRequest;
import fireflychat.ai.v1.ChatCompletionsResponse;
import fireflychat.ai.v1.ChatMessage;
import fireflychat.ai.v1.ListModelsRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class AiGatewayGrpcClient {

    private final ExternalServiceProperties properties;
    private final ConsulServiceResolver resolver;

    private volatile EndpointClient client;

    public AiGatewayGrpcClient(ExternalServiceProperties properties, ConsulServiceResolver resolver) {
        this.properties = properties;
        this.resolver = resolver;
    }

    public List<AiModelDto> listModels() {
        AiGatewayServiceGrpc.AiGatewayServiceBlockingStub stub = stub();
        var response = stub.withDeadlineAfter(timeoutMs(), TimeUnit.MILLISECONDS)
                .listModels(ListModelsRequest.getDefaultInstance());
        List<AiModelDto> models = new ArrayList<>();
        response.getModelsList().forEach(m -> models.add(new AiModelDto(
                String.valueOf(m.getId()),
                String.valueOf(m.getId()),
                m.getDisplayName(),
                toModelType(m.getType()),
                m.getInputRate(),
                m.getOutputRate(),
                m.getProvider(),
                true
        )));
        return models;
    }

    public ChatResult chatCompletions(long remoteUserId, String model, List<AiChatRequest.Message> messages) {
        AiGatewayServiceGrpc.AiGatewayServiceBlockingStub stub = stub();
        ChatCompletionsRequest.Builder builder = ChatCompletionsRequest.newBuilder()
                .setRequestId(UUID.randomUUID().toString())
                .setProjectKey(properties.getProjectKey())
                .setUserId(remoteUserId)
                .setSessionId("")
                .setModel(model == null ? "" : model);
        if (messages != null) {
            for (AiChatRequest.Message m : messages) {
                if (m == null) {
                    continue;
                }
                if (m.role() == null || m.content() == null) {
                    continue;
                }
                builder.addMessages(ChatMessage.newBuilder()
                        .setRole(m.role())
                        .setContent(m.content())
                        .build());
            }
        }
        ChatCompletionsResponse response = stub.withDeadlineAfter(timeoutMs(), TimeUnit.MILLISECONDS)
                .chatCompletions(builder.build());
        return new ChatResult(
                response.getContent(),
                response.getModelKey(),
                response.getPromptTokens(),
                response.getCompletionTokens()
        );
    }

    private AiGatewayServiceGrpc.AiGatewayServiceBlockingStub stub() {
        return getOrCreateClient().stub();
    }

    private synchronized EndpointClient getOrCreateClient() {
        ExternalServiceProperties.ServiceTarget target = properties.getAiserviceGrpc();
        ConsulServiceResolver.Endpoint endpoint = resolver
                .resolveOrFallback(target.getServiceName(), target.getFallback())
                .orElseThrow(() -> new IllegalStateException("No endpoint for aiservice-grpc"));

        EndpointClient existing = client;
        if (existing != null && existing.sameEndpoint(endpoint.host(), endpoint.port())) {
            return existing;
        }

        ManagedChannel channel = ManagedChannelBuilder.forAddress(endpoint.host(), endpoint.port())
                .usePlaintext()
                .build();
        EndpointClient next = new EndpointClient(
                endpoint.host(),
                endpoint.port(),
                channel,
                AiGatewayServiceGrpc.newBlockingStub(channel)
        );
        if (existing != null) {
            existing.close();
        }
        client = next;
        return next;
    }

    private long timeoutMs() {
        return Math.max(800L, properties.getTimeoutMs());
    }

    @PreDestroy
    public synchronized void shutdown() {
        if (client != null) {
            client.close();
            client = null;
        }
    }

    public record ChatResult(String content, String modelKey, long promptTokens, long completionTokens) {
    }

    private String toModelType(fireflychat.ai.v1.ModelType type) {
        if (type == null) {
            return "unspecified";
        }
        return switch (type) {
            case MODEL_TYPE_TEXT -> "text";
            case MODEL_TYPE_EMBEDDING -> "embedding";
            default -> "unspecified";
        };
    }

    private record EndpointClient(
            String host,
            int port,
            ManagedChannel channel,
            AiGatewayServiceGrpc.AiGatewayServiceBlockingStub stub
    ) {
        boolean sameEndpoint(String targetHost, int targetPort) {
            return host.equals(targetHost) && port == targetPort;
        }

        void close() {
            channel.shutdownNow();
        }
    }
}
