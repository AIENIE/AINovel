package com.ainovel.app.integration;

import com.ainovel.app.ai.AiRequestNotSentException;
import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.modelconfig.AiModelRole;
import com.ainovel.app.ai.dto.AiResponseMetadata;
import com.ainovel.app.ai.dto.AiModelDto;
import fireflychat.ai.v1.AiGatewayServiceGrpc;
import fireflychat.ai.v1.ChatCompletionsRequest;
import fireflychat.ai.v1.ChatCompletionsResponse;
import fireflychat.ai.v1.ChatCompletionsStreamEvent;
import fireflychat.ai.v1.ChatStreamTimeouts;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

@Component
public class AiGatewayGrpcClient {
    private static final ChatStreamTimeouts TEXT_STREAM_LIMITS = ChatStreamTimeouts.newBuilder()
            .setFirstTextMs(300_000).setIdleMs(120_000).setTotalMs(600_000).setIdleAfterFirstText(true).build();
    private static final long TEXT_STREAM_RPC_MS = 615_000;
    private static final Set<String> REPORT_KEYS = Set.of(AiModelRole.LANGUAGE_CHECK.key(), AiModelRole.PLOT_CHECK.key(),
            AiModelRole.REVISION_REVIEW.key(), "slop.judge", "slop.diagnose", "plot.diagnose");

    public static final String RETRIEVAL_RERANK_MODEL="qwen3.7-text-rerank";
    public static final String RETRIEVAL_STRUCTURED_MODEL="qwen3.7-flash-2026-07-15";
    @org.springframework.beans.factory.annotation.Autowired
    private com.ainovel.app.ai.AiValidationCallBudget validationBudget;
    @org.springframework.beans.factory.annotation.Autowired
    private com.ainovel.app.ai.AiValidationMoneyGateway moneyGateway;

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

    public fireflychat.ai.v1.ModelChatCapabilities chatCapabilities(String model) {
        var response=stub().withDeadlineAfter(timeoutMs(),TimeUnit.MILLISECONDS).listCallerModels(fireflychat.ai.v1.ListCallerModelsRequest.getDefaultInstance());
        return response.getModelsList().stream().filter(m->m.getModelKey().equals(model) || Long.toString(m.getId()).equals(model))
                .findFirst().map(fireflychat.ai.v1.ModelOption::getChatCapabilities).orElseThrow(()->new IllegalArgumentException("AI_MODEL_UNAVAILABLE"));
    }
    public List<com.ainovel.app.modelconfig.AiModelConfigurationService.CatalogModel> listCallerModels() {
        var response=stub().withDeadlineAfter(timeoutMs(),TimeUnit.MILLISECONDS)
                .listCallerModels(fireflychat.ai.v1.ListCallerModelsRequest.getDefaultInstance());
        return response.getModelsList().stream().map(m->{
            var t=m.getThinking();
            var thinking=m.hasThinking()?new com.ainovel.app.modelconfig.AiModelConfigurationService.Thinking(
                    t.getDeclared(),t.getSupportsEnable(),t.getSupportsDisable(),t.getOptionsList().stream().filter(o->o.getEffortValue()>=1&&o.getEffortValue()<=6)
                    .map(o->new com.ainovel.app.modelconfig.AiModelConfigurationService.ThinkingOption(
                            o.getEffort().name().replace("THINKING_EFFORT_",""),o.getLabel())).toList(),
                    t.hasDefaultEnabled()?t.getDefaultEnabled():null,
                    t.hasDefaultEffort()?t.getDefaultEffort().name().replace("THINKING_EFFORT_",""):null)
                    :com.ainovel.app.modelconfig.AiModelConfigurationService.Thinking.unknown();
            return new com.ainovel.app.modelconfig.AiModelConfigurationService.CatalogModel(
                    Long.toString(m.getId()),m.getModelKey(),m.getDisplayName(),m.getProvider(),true,
                    m.getSupportsStreaming(),m.getSupportsImageInput(),thinking,null,
                    m.hasChatCapabilities()?com.ainovel.app.modelconfig.AiModelConfigurationService.ChatCapabilities.from(m.getChatCapabilities()):com.ainovel.app.modelconfig.AiModelConfigurationService.ChatCapabilities.unknown());
        }).toList();
    }

