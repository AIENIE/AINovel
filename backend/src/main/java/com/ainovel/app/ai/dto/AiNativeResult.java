package com.ainovel.app.ai.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

/** Private protocol material for server-side tool execution and exact continuation, never author-facing text. */
public record AiNativeResult(AiChatRequest.Message message,List<ToolCall> toolCalls,String finishReason) {
    private static final ObjectMapper JSON = new ObjectMapper();
    public record ToolCall(String id,String name,String arguments) {}

    public static AiNativeResult parse(String raw) {
        if(raw == null) return null;
        try {
            JsonNode root=JSON.readTree(raw), message=root.path("message");
            if(!root.isObject() || !message.isObject() || !"assistant".equals(message.path("role").asText()))throw new IllegalArgumentException();
            var calls=new ArrayList<ToolCall>();
            JsonNode array=message.get("tool_calls");
            if(array!=null && !array.isNull()) {
                if(!array.isArray() || array.size()>16)throw new IllegalArgumentException();
                var ids=new HashSet<String>();
                for(var call:array) {
                    String id=call.path("id").asText(),name=call.path("function").path("name").asText();
                    var arguments=call.path("function").get("arguments");
                    if(!"function".equals(call.path("type").asText()) || id.isBlank() || !ids.add(id) || name.isBlank() || arguments==null || !arguments.isTextual())throw new IllegalArgumentException();
                    calls.add(new ToolCall(id,name,arguments.textValue()));
                }
            }
            String content=nullable(message,"content");
            if((content==null || content.isBlank()) && calls.isEmpty())throw new IllegalArgumentException();
            var result=new AiChatRequest.Message("assistant",content,null,array==null||array.isNull()?null:array.toString(),nullable(message,"reasoning_content"),nullable(message,"provider_state"));
            return new AiNativeResult(result,List.copyOf(calls),nullable(root,"finish_reason"));
        } catch(java.io.IOException | RuntimeException invalid) {
            throw new IllegalArgumentException("AI_NATIVE_RESPONSE_INVALID");
        }
    }
    private static String nullable(JsonNode node,String key) {
        var value=node.get(key);if(value==null||value.isNull())return null;
        if(!value.isTextual())throw new IllegalArgumentException();return value.textValue();
    }
}
