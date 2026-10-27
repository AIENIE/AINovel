package com.ainovel.app.integration;

import com.ainovel.app.ai.AiValidationCallBudget;
import com.ainovel.app.ai.AiValidationMoneyGateway;
import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.ai.dto.AiResponseMetadata;
import com.ainovel.app.modelconfig.AiModelSelection;
import com.ainovel.app.modelconfig.AiSelectionContext;
import fireflychat.ai.v1.*;
import io.grpc.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiQualityRequestTest {
    private final AtomicReference<ChatCompletionsRequest> sent = new AtomicReference<>();
    private Status failure;
    private boolean responseMetadata,emptyStream;

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void retainsSafeProviderMetadataThroughUnaryAndStream(boolean streaming) {
        responseMetadata=true;
        var result=client().complete("request",1,choice("world.overview.integrate"),messages(),streaming?(tokens,estimated)->{}:null);
        assertThat(result.metadata()).isEqualTo(AiResponseMetadata.reported("length",0L,true));
    }

    @Test void emptyCompletedStreamStillFailsRatherThanBecomingAUsableResult() {
        emptyStream=true;responseMetadata=true;
        assertThatThrownBy(()->client().complete("request",1,choice("world.overview.integrate"),messages(),(tokens,estimated)->{}))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("invalid COMPLETED");
    }

    @Test void moneyTransportPreservesTheSameSafeMetadata() {
        var client=client();var validation=mock(AiValidationCallBudget.class);var money=mock(AiValidationMoneyGateway.class);
        when(validation.monetaryEnabled()).thenReturn(true);
        when(money.call(any(),any())).thenReturn(ChatCompletionsResponse.newBuilder().setContent("").setCompletionTokens(8192)
                .setFinishReason("length").setReasoningTokens(8192).setUsageAuthoritative(true).build());
        ReflectionTestUtils.setField(client,"validationBudget",validation);ReflectionTestUtils.setField(client,"moneyGateway",money);
        assertThat(client.complete("request",1,choice("world.overview.integrate"),messages(),null).metadata())
                .isEqualTo(AiResponseMetadata.reported("length",8192L,true));
        assertThat(sent.get()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"slop.judge,false", "slop.judge,true", "slop.diagnose,false", "slop.diagnose,true", "plot.diagnose,false", "plot.diagnose,true"})
    void sendsTheFrozenReportLimitThroughBothTransports(String feature, boolean streaming) throws Exception {
        var choice = choice(feature);
        var snapshot = new AiSelectionContext.Snapshot("quote", Map.of(feature, choice), null, Map.of(feature, 1536));
        try (var scope = AiSelectionContext.install(snapshot)) {
            var result = client().complete("request", 1, choice, messages(), streaming ? (tokens, estimated) -> {} : null);
            assertThat(result.content()).isEqualTo("{}");
            assertThat(result.completionTokens()).isEqualTo(2);
            assertThat(result.metadata()).isNull();
        }
        var wire = ChatCompletionsRequest.parseFrom(sent.get().toByteArray());
        assertThat(wire.getRequestId()).isEqualTo("request");
        assertThat(wire.getMaxOutputTokens()).isEqualTo(1536);
        assertThat(wire.hasThinkingEnabled()).isTrue();
        assertThat(wire.getThinkingEnabled()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"scene.fast.draft", "slop.revise", "plot.revise"})
    void doesNotApplyTheJsonReportLimitToFullText(String feature) {
        var choice = choice(feature);
        try (var scope = AiSelectionContext.install(new AiSelectionContext.Snapshot("quote", Map.of(feature, choice)))) {
            client().complete("request", 1, choice, messages(), null);
        }
        assertThat(sent.get().getMaxOutputTokens()).isZero();
    }

    @Test void keepsTheProviderDefaultForAnUnquotedLegacyCall() {
        client().complete("request", 1, choice("slop.diagnose"), messages(), null);
        assertThat(sent.get().getMaxOutputTokens()).isZero();
    }

    @Test void recordsOnlyTheGrpcCodeWithoutClassifyingAnAmbiguousFailureAsUnsent() {
        var client = client();
        var validation = mock(AiValidationCallBudget.class);
        var callId = UUID.randomUUID();
        when(validation.claimGateway(anyString(), any(), anyString(), anyString())).thenReturn(callId);
        ReflectionTestUtils.setField(client, "validationBudget", validation);
        failure = Status.RESOURCE_EXHAUSTED.withDescription("untrusted provider response");
        assertThatThrownBy(() -> client.complete("request", 1, choice("slop.diagnose"), messages(), null))
                .isInstanceOf(StatusRuntimeException.class);
        verify(validation).complete(callId, Map.of("errorType", "StatusRuntimeException", "grpcCode", "RESOURCE_EXHAUSTED"), false);
    }

    private AiGatewayGrpcClient client() {
        var properties = new ExternalServiceProperties();
        properties.getAiserviceGrpc().setAddress("static://localhost:9999");
        properties.getSecurity().getAi().setHmacCaller("unit-test");
        properties.getSecurity().getAi().setHmacSecret("unit-test-secret");
        var factory = mock(GrpcChannelFactory.class);
        var channel = mock(ManagedChannel.class);
        when(factory.create("localhost", 9999)).thenReturn(channel);
        when(channel.newCall(any(), any())).thenAnswer(invocation ->
                new ReplyCall(((MethodDescriptor<?, ?>) invocation.getArgument(0)).getType() == MethodDescriptor.MethodType.SERVER_STREAMING));
        return new AiGatewayGrpcClient(properties, factory);
    }

    private static AiModelSelection choice(String feature) {
        return new AiModelSelection(feature, "option", "test-model", "Plan", "OFF", null, true, false, "TOKENS", null, null, null, 1);
    }

    private static List<AiChatRequest.Message> messages() {
        return List.of(new AiChatRequest.Message("user", "诊断当前文本"));
    }

    private final class ReplyCall extends ClientCall<ChatCompletionsRequest, Object> {
        private final boolean streaming;
        private final Queue<Object> replies = new ArrayDeque<>();
        private Listener<Object> listener;
        private int requested;
        private boolean ready, closed;

        ReplyCall(boolean streaming) { this.streaming = streaming; }
        @Override public void start(Listener<Object> listener, Metadata headers) { this.listener = listener; }
        @Override public void request(int count) { requested += count; drain(); }
        @Override public void cancel(String message, Throwable cause) { closed = true; }
        @Override public void sendMessage(ChatCompletionsRequest message) { sent.set(message); }
        @Override public void halfClose() {
            if (failure != null) { closed = true; listener.onClose(failure, new Metadata()); return; }
            if (streaming) {
                replies.add(event(1, ChatStreamEventType.CHAT_STREAM_EVENT_TYPE_STARTED).build());
                if(!emptyStream)replies.add(event(2, ChatStreamEventType.CHAT_STREAM_EVENT_TYPE_CONTENT_DELTA).setContentDelta("{}").setOutputTokensSoFar(2).build());
                var completed=event(emptyStream?2:3, ChatStreamEventType.CHAT_STREAM_EVENT_TYPE_COMPLETED).setPromptTokens(3).setCompletionTokens(2);
                if(responseMetadata)completed.setFinishReason("length").setReasoningTokens(0).setUsageAuthoritative(true);
                replies.add(completed.build());
            } else {
                var response=ChatCompletionsResponse.newBuilder().setContent("{}").setModelKey("test-model").setPromptTokens(3).setCompletionTokens(2);
                if(responseMetadata)response.setFinishReason("length").setReasoningTokens(0).setUsageAuthoritative(true);
                replies.add(response.build());
            }
            ready = true;
            drain();
        }
        private ChatCompletionsStreamEvent.Builder event(long sequence, ChatStreamEventType type) {
            return ChatCompletionsStreamEvent.newBuilder().setRequestId("request").setSequence(sequence).setModelKey("test-model").setEventType(type);
        }
        private void drain() {
            while (ready && !closed && requested > 0 && !replies.isEmpty()) { requested--; listener.onMessage(replies.remove()); }
            if (ready && !closed && replies.isEmpty()) { closed = true; listener.onClose(Status.OK, new Metadata()); }
        }
    }
}
