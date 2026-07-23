package com.ainovel.app.integration;

import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.ai.dto.AiModelDto;
import fireflychat.ai.v1.AiGatewayServiceGrpc;
import fireflychat.ai.v1.ChatCompletionsRequest;
import fireflychat.ai.v1.ChatCompletionsResponse;
import fireflychat.ai.v1.ChatCompletionsStreamEvent;
import fireflychat.ai.v1.ChatStreamEventType;
import fireflychat.ai.v1.ChatMessage;
import fireflychat.ai.v1.Embedding;
import fireflychat.ai.v1.EmbeddingsRequest;
import fireflychat.ai.v1.EmbeddingsResponse;
import fireflychat.ai.v1.ListModelsRequest;
import com.google.protobuf.MessageLite;
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
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

@Component
public class AiGatewayGrpcClient {

    private final ExternalServiceProperties properties;
    private final ClientInterceptor authInterceptor;
    private final GrpcEndpointManager<EndpointClient> endpointManager;

    public AiGatewayGrpcClient(
            ExternalServiceProperties properties,
            GrpcChannelFactory channelFactory
    ) {
        this.properties = properties;
        this.authInterceptor = new AiHmacAuthInterceptor(properties, Clock.systemUTC());
        this.endpointManager = new GrpcEndpointManager<>(
                properties.getAiserviceGrpc(),
                "aiservice-grpc",
                channelFactory
        );
    }

    public List<AiModelDto> listModels(long remoteUserId) {
        AiGatewayServiceGrpc.AiGatewayServiceBlockingStub stub = stub();
        var response = stub.withDeadlineAfter(timeoutMs(), TimeUnit.MILLISECONDS)
                .listModels(ListModelsRequest.newBuilder()
                        .setUserId(remoteUserId)
                        .build());
        List<AiModelDto> models = new ArrayList<>();
        response.getModelsList().forEach(m -> models.add(new AiModelDto(
                String.valueOf(m.getId()),
                String.valueOf(m.getId()),
                m.getDisplayName(),
                toModelType(m.getType()),
                m.getInputRate(),
                m.getOutputRate(),
                m.getProvider(),
                true,
                m.getSupportsImageInput(),
                m.getSupportsStreaming()
        )));
        return models;
    }

    public ChatResult chatCompletions(long remoteUserId, String model, List<AiChatRequest.Message> messages) {
        AiGatewayServiceGrpc.AiGatewayServiceBlockingStub stub = stub();
        ChatCompletionsRequest request = buildChatRequest(UUID.randomUUID().toString(), remoteUserId, model, messages);
        ChatCompletionsResponse response = stub.withDeadlineAfter(timeoutMs(), TimeUnit.MILLISECONDS)
                .chatCompletions(request);
        return new ChatResult(
                response.getContent(),
                response.getModelKey(),
                response.getPromptTokens(),
                response.getCompletionTokens(),
                response.getCacheTokens()
        );
    }

