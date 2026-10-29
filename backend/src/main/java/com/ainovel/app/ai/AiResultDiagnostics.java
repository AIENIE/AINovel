package com.ainovel.app.ai;

import com.ainovel.app.ai.dto.AiChatResponse;
import com.ainovel.app.ai.dto.AiResponseMetadata;

/** Safe outcome classification from durable reported facts; never from model names or cost estimates. */
public final class AiResultDiagnostics {
    private AiResultDiagnostics() {}
    public enum Failure { EMPTY_RESULT, OUTPUT_LIMIT, REASONING_OUTPUT_LIMIT, TRUNCATED_RESULT, RESULT_INVALID }
    public static Failure unusable(AiChatResponse response){
        boolean empty=response.content()==null||response.content().isBlank();var metadata=response.metadata();
        boolean length=metadata!=null&&metadata.finishReason()==AiResponseMetadata.FinishReason.LENGTH;
        if(!empty)return length?Failure.TRUNCATED_RESULT:Failure.RESULT_INVALID;
        if(!length)return Failure.EMPTY_RESULT;
        if(Boolean.TRUE.equals(metadata.usageAuthoritative())&&metadata.reasoningTokens()!=null&&metadata.reasoningTokens()>0&&response.usage()!=null
                &&Double.compare(metadata.reasoningTokens().doubleValue(),response.usage().outputTokens())==0)return Failure.REASONING_OUTPUT_LIMIT;
        return Failure.OUTPUT_LIMIT;
    }
}
