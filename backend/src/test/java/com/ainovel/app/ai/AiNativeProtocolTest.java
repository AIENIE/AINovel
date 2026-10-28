package com.ainovel.app.ai;

import com.ainovel.app.ai.dto.*;
import com.ainovel.app.common.ApiStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AiNativeProtocolTest {
    static final String TOOLS="[{\"type\":\"function\",\"function\":{\"name\":\"world_search\",\"parameters\":{\"type\":\"object\",\"properties\":{}}}}]";
    static final String CALLS="[{\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"world_search\",\"arguments\":\"{\\\"query\\\":\\\"灯塔\\\"}\"}}]";
    private final ObjectMapper json=new ObjectMapper();
    private final AiAdmissionGuard guard=new AiAdmissionGuard(mock(StringRedisTemplate.class),json,64,20000,100000,20,2,32);

    @Test void oldRequestShapeAndHashRemainUnchangedAndNewFieldsRoundTrip() throws Exception {
        var old=new AiChatRequest(List.of(new AiChatRequest.Message("user","正文")),null,null);
        String bytes=json.writeValueAsString(old);
        assertEquals("{\"messages\":[{\"role\":\"user\",\"content\":\"正文\"}],\"modelId\":null,\"context\":null,\"featureKey\":null}",bytes);
        String expected=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest("{\"messages\":[{\"role\":\"user\",\"content\":\"正文\"}],\"modelId\":null,\"context\":null}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals(expected,guard.requestHash(old));
        var request=nativeRequest();guard.validate(request);
        var restored=json.readValue(json.writeValueAsBytes(request),AiChatRequest.class);
        assertEquals(request.messages(),restored.messages());
        assertEquals(json.writeValueAsString(request),json.writeValueAsString(restored));
        assertNotEquals(guard.requestHash(request),guard.requestHash(old));
    }
    @Test void nativePairsRemainExactAndOrdinaryChatCannotUseNativeRoles() {
        assertDoesNotThrow(()->guard.validate(nativeRequest()));
        var messages=new ArrayList<>(nativeRequest().messages());messages.removeLast();
        assertThrows(ApiStatusException.class,()->guard.validate(new AiChatRequest(messages,null,null).withNativeTools(TOOLS,2048)));
        messages.add(new AiChatRequest.Message("tool","found","other",null,null,null));
        assertThrows(ApiStatusException.class,()->guard.validate(new AiChatRequest(messages,null,null).withNativeTools(TOOLS,2048)));
        assertThrows(ApiStatusException.class,()->guard.validate(new AiChatRequest(nativeRequest().messages(),null,null)));
    }
    @Test void toolOnlyResultKeepsOpaqueContinuationAndPrivateReasoningAcrossStoredJson() throws Exception {
        String raw="{\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":"+CALLS+",\"reasoning_content\":\"private protocol text\",\"provider_state\":\"opaque-state\"},\"finish_reason\":\"tool_calls\",\"unknown_future_field\":{\"x\":1}}";
        var response=new AiChatResponse("assistant","",new AiUsageDto(10,20,0,0,1),99,null,"model",
                AiResponseMetadata.reported("tool_calls",12L,true),raw);
        var replay=json.readValue(json.writeValueAsBytes(response),AiChatResponse.class);
        assertEquals(raw,replay.nativeResponseJson());assertEquals(response.metadata(),replay.metadata());
        var nativeResult=replay.nativeResult();
        assertEquals("world_search",nativeResult.toolCalls().getFirst().name());
        assertEquals("opaque-state",nativeResult.message().providerState());
        assertEquals("private protocol text",nativeResult.message().reasoningContent());
        assertNull(nativeResult.message().content());
        assertThrows(IllegalArgumentException.class,()->AiNativeResult.parse("{\"message\":{\"role\":\"assistant\",\"content\":\"\"}}"));
        assertNull(new AiChatResponse("assistant","old",new AiUsageDto(1,1,0,0,0),99).nativeResult());
    }
    @Test void runBudgetOwnerScopesRestoreWithoutLeakingToOtherThreads() throws Exception {
        var owner=(AiNativeBudgetContext.Owner)(request,transport,validation)->null;
        assertNull(AiNativeBudgetContext.current());
        try(var ignored=AiNativeBudgetContext.open(owner)) {
            assertSame(owner,AiNativeBudgetContext.current());
            try(var executor=java.util.concurrent.Executors.newSingleThreadExecutor()) {assertNull(executor.submit(AiNativeBudgetContext::current).get());}
        }
        assertNull(AiNativeBudgetContext.current());
    }
    static AiChatRequest nativeRequest() {
        return new AiChatRequest(List.of(new AiChatRequest.Message("user","世界的规则"),
                new AiChatRequest.Message("assistant",null,null,CALLS,"reasoning", "opaque"),
                new AiChatRequest.Message("tool","{\"hits\":[]}","call_1",null,null,null)),null,null).withNativeTools(TOOLS,2048);
    }
}