    public ChatResult complete(String requestId,long remoteUserId,
            com.ainovel.app.modelconfig.AiModelSelection selected,List<AiChatRequest.Message> messages,
            StreamProgressListener listener) {
        if (requiresTextStreaming(selected.modelKey()) && !selected.streaming()) {
            throw new AiRequestNotSentException(org.springframework.http.HttpStatus.PRECONDITION_FAILED, "AI_MODEL_UNAVAILABLE");
        }
        var request = textStreamLimits(selectedRequest(buildChatRequest(requestId, remoteUserId, selected.modelKey(), messages), selected));
        var owner = com.ainovel.app.ai.AiNativeBudgetContext.current();
        if (owner != null) {
            var snapshot = com.ainovel.app.modelconfig.AiSelectionContext.current();
            if (snapshot == null) throw new AiRequestNotSentException(org.springframework.http.HttpStatus.PRECONDITION_FAILED, "AI_NATIVE_CONFIGURATION_REQUIRED");
            var bounded = request.toBuilder().setMaxOutputTokens(snapshot.outputBudget(selected.featureKey())).build();
            var transport = moneyTransport(listener);
            return chatResult(owner.call(bounded, transport, monetaryValidation() ? grant -> moneyGateway.callPrepared(bounded, grant, transport) : null));
        }
        return textChat(request, messages, listener, listener != null);
    }

    /** Native tool turns retain the complete wire envelope and use an explicitly bounded unary call. */
    public ChatResult complete(String requestId,long remoteUserId,
            com.ainovel.app.modelconfig.AiModelSelection selected,AiChatRequest input,StreamProgressListener listener) {
        if(!input.nativeTools())return complete(requestId,remoteUserId,selected,input.messages(),listener);
        var request=selectedRequest(buildChatRequest(requestId,remoteUserId,selected.modelKey(),input),selected);
        var capabilities=chatCapabilities(selected.modelKey());
        if(requiresTextStreaming(selected.modelKey()) || !capabilities.getBudgetToolsDeclared() || !capabilities.getBudgetToolsAvailable())
            throw new com.ainovel.app.ai.AiRequestNotSentException(org.springframework.http.HttpStatus.PRECONDITION_FAILED,"AI_NATIVE_BUDGET_TOOLS_UNAVAILABLE");
        var owner=com.ainovel.app.ai.AiNativeBudgetContext.current();
        if(owner!=null)return chatResult(owner.call(request,moneyTransport(),monetaryValidation()?grant->moneyGateway.callPrepared(request,grant,moneyTransport()):null));
        if(!monetaryValidation())throw new com.ainovel.app.ai.AiRequestNotSentException(org.springframework.http.HttpStatus.PRECONDITION_FAILED,"AI_NATIVE_BUDGET_REQUIRED");
        return moneyChat(request);
    }

    static ChatCompletionsRequest withThinking(ChatCompletionsRequest original,
            com.ainovel.app.modelconfig.AiModelSelection selected) {
        var builder=original.toBuilder();
        if("OFF".equals(selected.thinkingMode())) builder.setThinkingEnabled(false);
        else if("ON".equals(selected.thinkingMode())) {
            builder.setThinkingEnabled(true);
            if(selected.thinkingEffort()!=null) builder.setThinkingEffort(
                    fireflychat.ai.v1.ThinkingEffort.valueOf("THINKING_EFFORT_"+selected.thinkingEffort()));
        }
        return builder.build();
    }

