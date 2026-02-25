package com.ainovel.app.integration;

import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.ai.dto.AiModelDto;
import fireflychat.ai.v1.AiGatewayServiceGrpc;
import fireflychat.ai.v1.ChatCompletionsRequest;
import fireflychat.ai.v1.ChatCompletionsResponse;
import fireflychat.ai.v1.ChatMessage;
import fireflychat.ai.v1.ListModelsRequest;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class AiGatewayGrpcClient {

    private final ExternalServiceProperties properties;
    private final ConsulServiceResolver resolver;
    private final GrpcChannelFactory channelFactory;
    private final ClientInterceptor authInterceptor;

    private volatile EndpointClient client;

    public AiGatewayGrpcClient(
            ExternalServiceProperties properties,
            ConsulServiceResolver resolver,
            GrpcChannelFactory channelFactory
    ) {
        this.properties = properties;
        this.resolver = resolver;
        this.channelFactory = channelFactory;
        this.authInterceptor = new AiHmacAuthInterceptor(properties, Clock.systemUTC());
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

        ManagedChannel channel = channelFactory.create(endpoint.host(), endpoint.port());
        EndpointClient next = new EndpointClient(
                endpoint.host(),
                endpoint.port(),
                channel,
                AiGatewayServiceGrpc.newBlockingStub(channel).withInterceptors(authInterceptor)
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

    private static class AiHmacAuthInterceptor implements ClientInterceptor {
        private static final Metadata.Key<String> CALLER_KEY =
                Metadata.Key.of("x-aienie-caller", Metadata.ASCII_STRING_MARSHALLER);
        private static final Metadata.Key<String> TS_KEY =
                Metadata.Key.of("x-aienie-ts", Metadata.ASCII_STRING_MARSHALLER);
        private static final Metadata.Key<String> NONCE_KEY =
                Metadata.Key.of("x-aienie-nonce", Metadata.ASCII_STRING_MARSHALLER);
        private static final Metadata.Key<String> SIGNATURE_KEY =
                Metadata.Key.of("x-aienie-signature", Metadata.ASCII_STRING_MARSHALLER);

        private final ExternalServiceProperties properties;
        private final Clock clock;

        private AiHmacAuthInterceptor(ExternalServiceProperties properties, Clock clock) {
            this.properties = properties;
            this.clock = clock;
        }

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                MethodDescriptor<ReqT, RespT> method,
                CallOptions callOptions,
                Channel next
        ) {
            return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
                @Override
                public void start(Listener<RespT> responseListener, Metadata headers) {
                    String caller = properties.getSecurity().getAi().getHmacCaller().trim();
                    String secret = properties.getSecurity().getAi().getHmacSecret().trim();
                    long ts = clock.instant().getEpochSecond();
                    String nonce = UUID.randomUUID().toString().replace("-", "");
                    String fullMethod = method.getFullMethodName();
                    String methodPath = fullMethod.startsWith("/") ? fullMethod : "/" + fullMethod;
                    String signature = sign(secret, caller + "\n" + methodPath + "\n" + ts + "\n" + nonce);

                    headers.put(CALLER_KEY, caller);
                    headers.put(TS_KEY, String.valueOf(ts));
                    headers.put(NONCE_KEY, nonce);
                    headers.put(SIGNATURE_KEY, signature);
                    super.start(responseListener, headers);
                }
            };
        }

        private String sign(String secret, String canonical) {
            try {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                byte[] digest = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
                return Base64.getEncoder().encodeToString(digest);
            } catch (Exception ex) {
                throw new IllegalStateException("Generate ai-service HMAC signature failed", ex);
            }
        }
    }
}
