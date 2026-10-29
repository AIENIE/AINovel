package com.ainovel.app.storyassistant;
import com.ainovel.app.aioperation.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class StoryAssistantOperationHandler implements AiOperationHandler {
    private final StoryAssistantService service;
    public StoryAssistantOperationHandler(@Lazy StoryAssistantService service){this.service=service;}
    @Override public String type(){return StoryAssistantService.TYPE;}
    @Override public Object execute(AiOperationExecution execution)throws Exception{
        return service.execute(execution,UUID.fromString(execution.objectMapper().readTree(execution.payloadJson()).path("turnId").asText()));
    }
}