    private static ChatCompletionsRequest selectedRequest(ChatCompletionsRequest original,
            com.ainovel.app.modelconfig.AiModelSelection selected) {
        var request = withThinking(original, selected);
        var snapshot = com.ainovel.app.modelconfig.AiSelectionContext.current();
        if (snapshot != null && (snapshot.explicitOutputLimits().containsKey(selected.featureKey())
                || REPORT_KEYS.contains(selected.featureKey()))) {
            request = request.toBuilder().setMaxOutputTokens(snapshot.outputBudget(selected.featureKey())).build();
        }
        return request;
    }

    public static boolean requiresTextStreaming(String model) {
        return model != null && (model.startsWith("gpt-") || model.startsWith("gemini-"));
    }

    private static ChatCompletionsRequest textStreamLimits(ChatCompletionsRequest request) {
        return requiresTextStreaming(request.getModel()) ? request.toBuilder().setStreamTimeouts(TEXT_STREAM_LIMITS).build() : request;
    }

    public ChatResult chatCompletions(long remoteUserId, String model, List<AiChatRequest.Message> messages) {
        return chatCompletions(UUID.randomUUID().toString(), remoteUserId, model, messages);
    }

    public ChatResult chatCompletions(String requestId, long remoteUserId, String model, List<AiChatRequest.Message> messages) {
        return textChat(textStreamLimits(buildChatRequest(requestId, remoteUserId, model, messages)), messages, null, false);
    }

    public ChatResult chatCompletionsStream(long remoteUserId, String model,
                                            List<AiChatRequest.Message> messages, StreamProgressListener listener) {
        return chatCompletionsStream(UUID.randomUUID().toString(), remoteUserId, model, messages, listener);
    }

    public ChatResult chatCompletionsStream(String requestId, long remoteUserId, String model,
                                            List<AiChatRequest.Message> messages, StreamProgressListener listener) {
        return textChat(textStreamLimits(buildChatRequest(requestId, remoteUserId, model, messages)), messages, listener, true);
    }

    private ChatResult textChat(ChatCompletionsRequest request, List<AiChatRequest.Message> messages,
                                StreamProgressListener listener, boolean streamRequested) {
        if (monetaryValidation()) return chatResult(moneyGateway.call(request, moneyTransport(listener)));
        boolean streaming = request.hasStreamTimeouts() || streamRequested;
        try {
            return validated(streaming ? "CHAT_STREAM" : "CHAT", request.getRequestId(), request.getModel(), messages,
                    () -> chatResult(streaming ? consumeStream(request, listener) : unaryChat(request)));
        } catch (io.grpc.StatusRuntimeException failure) {
            String description = failure.getStatus().getDescription();
            if (failure.getStatus().getCode()==io.grpc.Status.Code.FAILED_PRECONDITION
                    && description!=null && description.startsWith("THINKING_OPTIONS_UNSUPPORTED")) {
                throw new AiRequestNotSentException(org.springframework.http.HttpStatus.CONFLICT, "AI_THINKING_UNSUPPORTED");
            }
            throw failure;
        }
    }

    private ChatCompletionsResponse unaryChat(ChatCompletionsRequest request) {
        return stub().withDeadlineAfter(timeoutMs(), TimeUnit.MILLISECONDS).chatCompletions(request);
    }

    private boolean monetaryValidation() { return validationBudget.monetaryEnabled(); }

    public com.ainovel.app.ai.AiValidationMoneyGateway.Transport moneyTransport() { return moneyTransport(null); }

