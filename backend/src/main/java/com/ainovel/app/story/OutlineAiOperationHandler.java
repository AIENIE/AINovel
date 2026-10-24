package com.ainovel.app.story;
import com.ainovel.app.aioperation.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import java.util.UUID;
@Component
public class OutlineAiOperationHandler implements AiOperationHandler {
    private final OutlineAiService service;
    public OutlineAiOperationHandler(@Lazy OutlineAiService service){this.service=service;}
    @Override public String type(){return OutlineAiService.TYPE;}
    @Override public Object execute(AiOperationExecution execution)throws Exception{return service.execute(execution,UUID.fromString(execution.objectMapper().readTree(execution.payloadJson()).path("resultId").asText()));}
}