    public ChatResult chatCompletionsStream(long remoteUserId,
                                            String model,
                                            List<AiChatRequest.Message> messages,
                                            StreamProgressListener listener) {
        String requestId = UUID.randomUUID().toString();
        ChatCompletionsRequest request = buildChatRequest(requestId, remoteUserId, model, messages);
        Iterator<ChatCompletionsStreamEvent> events = stub()
                .withDeadlineAfter(timeoutMs(), TimeUnit.MILLISECONDS)
                .chatCompletionsStream(request);
        StringBuilder content = new StringBuilder();
        long expectedSequence = 1;
        long previousOutputTokens = 0;
        boolean started = false;
        boolean receivedDelta = false;
        ChatCompletionsStreamEvent completed = null;
        while (events.hasNext()) {
            ChatCompletionsStreamEvent event = events.next();
            if (completed != null) {
                throw new IllegalStateException("ai-service returned an event after COMPLETED");
            }
            if (!requestId.equals(event.getRequestId()) || event.getSequence() != expectedSequence++) {
                throw new IllegalStateException("ai-service returned an invalid stream sequence");
            }
            if (event.getEventType() == ChatStreamEventType.CHAT_STREAM_EVENT_TYPE_STARTED) {
                if (started) throw new IllegalStateException("ai-service returned duplicate STARTED event");
                started = true;
                listener.onStarted(requestId, event.getModelKey());
            } else if (event.getEventType() == ChatStreamEventType.CHAT_STREAM_EVENT_TYPE_CONTENT_DELTA) {
                if (!started || event.getContentDelta().isEmpty()) {
                    throw new IllegalStateException("ai-service returned invalid CONTENT_DELTA event");
                }
                if (event.getOutputTokensSoFar() < previousOutputTokens) {
                    throw new IllegalStateException("ai-service returned decreasing output token progress");
                }
                previousOutputTokens = event.getOutputTokensSoFar();
                receivedDelta = true;
                content.append(event.getContentDelta());
                listener.onDelta(event.getOutputTokensSoFar(), event.getOutputTokensEstimated());
            } else if (event.getEventType() == ChatStreamEventType.CHAT_STREAM_EVENT_TYPE_COMPLETED) {
                if (!started || !receivedDelta) throw new IllegalStateException("ai-service returned invalid COMPLETED event");
                completed = event;
            } else {
                throw new IllegalStateException("ai-service returned an unknown stream event");
            }
        }
        if (completed == null) throw new IllegalStateException("ai-service stream ended without COMPLETED event");
        listener.onCompleted(completed.getCompletionTokens(), completed.getPromptTokens(), completed.getCacheTokens());
        return new ChatResult(content.toString(), completed.getModelKey(), completed.getPromptTokens(),
                completed.getCompletionTokens(), completed.getCacheTokens());
    }

    private ChatCompletionsRequest buildChatRequest(String requestId,
                                                    long remoteUserId,
                                                    String model,
                                                    List<AiChatRequest.Message> messages) {
        ChatCompletionsRequest.Builder builder = ChatCompletionsRequest.newBuilder()
                .setRequestId(requestId)
                .setProjectKey(properties.getProjectKey())
                .setUserId(remoteUserId)
                .setSessionId("")
                .setModel(model == null ? "" : model);
        if (messages != null) {
            for (AiChatRequest.Message m : messages) {
                if (m == null) {
                    continue;
                }
                if (m.role() == null || m.role().isBlank() || m.content() == null || m.content().isBlank()) {
                    continue;
                }
                builder.addMessages(ChatMessage.newBuilder()
                        .setRole(m.role().trim())
                        .setContent(m.content().trim())
                        .build());
            }
        }
        return builder.build();
    }

    public EmbeddingResult embeddings(long remoteUserId, String model, List<String> input, boolean normalize) {
        AiGatewayServiceGrpc.AiGatewayServiceBlockingStub stub = stub();
        EmbeddingsRequest.Builder builder = EmbeddingsRequest.newBuilder()
                .setRequestId(UUID.randomUUID().toString())
                .setProjectKey(properties.getProjectKey())
                .setUserId(remoteUserId)
                .setSessionId("")
                .setModel(model == null ? "" : model)
                .setNormalize(normalize);
        if (input != null) {
            for (String item : input) {
                if (item != null && !item.isBlank()) {
                    builder.addInput(item.trim());
                }
            }
        }
        EmbeddingsResponse response = stub.withDeadlineAfter(timeoutMs(), TimeUnit.MILLISECONDS)
                .embeddings(builder.build());
        List<float[]> vectors = new ArrayList<>();
        for (Embedding embedding : response.getEmbeddingsList()) {
            float[] vector = new float[embedding.getVectorCount()];
            for (int i = 0; i < embedding.getVectorCount(); i++) {
                vector[i] = embedding.getVector(i);
            }
            vectors.add(vector);
        }
        return new EmbeddingResult(response.getModelKey(), response.getDimensions(), vectors, response.getPromptTokens());
    }

    private AiGatewayServiceGrpc.AiGatewayServiceBlockingStub stub() {
        return getOrCreateClient().stub();
    }

    private synchronized EndpointClient getOrCreateClient() {
        return endpointManager.getOrCreate((endpoint, channel) -> new EndpointClient(
                endpoint.host(),
                endpoint.port(),
                channel,
                AiGatewayServiceGrpc.newBlockingStub(channel).withInterceptors(authInterceptor)
        ));
    }

    private long timeoutMs() {
        return Math.max(800L, properties.getTimeoutMs());
    }

