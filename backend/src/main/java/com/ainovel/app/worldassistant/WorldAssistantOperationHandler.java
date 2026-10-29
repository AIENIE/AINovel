package com.ainovel.app.worldassistant;
import com.ainovel.app.aioperation.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class WorldAssistantOperationHandler implements AiOperationHandler {
    private final WorldAssistantService service;
    public WorldAssistantOperationHandler(@Lazy WorldAssistantService service){this.service=service;}
    @Override public String type(){return WorldAssistantService.TYPE;}
    @Override public Object execute(AiOperationExecution execution)throws Exception{
        return service.execute(execution,UUID.fromString(execution.objectMapper().readTree(execution.payloadJson()).path("turnId").asText()));
    }
}