    private com.ainovel.app.ai.AiValidationMoneyGateway.Transport moneyTransport(StreamProgressListener listener) {
        return new com.ainovel.app.ai.AiValidationMoneyGateway.Transport() {
            public fireflychat.ai.v1.ModelChatCapabilities capabilities(String model) { return chatCapabilities(model); }
            public fireflychat.ai.v1.PrepareCallBudgetResponse prepare(fireflychat.ai.v1.PrepareCallBudgetRequest request) {
                return stub().withDeadlineAfter(timeoutMs(),TimeUnit.MILLISECONDS).prepareCallBudget(request);
            }
            public ChatCompletionsResponse send(ChatCompletionsRequest request) {
                return request.hasStreamTimeouts() ? consumeStream(request, listener) : unaryChat(request);
            }
            public fireflychat.ai.v1.GetBudgetCallStatusResponse status(fireflychat.ai.v1.GetBudgetCallStatusRequest request) {
                return stub().withDeadlineAfter(timeoutMs(),TimeUnit.MILLISECONDS).getBudgetCallStatus(request);
            }
        };
    }
    private ChatResult moneyChat(ChatCompletionsRequest request) {
        return chatResult(moneyGateway.call(request, moneyTransport()));
    }
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString="${app.ai.validation.reconcile-delay-ms:30000}")
    public void reconcileValidationMoney() {
        if(monetaryValidation()) moneyGateway.reconcilePending(moneyTransport());
    }

    private ChatCompletionsResponse consumeStream(ChatCompletionsRequest request, StreamProgressListener listener) {
        Iterator<ChatCompletionsStreamEvent> events = stub()
                .withDeadlineAfter(request.hasStreamTimeouts() ? TEXT_STREAM_RPC_MS : timeoutMs(), TimeUnit.MILLISECONDS)
                .chatCompletionsStream(request);
        return collectStream(request, events, listener);
    }

    static ChatCompletionsResponse collectStream(ChatCompletionsRequest request, Iterator<ChatCompletionsStreamEvent> events,
                                                  StreamProgressListener listener) {
        String requestId = request.getRequestId();
        var content = new StringBuilder();
        long expectedSequence = 1;
        long outputTokens = 0;
        boolean started = false;
        ChatCompletionsStreamEvent completed = null;
        while (events.hasNext()) {
            var event = events.next();
            if (completed != null || !requestId.equals(event.getRequestId()) || event.getSequence()!=expectedSequence++) {
                throw new IllegalStateException("ai-service returned an invalid stream sequence");
            }
            switch (event.getEventType()) {
                case CHAT_STREAM_EVENT_TYPE_STARTED -> {
                    if (started) throw new IllegalStateException("ai-service returned duplicate STARTED event");
                    started = true;
                    if (listener != null) listener.onStarted(requestId, event.getModelKey());
                }
                case CHAT_STREAM_EVENT_TYPE_CONTENT_DELTA -> {
                    if (!started || event.getContentDelta().isEmpty() || event.getOutputTokensSoFar() < outputTokens) {
                        throw new IllegalStateException("ai-service returned invalid CONTENT_DELTA");
                    }
                    outputTokens = event.getOutputTokensSoFar();
                    content.append(event.getContentDelta());
                    if (listener != null && !event.getContentDelta().isBlank()) listener.onDelta(outputTokens, event.getOutputTokensEstimated());
                }
                case CHAT_STREAM_EVENT_TYPE_COMPLETED -> {
                    boolean bounded = request.hasBudgetCredential();
                    if (!started || event.getOutputTokensEstimated()
                            || !bounded && content.isEmpty()
                            || bounded && (!event.getUsageAuthoritative() || !event.hasBudgetCharge())) {
                        throw new IllegalStateException("ai-service returned an invalid completion receipt");
                    }
                    completed = event;
                }
                default -> throw new IllegalStateException("ai-service returned an unknown stream event");
            }
        }
        if (completed == null) throw new IllegalStateException("ai-service stream ended without COMPLETED event");
        if (listener != null) listener.onCompleted(completed.getCompletionTokens(), completed.getPromptTokens(), completed.getCacheTokens());
        var result = ChatCompletionsResponse.newBuilder().setContent(content.toString()).setModelKey(completed.getModelKey())
                .setPromptTokens(completed.getPromptTokens()).setCompletionTokens(completed.getCompletionTokens())
                .setCacheTokens(completed.getCacheTokens()).setFinishReason(completed.getFinishReason());
        if (completed.hasReasoningTokens()) result.setReasoningTokens(completed.getReasoningTokens());
        if (completed.hasUsageAuthoritative()) result.setUsageAuthoritative(completed.getUsageAuthoritative());
        if (completed.hasBudgetCharge()) result.setBudgetCharge(completed.getBudgetCharge());
        if (completed.hasStreamTiming()) result.setStreamTiming(completed.getStreamTiming());
        if (completed.hasCreditBilling()) result.setCreditBilling(completed.getCreditBilling());
        if (completed.hasNativeResponseJson()) result.setNativeResponseJson(completed.getNativeResponseJson());
        return result.build();
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

    private ChatCompletionsRequest buildChatRequest(String requestId,long remoteUserId,String model,AiChatRequest input) {
        if(!input.nativeTools())return buildChatRequest(requestId,remoteUserId,model,input.messages());
        var builder=ChatCompletionsRequest.newBuilder().setRequestId(requestId).setProjectKey(properties.getProjectKey())
                .setUserId(remoteUserId).setSessionId("").setModel(model).setToolsJson(input.toolsJson());
        if(input.maxOutputTokens()!=null)builder.setMaxOutputTokens(input.maxOutputTokens());
        for(var message:input.messages()) {
            var wire=ChatMessage.newBuilder().setRole(message.role()).setContent(message.content()==null?"":message.content());
            if(message.toolCallId()!=null)wire.setToolCallId(message.toolCallId());
            if(message.toolCallsJson()!=null)wire.setToolCallsJson(message.toolCallsJson());
            if(message.reasoningContent()!=null)wire.setReasoningContent(message.reasoningContent());
            if(message.providerState()!=null)wire.setProviderState(message.providerState());
            builder.addMessages(wire);
        }
        return builder.build();
    }

    public EmbeddingResult embeddings(long remoteUserId, String model, List<String> input, boolean normalize) {
        String requestId = UUID.randomUUID().toString();
        return validated("EMBEDDINGS", requestId, model, input, () -> embeddingTransport(requestId, remoteUserId, model, input, normalize));
    }

    /** Native retrieval is budgeted at the provider exit; it does not reuse historical local budgets. */
    public fireflychat.ai.v1.EmbeddingsResponse nativeEmbeddings(String requestId,long user,String model,List<String> input,String type,String run){
        var request=EmbeddingsRequest.newBuilder().setRequestId(requestId).setProjectKey(properties.getProjectKey())
                .setUserId(user).setModel(model).addAllInput(input).setDimensions(1024).setInputType(type).setNormalize(true);
        if(run!=null&&!run.isBlank())request.setEvaluationRunId(run);
        return stub().withDeadlineAfter(timeoutMs(),TimeUnit.MILLISECONDS).embeddings(request.build());
    }
    public fireflychat.ai.v1.RerankResponse rerank(String requestId,long user,String query,List<String> documents,String instruction,int top,String run){
        var request=fireflychat.ai.v1.RerankRequest.newBuilder().setRequestId(requestId).setProjectKey(properties.getProjectKey())
                .setUserId(user).setModel(RETRIEVAL_RERANK_MODEL).setQuery(query).addAllDocuments(documents).setInstruct(instruction).setTopN(top);
        if(run!=null&&!run.isBlank())request.setEvaluationRunId(run);
        return stub().withDeadlineAfter(timeoutMs(),TimeUnit.MILLISECONDS).rerank(request.build());
    }
    public ChatResult structured(String requestId,long user,List<AiChatRequest.Message> messages,String schema,int maxOutput,String run){
        var request=ChatCompletionsRequest.newBuilder().setRequestId(requestId).setProjectKey(properties.getProjectKey())
                .setUserId(user).setModel(RETRIEVAL_STRUCTURED_MODEL).setJsonSchema(schema).setMaxOutputTokens(maxOutput).setThinkingEnabled(false);
        for(var m:messages)request.addMessages(ChatMessage.newBuilder().setRole(m.role()).setContent(m.content()));
        if(run!=null&&!run.isBlank())request.setEvaluationRunId(run);
        var response=stub().withDeadlineAfter(timeoutMs(),TimeUnit.MILLISECONDS).chatCompletions(request.build());
        return chatResult(response);
    }

    private EmbeddingResult embeddingTransport(String requestId, long remoteUserId, String model, List<String> input, boolean normalize) {
        AiGatewayServiceGrpc.AiGatewayServiceBlockingStub stub = stub();
        EmbeddingsRequest.Builder builder = EmbeddingsRequest.newBuilder()
                .setRequestId(requestId)
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

    private <T> T validated(String kind, String requestId, String model, Object input, java.util.function.Supplier<T> call) {
        if (requestId == null || requestId.length() > 160) throw new IllegalArgumentException("AI_REQUEST_ID_TOO_LONG");
        UUID validationId;
        try { validationId = validationBudget.claimGateway(kind, input, requestId, model); }
        catch (com.ainovel.app.common.ApiStatusException denied) {
            throw new com.ainovel.app.ai.AiRequestNotSentException(denied.getStatus(), denied.getMessage());
        }
        try {
            T result = call.get();
            validationBudget.complete(validationId, result, true);
            return result;
        } catch (RuntimeException failure) {
            {
                Map<String, String> failureMetadata = new java.util.LinkedHashMap<>();
                failureMetadata.put("errorType", failure.getClass().getSimpleName());
                if (failure instanceof io.grpc.StatusRuntimeException status) {
                    failureMetadata.put("grpcCode", status.getStatus().getCode().name());
                }
                try { validationBudget.complete(validationId, failureMetadata, false); }
                catch (RuntimeException recordingFailure) { failure.addSuppressed(recordingFailure); }
            }
            throw failure;
        }
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
        return Math.max(800L, properties.getAiTimeoutMs());
    }

    @PreDestroy
    public synchronized void shutdown() {
        endpointManager.shutdown();
    }

    private static ChatResult chatResult(ChatCompletionsResponse response) {
        return new ChatResult(response.getContent(), response.getModelKey(), response.getPromptTokens(),
                response.getCompletionTokens(), response.getCacheTokens(), AiResponseMetadata.reported(
                        response.hasFinishReason() ? response.getFinishReason() : null,
                        response.hasReasoningTokens() ? response.getReasoningTokens() : null,
                        response.hasUsageAuthoritative() ? response.getUsageAuthoritative() : null),
                response.hasNativeResponseJson()?response.getNativeResponseJson():null);
    }

    public record ChatResult(String content, String modelKey, long promptTokens, long completionTokens, long cacheTokens,
                             AiResponseMetadata metadata,String nativeResponseJson) {
        public ChatResult(String content,String modelKey,long promptTokens,long completionTokens,long cacheTokens,AiResponseMetadata metadata) {
            this(content,modelKey,promptTokens,completionTokens,cacheTokens,metadata,null);
        }
        public ChatResult(String content,String modelKey,long promptTokens,long completionTokens,long cacheTokens) {
            this(content,modelKey,promptTokens,completionTokens,cacheTokens,null);
        }
    }

    public interface StreamProgressListener {
        default void onStarted(String requestId, String modelKey) {}
        default void onModelResolved(String modelKey) {}
        void onDelta(long outputTokens, boolean estimated);
        default void onCompleted(long completionTokens, long promptTokens, long cacheTokens) {}
        default void onUnaryCompleted(String modelKey,long completionTokens,long promptTokens,long cacheTokens) {
            onModelResolved(modelKey);
            onCompleted(completionTokens,promptTokens,cacheTokens);
        }
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
            case MODEL_TYPE_RERANK -> "rerank";
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

    static final class AiHmacAuthInterceptor implements ClientInterceptor {
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

        AiHmacAuthInterceptor(ExternalServiceProperties properties, Clock clock) {
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
                    String caller = properties.getSecurity().getAi().getHmacCaller();
                    String secret = properties.getSecurity().getAi().getHmacSecret();
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