    @PreDestroy
    public synchronized void shutdown() {
        endpointManager.shutdown();
    }

    public record ChatResult(String content, String modelKey, long promptTokens, long completionTokens, long cacheTokens) {
    }

    public interface StreamProgressListener {
        default void onStarted(String requestId, String modelKey) {}
        void onDelta(long outputTokens, boolean estimated);
        default void onCompleted(long completionTokens, long promptTokens, long cacheTokens) {}
    }

    public record EmbeddingResult(String modelKey, int dimensions, List<float[]> vectors, long promptTokens) {
    }

    private String toModelType(fireflychat.ai.v1.ModelType type) {
        if (type == null) {
            return "unspecified";
        }
        return switch (type) {
            case MODEL_TYPE_TEXT -> "text";
            case MODEL_TYPE_IMAGE -> "image";
            case MODEL_TYPE_EMBEDDING -> "embedding";
            case MODEL_TYPE_AUDIO -> "audio";
            default -> "unspecified";
        };
    }

    private record EndpointClient(
            String host,
            int port,
            ManagedChannel channel,
            AiGatewayServiceGrpc.AiGatewayServiceBlockingStub stub
    ) implements GrpcEndpointManager.ManagedClient {
        public void close() {
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
        private static final Metadata.Key<String> BODY_SHA256_KEY =
                Metadata.Key.of("x-aienie-body-sha256", Metadata.ASCII_STRING_MARSHALLER);
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
                private Listener<RespT> listener;
                private Metadata pendingHeaders;
                private boolean started;
                private int pendingRequests;

                @Override
                public void start(Listener<RespT> responseListener, Metadata headers) {
                    this.listener = responseListener;
                    this.pendingHeaders = headers;
                }

                @Override
                public void request(int numMessages) {
                    if (started) {
                        super.request(numMessages);
                    } else {
                        pendingRequests += numMessages;
                    }
                }

                @Override
                public void sendMessage(ReqT message) {
                    ensureStarted(message);
                    super.sendMessage(message);
                }

                private void ensureStarted(ReqT message) {
                    if (started) {
                        return;
                    }
                    Metadata headers = pendingHeaders == null ? new Metadata() : pendingHeaders;
                    String caller = properties.getSecurity().getAi().getHmacCaller().trim();
                    String secret = properties.getSecurity().getAi().getHmacSecret().trim();
                    long ts = clock.instant().getEpochSecond();
                    String nonce = UUID.randomUUID().toString().replace("-", "");
                    String fullMethod = method.getFullMethodName();
                    String methodPath = fullMethod.startsWith("/") ? fullMethod : "/" + fullMethod;
                    String bodySha256 = sha256Hex(requestBytes(method, message));
                    String signature = sign(secret, caller + "\n" + methodPath + "\n" + ts + "\n" + nonce + "\n" + bodySha256);

                    headers.put(CALLER_KEY, caller);
                    headers.put(TS_KEY, String.valueOf(ts));
                    headers.put(NONCE_KEY, nonce);
                    headers.put(BODY_SHA256_KEY, bodySha256);
                    headers.put(SIGNATURE_KEY, signature);
                    super.start(listener, headers);
                    started = true;
                    if (pendingRequests > 0) {
                        super.request(pendingRequests);
                        pendingRequests = 0;
                    }
                }

                @Override
                public void halfClose() {
                    if (!started) {
                        ensureStarted(null);
                    }
                    super.halfClose();
                }
            };
        }

        private <ReqT> byte[] requestBytes(MethodDescriptor<ReqT, ?> method, ReqT message) {
            if (message == null) {
                return new byte[0];
            }
            if (message instanceof MessageLite protobuf) {
                return protobuf.toByteArray();
            }
            try (InputStream in = method.getRequestMarshaller().stream(message);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                in.transferTo(out);
                return out.toByteArray();
            } catch (Exception ex) {
                throw new IllegalStateException("Serialize ai-service grpc request failed", ex);
            }
        }

        private String sha256Hex(byte[] bytes) {
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
                StringBuilder sb = new StringBuilder(digest.length * 2);
                for (byte b : digest) {
                    sb.append(String.format("%02x", b));
                }
                return sb.toString();
            } catch (Exception ex) {
                throw new IllegalStateException("Compute ai-service request body hash failed", ex);
            }
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
