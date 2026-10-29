package com.ainovel.app.writingagent;
import com.ainovel.app.aioperation.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import java.util.UUID;
@Component
public class WritingAgentHandler implements AiOperationHandler {
    private final WritingAgentRunner runner;
    public WritingAgentHandler(@Lazy WritingAgentRunner runner){this.runner=runner;}
    @Override public String type(){return WritingAgentService.TYPE;}
    @Override public Object execute(AiOperationExecution e)throws Exception{return runner.execute(e,UUID.fromString(e.objectMapper().readTree(e.payloadJson()).path("runId").asText()));}
}
