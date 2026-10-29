package com.ainovel.app.ai;

import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.common.ApiStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounds and pairs private native protocol messages without altering the provider's continuation material. */
public final class AiNativeRequestValidator {
    private AiNativeRequestValidator() {}
    public static void validate(AiChatRequest request,ObjectMapper json,int maxMessageChars,int maxTotalChars) {
        try {
            if(request.maxOutputTokens()==null || request.maxOutputTokens()<1 || request.toolsJson().getBytes(StandardCharsets.UTF_8).length>65536)throw invalid();
            var tools=json.readTree(request.toolsJson());
            if(tools==null || !tools.isArray() || tools.isEmpty() || tools.size()>16)throw invalid();
            var names=new HashSet<String>();
            for(var tool:tools) {
                String name=tool.path("function").path("name").asText();
                if(!"function".equals(tool.path("type").asText()) || !name.matches("[A-Za-z0-9_-]{1,64}") || !names.add(name)
                        || !tool.path("function").path("parameters").isObject())throw invalid();
            }
            var pending=new HashSet<String>();var all=new HashSet<String>();boolean user=false;long total=request.toolsJson().length();
            for(var message:request.messages()) {
                if(message==null || message.role()==null)throw invalid();
                String role=message.role();String content=Objects.toString(message.content(),"");
                if(!Set.of("system","user","assistant","tool").contains(role) || content.length()>maxMessageChars)throw invalid();
                if(!pending.isEmpty() && !"tool".equals(role))throw invalid();
                total+=content.length()+length(message.toolCallId())+length(message.toolCallsJson())+length(message.reasoningContent())+length(message.providerState());
                if(total>maxTotalChars)throw invalid();
                if("tool".equals(role)) {
                    if(message.toolCallId()==null || !pending.remove(message.toolCallId()) || content.isBlank()
                            || message.toolCallsJson()!=null || message.reasoningContent()!=null || message.providerState()!=null)throw invalid();
                }else if("assistant".equals(role)) {
                    if(message.toolCallId()!=null)throw invalid();
                    if(message.toolCallsJson()!=null) {
                        var calls=json.readTree(message.toolCallsJson());
                        if(calls==null || !calls.isArray() || calls.isEmpty() || calls.size()>16)throw invalid();
                        for(var call:calls) {
                            String id=call.path("id").asText(),name=call.path("function").path("name").asText();
                            if(!"function".equals(call.path("type").asText()) || id.isBlank() || id.length()>200 || !all.add(id)
                                    || !name.matches("[A-Za-z0-9_-]{1,64}") || !call.path("function").path("arguments").isTextual())throw invalid();
                            pending.add(id);
                        }
                    }else if(content.isBlank())throw invalid();
                }else {
                    if(message.hasNativeFields() || content.isBlank())throw invalid();
                    user|="user".equals(role);
                }
            }
            if(!user || !pending.isEmpty())throw invalid();
        }catch(java.io.IOException invalidJson) {throw invalid();}
    }
    private static int length(String value) {return value==null?0:value.length();}
    private static ApiStatusException invalid() {return new ApiStatusException(HttpStatus.BAD_REQUEST,"AI_NATIVE_REQUEST_INVALID");}
}
