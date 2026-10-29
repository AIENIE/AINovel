package com.ainovel.app.worldassistant;

import com.ainovel.app.ai.dto.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.ainovel.app.ai.dto.AiResponseMetadata.FinishReason.*;

class WorldAssistantResultFailureTest {
    private AiChatResponse response(String body,AiResponseMetadata metadata) {
        return new AiChatResponse("assistant",body,new AiUsageDto(100,8192,0,0,13),20,null,"model",metadata);
    }
    @Test void blankBodyDoesNotInventLengthOrThinkingCause() {
        for(String body:new String[]{null,""," \n\t"})
            assertEquals(WorldAssistantResultFailure.EMPTY,WorldAssistantResultFailure.code(response(body,null)));
        assertEquals(WorldAssistantResultFailure.EMPTY,WorldAssistantResultFailure.code(response("",new AiResponseMetadata(1,STOP,8192L,true))));
    }
    @Test void exhaustedThinkingRequiresLengthAndAuthoritativeWholeOutput() {
        assertEquals(WorldAssistantResultFailure.REASONING_LIMIT,WorldAssistantResultFailure.code(response("",new AiResponseMetadata(1,LENGTH,8192L,true))));
        for(Long thinking:new Long[]{null,0L,100L,8193L})
            assertEquals(WorldAssistantResultFailure.OUTPUT_LIMIT,WorldAssistantResultFailure.code(response("",new AiResponseMetadata(1,LENGTH,thinking,true))));
        for(Boolean authoritative:new Boolean[]{null,false})
            assertEquals(WorldAssistantResultFailure.OUTPUT_LIMIT,WorldAssistantResultFailure.code(response("",new AiResponseMetadata(1,LENGTH,8192L,authoritative))));
    }
    @Test void malformedNonemptyBodyDistinguishesTruncationFromFormatError() {
        assertEquals(WorldAssistantResultFailure.TRUNCATED,WorldAssistantResultFailure.code(response("{\"kind\":",new AiResponseMetadata(1,LENGTH,8000L,true))));
        assertEquals(WorldAssistantResultFailure.INVALID,WorldAssistantResultFailure.code(response("not json",new AiResponseMetadata(1,STOP,0L,true))));
        assertEquals(WorldAssistantResultFailure.INVALID,WorldAssistantResultFailure.code(response("not json",null)));
    }
}
