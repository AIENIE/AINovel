package com.ainovel.app.assistant;

import com.ainovel.app.aioperation.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class AssistantOperationHandler implements AiOperationHandler {
    private final WritingAssistantService service;
    public AssistantOperationHandler(@Lazy WritingAssistantService service){this.service=service;}
    @Override public String type(){return WritingAssistantService.TYPE;}
    @Override public Object execute(AiOperationExecution execution)throws Exception{
        UUID turn=UUID.fromString(execution.objectMapper().readTree(execution.payloadJson()).path("turnId").asText());
        return service.execute(execution,turn);
    }
}
